package com.pagecast.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextInputValidatorTest {

    private fun valid(result: TextValidation): String = (result as TextValidation.Valid).text

    private fun reason(result: TextValidation): InvalidReason = (result as TextValidation.Invalid).reason

    @Test
    fun `붙여넣기 길이 제한`() {
        val atLimit = "a".repeat(TextLimits.MAX_PASTE_CHARS)
        assertEquals(atLimit, valid(TextInputValidator.validatePastedText(atLimit)))
        assertEquals(
            InvalidReason.TOO_LONG_FOR_PASTE,
            reason(TextInputValidator.validatePastedText(atLimit + "a")),
        )
    }

    @Test
    fun `빈 입력 거부`() {
        assertEquals(InvalidReason.EMPTY, reason(TextInputValidator.validatePastedText(" \n\t ")))
    }

    @Test
    fun `줄바꿈 통일과 제어문자 제거`() {
        val raw = "Line one.\r\nLine\u0007 two.\rLine three.\u200B"
        assertEquals(
            "Line one.\nLine two.\nLine three.\u200B",
            valid(TextInputValidator.validatePastedText(raw)),
        )
    }

    @Test
    fun `NFC 정규화`() {
        val decomposed = "Cafe\u0301"
        assertEquals("Café", valid(TextInputValidator.validatePastedText(decomposed)))
    }

    @Test
    fun `짝 없는 서로게이트 제거`() {
        val raw = "ok\uD800 fine 😀"
        assertEquals("ok fine 😀", valid(TextInputValidator.validatePastedText(raw)))
    }

    @Test
    fun `UTF-8 파일`() {
        val bytes = "Hello, “world”.".toByteArray(Charsets.UTF_8)
        assertEquals("Hello, “world”.", valid(TextInputValidator.validateFileBytes(bytes)))
    }

    @Test
    fun `UTF-8 BOM 제거`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Hi.".toByteArray()
        assertEquals("Hi.", valid(TextInputValidator.validateFileBytes(bytes)))
    }

    @Test
    fun `UTF-16 BOM 파일`() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Hi there.".toByteArray(Charsets.UTF_16LE)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "Hi there.".toByteArray(Charsets.UTF_16BE)
        assertEquals("Hi there.", valid(TextInputValidator.validateFileBytes(le)))
        assertEquals("Hi there.", valid(TextInputValidator.validateFileBytes(be)))
    }

    @Test
    fun `잘못된 UTF-8은 거부`() {
        // Latin-1로 저장된 "café" — 0xE9 단독 바이트는 UTF-8로 잘못된 시퀀스다.
        val bytes = byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte())
        assertEquals(InvalidReason.INVALID_ENCODING, reason(TextInputValidator.validateFileBytes(bytes)))
    }

    @Test
    fun `NUL이 있으면 바이너리로 거부`() {
        val bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x41)
        assertEquals(InvalidReason.BINARY_CONTENT, reason(TextInputValidator.validateFileBytes(bytes)))
    }

    @Test
    fun `파일 크기 제한`() {
        val bytes = ByteArray(TextLimits.MAX_FILE_BYTES + 1) { 'a'.code.toByte() }
        assertEquals(InvalidReason.FILE_TOO_LARGE, reason(TextInputValidator.validateFileBytes(bytes)))
    }

    @Test
    fun `제목 정리`() {
        assertEquals("My Story", TextInputValidator.sanitizeTitle("  My\n Story\u0000 "))
        assertNull(TextInputValidator.sanitizeTitle(" \n "))
        assertEquals(TextLimits.MAX_TITLE_CHARS, TextInputValidator.sanitizeTitle("t".repeat(500))!!.length)
    }
}
