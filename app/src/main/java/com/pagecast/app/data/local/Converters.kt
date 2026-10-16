package com.pagecast.app.data.local

import androidx.room.TypeConverter
import com.pagecast.app.domain.model.Emotion
import com.pagecast.app.domain.model.SourceType

/** enum은 이름 문자열로 저장한다(순서가 바뀌어도 안전). */
class Converters {
    @TypeConverter
    fun emotionToString(value: Emotion): String = value.name

    @TypeConverter
    fun stringToEmotion(value: String): Emotion = Emotion.fromTag(value)

    @TypeConverter
    fun sourceTypeToString(value: SourceType): String = value.name

    @TypeConverter
    fun stringToSourceType(value: String): SourceType =
        SourceType.entries.firstOrNull { it.name == value } ?: SourceType.PASTE
}
