package com.pagecast.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 테스트 문장은 모두 직접 작성한 짧은 예문이다(저작권 있는 본문을 쓰지 않는다).
class SentenceSplitterTest {

    private val splitter = SentenceSplitter()

    private fun sentences(text: String) = splitter.split(text).map { it.text }

    @Test
    fun `기본 문장 분할`() {
        assertEquals(
            listOf("The rain stopped.", "Mina opened the window!", "Was anyone there?"),
            sentences("The rain stopped. Mina opened the window! Was anyone there?"),
        )
    }

    @Test
    fun `호칭 약어 뒤에서는 끊지 않는다`() {
        assertEquals(
            listOf("Mr. Hale met Dr. Ito at noon.", "They talked."),
            sentences("Mr. Hale met Dr. Ito at noon. They talked."),
        )
    }

    @Test
    fun `이름 이니셜과 점이 섞인 약어도 합친다`() {
        assertEquals(
            listOf("She read a novel by J. R. Moss, e.g. the long one.", "It was late."),
            sentences("She read a novel by J. R. Moss, e.g. the long one. It was late."),
        )
    }

    @Test
    fun `따옴표 대사`() {
        assertEquals(
            listOf("\"Wait here.\"", "\"Why?\"", "\"Because I said so.\""),
            sentences("\"Wait here.\" \"Why?\" \"Because I said so.\""),
        )
    }

    @Test
    fun `빈 줄이 있으면 빈 줄로 문단을 나누고 줄바꿈은 합친다`() {
        val result = splitter.split("The fox ran\ninto the woods.\n\nThe owl watched.\n\n\n")
        assertEquals(
            listOf(SplitSentence(0, "The fox ran into the woods."), SplitSentence(1, "The owl watched.")),
            result,
        )
    }

    @Test
    fun `빈 줄이 없으면 줄마다 문단이다`() {
        val result = splitter.split("First line here.\nSecond line here.")
        assertEquals(listOf(0, 1), result.map { it.paragraphIndex })
    }

    @Test
    fun `빈 입력은 빈 결과`() {
        assertTrue(splitter.split("   \n\n  ").isEmpty())
    }

    @Test
    fun `너무 긴 문장은 쉼표에서 나눈다`() {
        val small = SentenceSplitter(maxSentenceLength = 40)
        val long = "The road went on and on past the hills, and the travelers kept walking until dusk."
        val chunks = small.split(long).map { it.text }
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 40 })
        assertEquals(long, chunks.joinToString(" "))
    }

    @Test
    fun `약어 판정`() {
        assertTrue(splitter.endsWithAbbreviation("He called Mrs."))
        assertTrue(splitter.endsWithAbbreviation("(Dr."))
        assertFalse(splitter.endsWithAbbreviation("It ended."))
        assertFalse(splitter.endsWithAbbreviation("Bring pens, paper, etc."))
    }
}
