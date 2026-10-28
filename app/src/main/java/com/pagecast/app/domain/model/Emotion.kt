package com.pagecast.app.domain.model

/**
 * 대사의 감정 지시. 3단계 LLM이 내보내는 태그를 이 값으로 매핑한다.
 * 알 수 없는 태그는 [NEUTRAL]로 처리해 LLM 출력이 바뀌어도 앱이 깨지지 않게 한다.
 */
enum class Emotion {
    NEUTRAL,
    HAPPY,
    SAD,
    ANGRY,
    FEARFUL,
    SURPRISED,
    EXCITED,
    TENDER,
    WHISPER,
    ;

    companion object {
        fun fromTag(tag: String?): Emotion {
            val normalized = tag?.trim() ?: return NEUTRAL
            return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: NEUTRAL
        }
    }
}
