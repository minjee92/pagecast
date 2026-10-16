package com.pagecast.app.domain.text

/** 문장 분할 결과 한 개. 저장 시 [com.pagecast.app.domain.model.Segment]가 된다. */
data class SplitSentence(
    val paragraphIndex: Int,
    val text: String,
)
