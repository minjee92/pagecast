package com.pagecast.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pagecast.app.domain.model.Emotion
import com.pagecast.app.domain.model.SourceType

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val language: String,
    val sourceType: SourceType,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bookId", "chapterIndex"], unique = true)],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    @ColumnInfo(name = "chapterIndex") val index: Int,
    val title: String,
)

@Entity(
    tableName = "characters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val name: String,
    val description: String?,
)

@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["speakerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["chapterId", "segmentIndex"], unique = true),
        Index("speakerId"),
    ],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterId: Long,
    @ColumnInfo(name = "segmentIndex") val index: Int,
    val paragraphIndex: Int,
    val text: String,
    val speakerId: Long?,
    val emotion: Emotion,
)

/**
 * SQLite의 UNIQUE는 NULL끼리 중복으로 보지 않으므로, 내레이터(characterId = null) 행의
 * 책당 1개 제약은 4단계 저장 로직(upsert)에서 보장해야 한다.
 */
@Entity(
    tableName = "voice_casts",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["bookId", "characterId"], unique = true),
        Index("characterId"),
    ],
)
data class VoiceCastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val characterId: Long?,
    val engineId: String,
    val voiceId: String,
    val pitch: Float,
    val rate: Float,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chapterId")],
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: Long,
    val chapterId: Long,
    val segmentIndex: Int,
    val charOffset: Int,
    val updatedAt: Long,
)

/** 서재 목록 조회 결과. */
data class BookSummaryRow(
    @Embedded val book: BookEntity,
    val segmentCount: Int,
    val lastReadIndex: Int?,
)
