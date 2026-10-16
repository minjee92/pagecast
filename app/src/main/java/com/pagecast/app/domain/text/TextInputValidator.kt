package com.pagecast.app.domain.text

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

object TextLimits {
    /** 붙여넣기 최대 글자 수. 넘으면 TXT 불러오기를 안내한다. */
    const val MAX_PASTE_CHARS = 200_000

    /** TXT 파일 최대 크기(바이트). */
    const val MAX_FILE_BYTES = 5 * 1024 * 1024

    const val MAX_TITLE_CHARS = 120
}

sealed interface TextValidation {
    data class Valid(val text: String) : TextValidation
    data class Invalid(val reason: InvalidReason) : TextValidation
}

enum class InvalidReason {
    EMPTY,
    TOO_LONG_FOR_PASTE,
    FILE_TOO_LARGE,
    INVALID_ENCODING,
    BINARY_CONTENT,
}

/**
 * 사용자 입력(붙여넣기, TXT 파일)을 검증하고 정규화한다.
 * - 크기 제한
 * - 엄격한 UTF-8 / BOM이 있는 UTF-16 디코딩 (잘못된 바이트는 대체하지 않고 거부)
 * - NUL·제어문자 비율로 바이너리 파일 거부
 * - 줄바꿈 통일, 유니코드 NFC 정규화, `\n`·`\t` 외 제어문자 제거
 */
object TextInputValidator {

    /** 제어문자가 이 비율을 넘으면 텍스트가 아닌 것으로 본다. 바이너리 파일은 보통 10%를 크게 넘는다. */
    private const val MAX_CONTROL_CHAR_RATIO = 0.05

    fun validatePastedText(raw: String): TextValidation {
        if (raw.length > TextLimits.MAX_PASTE_CHARS) {
            return TextValidation.Invalid(InvalidReason.TOO_LONG_FOR_PASTE)
        }
        return validateDecoded(raw)
    }

    /**
     * 파일에서 읽은 바이트를 검증한다. 호출자는 [TextLimits.MAX_FILE_BYTES] + 1 바이트까지만
     * 읽어서 넘겨야 한다(초과 여부 판정용).
     */
    fun validateFileBytes(bytes: ByteArray): TextValidation {
        if (bytes.size > TextLimits.MAX_FILE_BYTES) {
            return TextValidation.Invalid(InvalidReason.FILE_TOO_LARGE)
        }
        val decoded = decodeStrict(bytes) ?: return TextValidation.Invalid(InvalidReason.INVALID_ENCODING)
        return validateDecoded(decoded)
    }

    /** 제목은 한 줄로 만들고 길이를 제한한다. 비어 있으면 null. */
    fun sanitizeTitle(raw: String): String? {
        val oneLine = removeControlChars(normalizeNewlines(raw)).replace(Regex("\\s+"), " ").trim()
        if (oneLine.isEmpty()) return null
        return Normalizer.normalize(oneLine, Normalizer.Form.NFC).take(TextLimits.MAX_TITLE_CHARS).trim()
    }

    private fun validateDecoded(raw: String): TextValidation {
        if (raw.indexOf('\u0000') >= 0 || controlCharRatio(raw) > MAX_CONTROL_CHAR_RATIO) {
            return TextValidation.Invalid(InvalidReason.BINARY_CONTENT)
        }
        val normalized = normalize(raw)
        if (normalized.isBlank()) return TextValidation.Invalid(InvalidReason.EMPTY)
        return TextValidation.Valid(normalized)
    }

    internal fun normalize(raw: String): String {
        val text = removeControlChars(normalizeNewlines(raw.removePrefix("\uFEFF")))
        return Normalizer.normalize(removeLoneSurrogates(text), Normalizer.Form.NFC).trim()
    }

    private fun normalizeNewlines(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')
            .replace('\u2028', '\n').replace('\u2029', '\n')

    private fun isDisallowedControl(c: Char): Boolean =
        (c.isISOControl() && c != '\n' && c != '\t') || c == '\uFEFF'

    private fun removeControlChars(text: String): String =
        if (text.none(::isDisallowedControl)) text else text.filterNot(::isDisallowedControl)

    private fun controlCharRatio(text: String): Double {
        if (text.isEmpty()) return 0.0
        // CR은 Windows 줄바꿈이므로 제외하고 센다.
        val count = text.count { it != '\r' && isDisallowedControl(it) && it != '\uFEFF' }
        return count.toDouble() / text.length
    }

    /** 붙여넣은 문자열에 남을 수 있는 짝 없는 서로게이트를 제거한다. */
    private fun removeLoneSurrogates(text: String): String {
        if (text.none { it.isSurrogate() }) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> {
                    sb.append(c).append(text[i + 1])
                    i += 2
                    continue
                }
                c.isSurrogate() -> Unit
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun decodeStrict(bytes: ByteArray): String? {
        val (charset, offset) = when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> Charsets.UTF_8 to 0
        }
        return decode(charset, bytes, offset)
    }

    private fun decode(charset: Charset, bytes: ByteArray, offset: Int): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
}
