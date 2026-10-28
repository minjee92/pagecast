package com.pagecast.app.domain.model

/** 책별 마지막 읽은 위치. */
data class ReadingProgress(
    val bookId: Long,
    val chapterId: Long,
    val segmentIndex: Int,
    /** Segment 안에서 재개할 글자 위치. 문장 처음부터면 0. */
    val charOffset: Int,
    val updatedAt: Long,
)
