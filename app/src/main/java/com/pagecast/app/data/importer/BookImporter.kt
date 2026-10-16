package com.pagecast.app.data.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.pagecast.app.domain.model.SourceType
import com.pagecast.app.domain.repository.BookRepository
import com.pagecast.app.domain.text.InvalidReason
import com.pagecast.app.domain.text.SentenceSplitter
import com.pagecast.app.domain.text.TextInputValidator
import com.pagecast.app.domain.text.TextLimits
import com.pagecast.app.domain.text.TextValidation
import com.pagecast.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

sealed interface ImportResult {
    data class Success(val bookId: Long) : ImportResult
    data class Failure(val reason: ImportFailure) : ImportResult
}

enum class ImportFailure {
    EMPTY,
    TOO_LONG_FOR_PASTE,
    FILE_TOO_LARGE,
    INVALID_ENCODING,
    BINARY_CONTENT,
    READ_FAILED,
}

/**
 * 붙여넣은 텍스트나 TXT 파일을 검증 → 문장 분할 → 저장까지 처리한다.
 * 본문은 이 클래스 밖(로그, 화면 간 인자)으로 나가지 않고 저장 후 bookId만 반환한다.
 */
class BookImporter(
    private val contentResolver: ContentResolver,
    private val repository: BookRepository,
    private val splitter: SentenceSplitter,
) {

    suspend fun importPasted(title: String, raw: String): ImportResult =
        save(
            validation = withContext(Dispatchers.Default) { TextInputValidator.validatePastedText(raw) },
            title = TextInputValidator.sanitizeTitle(title),
            sourceType = SourceType.PASTE,
        )

    suspend fun importTxt(uri: Uri): ImportResult {
        val bytes = try {
            withContext(Dispatchers.IO) { readBounded(uri) }
        } catch (e: IOException) {
            AppLog.w("txt_read_failed")
            return ImportResult.Failure(ImportFailure.READ_FAILED)
        } catch (e: SecurityException) {
            AppLog.w("txt_read_denied")
            return ImportResult.Failure(ImportFailure.READ_FAILED)
        }
        val validation = withContext(Dispatchers.Default) { TextInputValidator.validateFileBytes(bytes) }
        val title = withContext(Dispatchers.IO) { displayName(uri) }
            ?.substringBeforeLast('.')
            ?.let(TextInputValidator::sanitizeTitle)
        return save(validation, title, SourceType.TXT)
    }

    private suspend fun save(validation: TextValidation, title: String?, sourceType: SourceType): ImportResult {
        val text = when (validation) {
            is TextValidation.Invalid -> return ImportResult.Failure(validation.reason.toFailure())
            is TextValidation.Valid -> validation.text
        }
        val sentences = withContext(Dispatchers.Default) { splitter.split(text) }
        if (sentences.isEmpty()) return ImportResult.Failure(ImportFailure.EMPTY)
        val bookId = repository.createBook(
            title = title ?: defaultTitle(sentences.first().text),
            sourceType = sourceType,
            sentences = sentences,
        )
        AppLog.d("book_imported", "chars" to text.length, "segments" to sentences.size)
        return ImportResult.Success(bookId)
    }

    /**
     * 최대 [TextLimits.MAX_FILE_BYTES] + 1 바이트까지만 읽는다.
     * 파일 제공자가 알려주는 크기는 믿지 않고, 실제로 읽은 양으로 초과 여부를 판정한다.
     */
    private fun readBounded(uri: Uri): ByteArray {
        val input: InputStream = contentResolver.openInputStream(uri) ?: throw IOException("no stream")
        return input.use { stream ->
            val limit = TextLimits.MAX_FILE_BYTES + 1
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (out.size() < limit) {
                val read = stream.read(buffer, 0, minOf(buffer.size, limit - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: RuntimeException) {
        null
    }

    private fun defaultTitle(firstSentence: String): String =
        TextInputValidator.sanitizeTitle(firstSentence.take(40)) ?: "Untitled"

    private fun InvalidReason.toFailure(): ImportFailure = when (this) {
        InvalidReason.EMPTY -> ImportFailure.EMPTY
        InvalidReason.TOO_LONG_FOR_PASTE -> ImportFailure.TOO_LONG_FOR_PASTE
        InvalidReason.FILE_TOO_LARGE -> ImportFailure.FILE_TOO_LARGE
        InvalidReason.INVALID_ENCODING -> ImportFailure.INVALID_ENCODING
        InvalidReason.BINARY_CONTENT -> ImportFailure.BINARY_CONTENT
    }
}
