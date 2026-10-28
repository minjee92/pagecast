package com.pagecast.app.playback

/** 화면과 재생 서비스가 구독하는 낭독 상태. */
data class NarrationState(
    val bookId: Long? = null,
    val chapterId: Long? = null,
    val bookTitle: String = "",
    val segmentCount: Int = 0,
    val currentIndex: Int = 0,
    /** 현재 문장 안에서 지금 읽는 단어 범위(문장 텍스트 기준). 엔진이 알려주지 않으면 null. */
    val wordRange: IntRange? = null,
    /** 재개할 때 쓸 현재 문장 안의 글자 위치. */
    val resumeOffset: Int = 0,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
    val engineStatus: EngineStatus = EngineStatus.NOT_INITIALIZED,
    /** 엔진이 단어 위치 이벤트를 보내는지. 아직 모르면 null. */
    val wordEventsSupported: Boolean? = null,
    /** 마지막 문장까지 다 읽었는지. */
    val finished: Boolean = false,
) {
    val hasContent: Boolean get() = segmentCount > 0
}

enum class EngineStatus {
    NOT_INITIALIZED,
    INITIALIZING,
    READY,
    ENGINE_UNAVAILABLE,
    LANGUAGE_UNAVAILABLE,
    ERROR,
}
