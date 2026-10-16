package com.pagecast.app.domain.model

/**
 * 낭독 단위. 1단계에서는 문장 하나가 Segment 하나이고,
 * 3단계(LLM 대본화)부터는 `{화자, 대사, 감정}` 단위가 된다.
 */
data class Segment(
    val id: Long,
    val chapterId: Long,
    /** Chapter 안에서의 순서 (0부터). 재생 위치·하이라이트의 기준. */
    val index: Int,
    /** 화면에 문단을 다시 구성하기 위한 문단 번호 (0부터). */
    val paragraphIndex: Int,
    val text: String,
    /** 화자 [Character.id]. null이면 내레이터. */
    val speakerId: Long? = null,
    val emotion: Emotion = Emotion.NEUTRAL,
)
