package com.pagecast.app.data.importer

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.pagecast.app.util.AppLog

/** 선택한 문서의 형식 정보. 본문은 읽지 않는다. */
data class DocumentInfo(
    val mimeType: String?,
    val displayName: String?,
    /** 구글 문서처럼 바이너리 파일이 없고 변환(내보내기)으로만 열 수 있는 문서 */
    val isVirtual: Boolean,
) {
    val isPlainText: Boolean
        get() = !isVirtual && (
            mimeType == "text/plain" ||
                // 일부 파일 제공자는 .txt에도 octet-stream을 준다.
                (mimeType == "application/octet-stream" && displayName?.endsWith(".txt", ignoreCase = true) == true)
            )
}

/**
 * SAF 문서의 형식을 확인한다.
 * 2c 단계(구글 문서·DOCX·EPUB·PDF 지원)를 준비하기 위해, debug 빌드에서는 가상 파일이
 * 어떤 형식으로 내보내기를 제공하는지 숫자 플래그로만 로그에 남긴다(파일명·본문은 남기지 않음).
 */
class DocumentInspector(context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun inspect(uri: Uri): DocumentInfo {
        var flags = 0
        var mime: String? = null
        var name: String? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                        .takeIf { it >= 0 }?.let { flags = cursor.getInt(it) }
                    cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        .takeIf { it >= 0 }?.let { mime = cursor.getString(it) }
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 }?.let { name = cursor.getString(it) }
                }
            }
        } catch (_: RuntimeException) {
            // 메타데이터를 못 읽어도 아래 getType으로 판단한다.
        }
        val info = DocumentInfo(
            mimeType = mime ?: contentResolver.getType(uri),
            displayName = name,
            isVirtual = (flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0,
        )
        if (isDebuggable && !info.isPlainText) logExportOptions(uri, info)
        return info
    }

    private fun logExportOptions(uri: Uri, info: DocumentInfo) {
        val streamTypes = try {
            contentResolver.getStreamTypes(uri, "*/*")?.toSet().orEmpty()
        } catch (_: RuntimeException) {
            emptySet()
        }
        fun has(type: String) = if (type in streamTypes) 1 else 0
        AppLog.d(
            "import_unsupported_document",
            "virtual" to if (info.isVirtual) 1 else 0,
            "googleDoc" to if (info.mimeType == MIME_GOOGLE_DOC) 1 else 0,
            "streamTypes" to streamTypes.size,
            "textPlain" to has("text/plain"),
            "docx" to has(MIME_DOCX),
            "pdf" to has(MIME_PDF),
            "epub" to has(MIME_EPUB),
        )
    }

    companion object {
        const val MIME_PDF = "application/pdf"
        const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val MIME_EPUB = "application/epub+zip"
        const val MIME_GOOGLE_DOC = "application/vnd.google-apps.document"

        /** 선택기에 보여 줄 형식. TXT 외에는 고르면 "아직 지원하지 않음" 안내를 보여 준다. */
        val PICKER_MIME_TYPES = arrayOf("text/plain", MIME_PDF, MIME_DOCX, MIME_EPUB, MIME_GOOGLE_DOC)
    }
}
