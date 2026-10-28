package com.pagecast.app.playback

import com.pagecast.app.domain.model.Segment
import com.pagecast.app.tts.QueueMode
import com.pagecast.app.tts.TtsEvent
import com.pagecast.app.tts.TtsInitException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NarrationControllerTest {

    private val texts = listOf("The bell rang twice.", "Nobody moved.", "Then the door opened.")
    private val segments = texts.mapIndexed { i, t -> Segment(id = i + 1L, chapterId = 1, index = i, paragraphIndex = 0, text = t) }

    private fun TestScope.newController(engine: FakeTtsEngine = FakeTtsEngine()): Pair<NarrationController, FakeTtsEngine> {
        val controller = NarrationController(engine, backgroundScope)
        controller.initialize()
        controller.load(bookId = 7, title = "Test", newSegments = segments)
        return controller to engine
    }

    private fun scopeTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    @Test
    fun `재생하면 현재 문장은 FLUSH, 다음 문장은 ADD로 넣는다`() = scopeTest {
        val (controller, engine) = newController()
        controller.play()

        assertTrue(controller.state.value.isPlaying)
        assertEquals(2, engine.spoken.size)
        assertEquals(texts[0] to QueueMode.FLUSH, engine.spoken[0].let { it.first.text to it.second })
        assertEquals(texts[1] to QueueMode.ADD, engine.spoken[1].let { it.first.text to it.second })
    }

    @Test
    fun `단어 범위 이벤트가 상태에 반영된다`() = scopeTest {
        val (controller, engine) = newController()
        controller.play()
        engine.emit(TtsEvent.WordRange(engine.spoken[0].first.id, 4, 8))

        assertEquals(4 until 8, controller.state.value.wordRange)
        assertEquals(true, controller.state.value.wordEventsSupported)
    }

    @Test
    fun `일시정지 후 재개하면 마지막 단어부터 말하고 범위를 보정한다`() = scopeTest {
        val (controller, engine) = newController()
        controller.play()
        engine.emit(TtsEvent.WordRange(engine.spoken[0].first.id, 9, 13)) // "rang"
        controller.pause()

        assertFalse(controller.state.value.isPlaying)
        assertEquals(9, controller.state.value.resumeOffset)

        controller.play()
        val resumed = engine.lastFlushed()
        assertEquals("rang twice.", resumed.text)

        engine.emit(TtsEvent.WordRange(resumed.id, 5, 10)) // 발화 기준 "twice"
        assertEquals(14 until 19, controller.state.value.wordRange)
    }

    @Test
    fun `문장이 끝나면 다음 문장으로 넘어가고 그다음 문장을 미리 넣는다`() = scopeTest {
        val (controller, engine) = newController()
        controller.play()
        engine.emit(TtsEvent.Done(engine.spoken[0].first.id))

        assertEquals(1, controller.state.value.currentIndex)
        assertEquals(texts[2] to QueueMode.ADD, engine.spoken.last().let { it.first.text to it.second })
    }

    @Test
    fun `마지막 문장이 끝나면 정지하고 다시 재생하면 처음부터`() = scopeTest {
        val (controller, engine) = newController()
        controller.seekTo(2, autoPlay = true)
        engine.emit(TtsEvent.Done(engine.lastFlushed().id))

        assertFalse(controller.state.value.isPlaying)
        assertTrue(controller.state.value.finished)

        controller.play()
        assertEquals(texts[0], engine.lastFlushed().text)
        assertEquals(0, controller.state.value.currentIndex)
    }

    @Test
    fun `정지 뒤 늦게 도착한 이전 발화 이벤트는 무시한다`() = scopeTest {
        val (controller, engine) = newController()
        controller.play()
        val oldId = engine.spoken[0].first.id
        controller.seekTo(2)

        engine.emit(TtsEvent.Done(oldId))
        engine.emit(TtsEvent.WordRange(oldId, 0, 3))

        assertEquals(2, controller.state.value.currentIndex)
        assertNull(controller.state.value.wordRange)
    }

    @Test
    fun `재생 중 속도를 바꾸면 지금 단어부터 새 속도로 다시 말한다`() = scopeTest {
        val (controller, engine) = newController()
        controller.play()
        engine.emit(TtsEvent.WordRange(engine.spoken[0].first.id, 9, 13))
        controller.setSpeed(1.5f)

        val restarted = engine.lastFlushed()
        assertEquals("rang twice.", restarted.text)
        assertEquals(1.5f, restarted.rate)
        assertTrue(controller.state.value.isPlaying)
    }

    @Test
    fun `속도는 범위로 제한된다`() = scopeTest {
        val (controller, _) = newController()
        controller.setSpeed(5f)
        assertEquals(NarrationController.MAX_SPEED, controller.state.value.speed)
        controller.setSpeed(0.1f)
        assertEquals(NarrationController.MIN_SPEED, controller.state.value.speed)
    }

    @Test
    fun `이전과 다음은 범위 안에서만 움직인다`() = scopeTest {
        val (controller, _) = newController()
        controller.previous()
        assertEquals(0, controller.state.value.currentIndex)
        controller.next()
        controller.next()
        controller.next()
        assertEquals(2, controller.state.value.currentIndex)
    }

    @Test
    fun `재생 중 다른 문장을 탭하면 그 문장부터 읽는다`() = scopeTest {
        val (controller, engine) = newController()
        controller.play()
        controller.seekTo(1)

        assertTrue(controller.state.value.isPlaying)
        assertEquals(texts[1], engine.lastFlushed().text)
    }

    @Test
    fun `단어 이벤트 없이 문장이 끝나면 단어 하이라이트 미지원으로 표시`() = scopeTest {
        val (controller, engine) = newController()
        controller.play()
        engine.emit(TtsEvent.Done(engine.spoken[0].first.id))

        assertEquals(false, controller.state.value.wordEventsSupported)
    }

    @Test
    fun `엔진 초기화 실패 시 말하지 않는다`() = scopeTest {
        val failing = FakeTtsEngine(Result.failure(TtsInitException(TtsInitException.Reason.LANGUAGE_UNAVAILABLE)))
        val (controller, engine) = newController(failing)
        controller.play()

        assertEquals(EngineStatus.LANGUAGE_UNAVAILABLE, controller.state.value.engineStatus)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `같은 책을 다시 load하면 재생 상태를 유지한다`() = scopeTest {
        val (controller, _) = newController()
        controller.play()
        controller.load(bookId = 7, title = "Test", newSegments = segments, startIndex = 2)

        assertTrue(controller.state.value.isPlaying)
        assertEquals(0, controller.state.value.currentIndex)
    }
}
