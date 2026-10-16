package com.pagecast.app.data.repository

import com.pagecast.app.data.local.BookEntity
import com.pagecast.app.data.local.BookSummaryRow
import com.pagecast.app.data.local.ChapterEntity
import com.pagecast.app.data.local.ReadingProgressEntity
import com.pagecast.app.data.local.SegmentEntity
import com.pagecast.app.domain.model.Book
import com.pagecast.app.domain.model.BookSummary
import com.pagecast.app.domain.model.Chapter
import com.pagecast.app.domain.model.ReadingProgress
import com.pagecast.app.domain.model.Segment

internal fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    language = language,
    sourceType = sourceType,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun BookSummaryRow.toDomain() = BookSummary(
    book = book.toDomain(),
    segmentCount = segmentCount,
    lastReadIndex = lastReadIndex,
)

internal fun ChapterEntity.toDomain() = Chapter(id = id, bookId = bookId, index = index, title = title)

internal fun SegmentEntity.toDomain() = Segment(
    id = id,
    chapterId = chapterId,
    index = index,
    paragraphIndex = paragraphIndex,
    text = text,
    speakerId = speakerId,
    emotion = emotion,
)

internal fun ReadingProgressEntity.toDomain() = ReadingProgress(
    bookId = bookId,
    chapterId = chapterId,
    segmentIndex = segmentIndex,
    charOffset = charOffset,
    updatedAt = updatedAt,
)

internal fun ReadingProgress.toEntity() = ReadingProgressEntity(
    bookId = bookId,
    chapterId = chapterId,
    segmentIndex = segmentIndex,
    charOffset = charOffset,
    updatedAt = updatedAt,
)
