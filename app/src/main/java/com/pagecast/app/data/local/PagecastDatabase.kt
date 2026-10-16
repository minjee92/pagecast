package com.pagecast.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        CharacterEntity::class,
        SegmentEntity::class,
        VoiceCastEntity::class,
        ReadingProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PagecastDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        private const val NAME = "pagecast.db"

        // 스키마 변경 시 Migration을 추가한다. 사용자의 책 데이터를 지우는
        // fallbackToDestructiveMigration은 쓰지 않는다.
        fun create(context: Context): PagecastDatabase =
            Room.databaseBuilder(context.applicationContext, PagecastDatabase::class.java, NAME)
                .build()
    }
}
