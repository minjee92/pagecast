package com.pagecast.app.playback

import com.pagecast.app.domain.model.BookContent
import com.pagecast.app.domain.model.BookSummary
import com.pagecast.app.domain.model.ReadingProgress
import com.pagecast.app.domain.model.Segment
import com.pagecast.app.domain.model.SourceType
import com.pagecast.app.domain.repository.BookRepository
import com.pagecast.app.domain.text.SplitSentence
import com.pagecast.app.tts.TtsEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRecorderTest {

    private class FakeRepository : BookRepository {
        val saved = mutableListOf<ReadingProgress>()
        override fun observeBooks(): Flow<List<BookSummary>> = emptyFlow()
        override suspend fun createBook(title: String, sourceType: SourceType, sentences: List<SplitSentence>) = 0L
        override suspend fun getBookContent(bookId: Long): BookContent? = null
        override suspend fun deleteBook(bookId: Long) = Unit
        override suspend fun getProgress(bookId: Long): ReadingProgress? = saved.lastOrNull { it.bookId == bookId }
        override suspend fun saveProgress(progress: ReadingProgress) {
            saved += progress
        }
    }

    private val segments = listOf("The kettle sang.", "Ana poured the tea.", "Rain tapped the glass.")
        .mapIndexed { i, t -> Segment(id = i + 1L, chapterId = 3, index = i, paragraphIndex = 0, text = t) }

    private fun TestScope.setUp(): Triple<NarrationController, FakeTtsEngine, FakeRepository> {
        val engine = FakeTtsEngine()
        val controller = NarrationController(engine, backgroundScope)
        val repository = FakeRepository()
        ProgressRecorder(controller, repository, backgroundScope, clock = { 42L }, playingDebounceMs = 1_000).start()
        controller.initialize()
        controller.load(bookId = 9, title = "Tea", newSegments = segments)
        runCurrent()
        return Triple(controller, engine, repository)
    }

    private fun scopeTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    @Test
    fun `책을 열면 현재 위치를 바로 저장한다`() = scopeTest {
        val (_, _, repository) = setUp()

        val last = repository.saved.last()
        assertEquals(9L, last.bookId)
        assertEquals(3L, last.chapterId)
        assertEquals(0, last.segmentIndex)
    }

    @Test
    fun `재생 중에는 문장이 바뀐 뒤 잠시 기다렸다가 저장한다`() = scopeTest {
        val (controller, engine, repository) = setUp()
        controller.play()
        engine.emit(TtsEvent.Done(engine.spoken[0].first.id))
        val before = repository.saved.size

        advanceTimeBy(500)
        assertEquals(before, repository.saved.size)

        advanceTimeBy(600)
        assertEquals(1, repository.saved.last().segmentIndex)
        assertEquals(0, repository.saved.last().charOffset)
    }

    @Test
    fun `재생 중 단어 이벤트마다 저장하지 않는다`() = scopeTest {
        val (controller, engine, repository) = setUp()
        controller.play()
        val id = engine.spoken[0].first.id
        repeat(5) { engine.emit(TtsEvent.WordRange(id, it * 2, it * 2 + 1)) }
        advanceTimeBy(2_000)

        val playingSaves = repository.saved.drop(1)
        assertTrue(playingSaves.size <= 1)
    }

    @Test
    fun `일시정지하면 단어 위치까지 즉시 저장한다`() = scopeTest {
        val (controller, engine, repository) = setUp()
        controller.play()
        engine.emit(TtsEvent.WordRange(engine.spoken[0].first.id, 4, 10)) // "kettle"
        controller.pause()
        runCurrent()

        val last = repository.saved.last()
        assertEquals(0, last.segmentIndex)
        assertEquals(4, last.charOffset)
        assertEquals(42L, last.updatedAt)
    }
}
