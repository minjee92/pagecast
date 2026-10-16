package com.pagecast.app.domain.model

data class Chapter(
    val id: Long,
    val bookId: Long,
    val index: Int,
    val title: String,
)
