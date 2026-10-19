package com.pagecast.app.data.importer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentInfoTest {

    @Test
    fun `일반 TXT는 지원`() {
        assertTrue(DocumentInfo("text/plain", "story.txt", isVirtual = false).isPlainText)
    }

    @Test
    fun `octet-stream이어도 txt 확장자면 지원`() {
        assertTrue(DocumentInfo("application/octet-stream", "Story.TXT", isVirtual = false).isPlainText)
        assertFalse(DocumentInfo("application/octet-stream", "story.bin", isVirtual = false).isPlainText)
    }

    @Test
    fun `구글 문서 같은 가상 파일은 미지원`() {
        assertFalse(DocumentInfo(DocumentInspector.MIME_GOOGLE_DOC, "My draft", isVirtual = true).isPlainText)
        // 가상 파일은 text/plain으로 보이더라도 직접 열 수 없다.
        assertFalse(DocumentInfo("text/plain", "draft", isVirtual = true).isPlainText)
    }

    @Test
    fun `PDF DOCX EPUB은 미지원`() {
        listOf(DocumentInspector.MIME_PDF, DocumentInspector.MIME_DOCX, DocumentInspector.MIME_EPUB).forEach {
            assertFalse(DocumentInfo(it, "book", isVirtual = false).isPlainText)
        }
    }
}
