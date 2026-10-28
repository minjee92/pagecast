package com.pagecast.app.domain.repository

import com.pagecast.app.domain.model.BookContent
import com.pagecast.app.domain.model.BookSummary
import com.pagecast.app.domain.model.ReadingProgress
import com.pagecast.app.domain.model.SourceType
import com.pagecast.app.domain.text.SplitSentence
import kotlinx.coroutines.flow.Flow

interface BookRepository {

    fun observeBooks(): Flow<List<BookSummary>>

    /** 새 책을 만들고 문장들을 Segment로 저장한다. 생성된 bookId를 반환한다. */
    suspend fun createBook(
        title: String,
        sourceType: SourceType,
        sentences: List<SplitSentence>,
    ): Long

    suspend fun getBookContent(bookId: Long): BookContent?

    suspend fun deleteBook(bookId: Long)

    suspend fun getProgress(bookId: Long): ReadingProgress?

    suspend fun saveProgress(progress: ReadingProgress)
}
