package com.pagecast.app.domain.model

/**
 * 책별 캐릭터-목소리 캐스팅. 4단계부터 사용한다.
 * [characterId]가 null이면 그 책의 내레이터 목소리다.
 */
data class VoiceCast(
    val id: Long,
    val bookId: Long,
    val characterId: Long?,
    /** [com.pagecast.app.tts.TtsEngine.id] */
    val engineId: String,
    val voiceId: String,
    val pitch: Float = 1f,
    val rate: Float = 1f,
)
