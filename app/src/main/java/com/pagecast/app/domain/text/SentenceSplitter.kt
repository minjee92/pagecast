package com.pagecast.app.domain.text

import java.text.BreakIterator
import java.util.Locale

/**
 * 본문을 문단 → 문장으로 나눈다.
 *
 * - 문장 경계는 `java.text.BreakIterator`(문장 단위)에 맡기고,
 *   "Mr. Smith"처럼 약어 뒤에서 잘못 끊긴 경우만 다음 문장과 다시 합친다.
 * - 빈 줄이 있는 글은 빈 줄을 문단 구분으로 보고 문단 안의 줄바꿈은 공백으로 합친다
 *   (하드 랩된 원고). 빈 줄이 전혀 없으면 줄마다 한 문단으로 본다.
 * - TTS 입력 한도와 하이라이트 가독성을 위해 너무 긴 문장은 [maxSentenceLength] 이하로 더 나눈다.
 */
class SentenceSplitter(
    private val locale: Locale = Locale.US,
    private val maxSentenceLength: Int = DEFAULT_MAX_SENTENCE_LENGTH,
) {

    fun split(text: String): List<SplitSentence> {
        val result = ArrayList<SplitSentence>()
        splitParagraphs(text).forEachIndexed { paragraphIndex, paragraph ->
            splitSentences(paragraph).forEach { sentence ->
                chunkLongSentence(sentence).forEach { result += SplitSentence(paragraphIndex, it) }
            }
        }
        return result
    }

    internal fun splitParagraphs(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val hasBlankLines = BLANK_LINE.containsMatchIn(normalized)
        val rawParagraphs = if (hasBlankLines) {
            normalized.split(BLANK_LINE).map { it.replace('\n', ' ') }
        } else {
            normalized.split('\n')
        }
        return rawParagraphs.map { it.collapseWhitespace() }.filter { it.isNotEmpty() }
    }

    internal fun splitSentences(paragraph: String): List<String> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(paragraph)
        val raw = ArrayList<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            paragraph.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(raw::add)
            start = end
            end = iterator.next()
        }
        return mergeAbbreviations(raw)
    }

    /** 약어로 끝나는 조각은 다음 조각과 합친다. */
    private fun mergeAbbreviations(pieces: List<String>): List<String> {
        val merged = ArrayList<String>(pieces.size)
        var pending: String? = null
        for (piece in pieces) {
            val current = pending?.let { "$it $piece" } ?: piece
            pending = if (endsWithAbbreviation(current)) current else {
                merged += current
                null
            }
        }
        pending?.let(merged::add)
        return merged
    }

    internal fun endsWithAbbreviation(sentence: String): Boolean {
        if (!sentence.endsWith('.')) return false
        val lastToken = sentence.substringAfterLast(' ').trimStart(*OPENING_PUNCTUATION)
        val word = lastToken.dropLast(1)
        if (word.isEmpty()) return false
        // "J." 같은 이름 이니셜
        if (word.length == 1 && word[0].isUpperCase()) return true
        // "e.g." / "i.e." / "U.S." 처럼 마침표가 섞인 약어
        if (DOTTED_ABBREVIATION.matches(lastToken)) return true
        return word.lowercase(Locale.ROOT) in ABBREVIATIONS
    }

    internal fun chunkLongSentence(sentence: String): List<String> {
        if (sentence.length <= maxSentenceLength) return listOf(sentence)
        val chunks = ArrayList<String>()
        var rest = sentence
        while (rest.length > maxSentenceLength) {
            val window = rest.substring(0, maxSentenceLength)
            // 쉼표·세미콜론 같은 자연스러운 쉼에서 먼저 자르고, 없으면 공백에서 자른다.
            val cut = CLAUSE_BREAKS.maxOf { window.lastIndexOf(it) }
                .takeIf { it > maxSentenceLength / 2 }?.plus(1)
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: maxSentenceLength
            chunks += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) chunks += rest
        return chunks
    }

    private fun String.collapseWhitespace(): String = replace(WHITESPACE, " ").trim()

    companion object {
        const val DEFAULT_MAX_SENTENCE_LENGTH = 600

        private val BLANK_LINE = Regex("\\n[ \\t]*\\n")
        private val WHITESPACE = Regex("[ \\t\\u00A0]+")
        private val DOTTED_ABBREVIATION = Regex("(?:[A-Za-z]\\.){2,}")
        private val OPENING_PUNCTUATION = charArrayOf('"', '\'', '(', '[', '“', '‘')
        private val CLAUSE_BREAKS = listOf(";", ",", ":", "—", " -")

        /**
         * 마침표 없이 비교한다. 뒤에 거의 항상 이름·명사가 오는 약어만 넣는다.
         * "etc.", "Inc.", "no."처럼 문장 끝에도 자주 오는 약어는 넣지 않는다.
         */
        private val ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "mx", "dr", "prof", "st", "sr", "jr", "rev", "hon",
            "capt", "col", "gen", "lt", "sgt", "cmdr", "gov", "pres", "sen", "rep",
            "mt", "ft", "vs", "approx", "dept", "fig",
        )
    }
}
