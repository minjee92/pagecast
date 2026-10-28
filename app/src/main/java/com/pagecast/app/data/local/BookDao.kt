package com.pagecast.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query(
        """
        SELECT b.*,
            (SELECT COUNT(*) FROM segments s
                INNER JOIN chapters c ON s.chapterId = c.id
                WHERE c.bookId = b.id) AS segmentCount,
            p.segmentIndex AS lastReadIndex
        FROM books b
        LEFT JOIN reading_progress p ON p.bookId = b.id
        ORDER BY b.updatedAt DESC
        """,
    )
    fun observeBookSummaries(): Flow<List<BookSummaryRow>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBook(bookId: Long): BookEntity?

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex LIMIT 1")
    suspend fun getFirstChapter(bookId: Long): ChapterEntity?

    @Query("SELECT * FROM segments WHERE chapterId = :chapterId ORDER BY segmentIndex")
    suspend fun getSegments(chapterId: Long): List<SegmentEntity>

    @Insert
    suspend fun insertBook(book: BookEntity): Long

    @Insert
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Insert
    suspend fun insertSegments(segments: List<SegmentEntity>)

    /** 책·Chapter·Segment를 한 트랜잭션으로 저장한다. 중간에 실패하면 아무것도 남지 않는다. */
    @Transaction
    suspend fun insertBookWithContent(
        book: BookEntity,
        chapterTitle: String,
        buildSegments: (chapterId: Long) -> List<SegmentEntity>,
    ): Long {
        val bookId = insertBook(book)
        val chapterId = insertChapter(ChapterEntity(bookId = bookId, index = 0, title = chapterTitle))
        insertSegments(buildSegments(chapterId))
        return bookId
    }

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: Long)

    @Query("UPDATE books SET updatedAt = :updatedAt WHERE id = :bookId")
    suspend fun touchBook(bookId: Long, updatedAt: Long)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: Long): ReadingProgressEntity?

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)
}
