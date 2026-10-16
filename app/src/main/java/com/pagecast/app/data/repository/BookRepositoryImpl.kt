package com.pagecast.app.data.repository

import com.pagecast.app.data.local.BookDao
import com.pagecast.app.data.local.BookEntity
import com.pagecast.app.data.local.SegmentEntity
import com.pagecast.app.domain.model.BookContent
import com.pagecast.app.domain.model.BookSummary
import com.pagecast.app.domain.model.Emotion
import com.pagecast.app.domain.model.ReadingProgress
import com.pagecast.app.domain.model.SourceType
import com.pagecast.app.domain.repository.BookRepository
import com.pagecast.app.domain.text.SplitSentence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookRepositoryImpl(
    private val dao: BookDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : BookRepository {

    override fun observeBooks(): Flow<List<BookSummary>> =
        dao.observeBookSummaries().map { rows -> rows.map { it.toDomain() } }

    override suspend fun createBook(
        title: String,
        sourceType: SourceType,
        sentences: List<SplitSentence>,
    ): Long {
        require(sentences.isNotEmpty()) { "sentences must not be empty" }
        val now = clock()
        return dao.insertBookWithContent(
            book = BookEntity(
                title = title,
                language = DEFAULT_LANGUAGE,
                sourceType = sourceType,
                createdAt = now,
                updatedAt = now,
            ),
            chapterTitle = title,
        ) { chapterId ->
            sentences.mapIndexed { index, sentence ->
                SegmentEntity(
                    chapterId = chapterId,
                    index = index,
                    paragraphIndex = sentence.paragraphIndex,
                    text = sentence.text,
                    speakerId = null,
                    emotion = Emotion.NEUTRAL,
                )
            }
        }
    }

    override suspend fun getBookContent(bookId: Long): BookContent? {
        val book = dao.getBook(bookId) ?: return null
        val chapter = dao.getFirstChapter(bookId) ?: return null
        val segments = dao.getSegments(chapter.id)
        return BookContent(
            book = book.toDomain(),
            chapter = chapter.toDomain(),
            segments = segments.map { it.toDomain() },
        )
    }

    override suspend fun deleteBook(bookId: Long) = dao.deleteBook(bookId)

    override suspend fun getProgress(bookId: Long): ReadingProgress? =
        dao.getProgress(bookId)?.toDomain()

    override suspend fun saveProgress(progress: ReadingProgress) {
        dao.upsertProgress(progress.toEntity())
        dao.touchBook(progress.bookId, progress.updatedAt)
    }

    private companion object {
        const val DEFAULT_LANGUAGE = "en"
    }
}
