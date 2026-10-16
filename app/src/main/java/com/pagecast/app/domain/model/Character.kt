package com.pagecast.app.domain.model

/** 책에 등장하는 인물. 3단계(대본화)부터 사용한다. */
data class Character(
    val id: Long,
    val bookId: Long,
    val name: String,
    val description: String? = null,
)
