package com.pagecast.app.domain.model

/** 책(또는 한 편의 글). 본문은 [Chapter] → [Segment]로 나뉘어 저장된다. */
data class Book(
    val id: Long,
    val title: String,
    /** BCP-47 언어 태그. 1단계는 영어 원서만 다루므로 기본값 "en". */
    val language: String,
    val sourceType: SourceType,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class SourceType {
    /** 붙여넣은 텍스트 */
    PASTE,

    /** 불러온 TXT 파일 */
    TXT,

    /** 2단계: 카메라 촬영 + OCR */
    OCR,
}

/** 서재 목록 한 줄에 필요한 요약 정보. */
data class BookSummary(
    val book: Book,
    val segmentCount: Int,
    /** 마지막으로 읽은 Segment index. 한 번도 읽지 않았으면 null. */
    val lastReadIndex: Int?,
)

/** 리더 화면에 필요한 책 전체 내용. 1단계는 Chapter가 하나뿐이다. */
data class BookContent(
    val book: Book,
    val chapter: Chapter,
    val segments: List<Segment>,
)
