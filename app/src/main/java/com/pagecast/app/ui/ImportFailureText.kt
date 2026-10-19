package com.pagecast.app.ui

import androidx.annotation.StringRes
import com.pagecast.app.R
import com.pagecast.app.data.importer.ImportFailure

@StringRes
fun ImportFailure.messageRes(): Int = when (this) {
    ImportFailure.EMPTY -> R.string.import_error_empty
    ImportFailure.TOO_LONG_FOR_PASTE -> R.string.import_error_too_long
    ImportFailure.FILE_TOO_LARGE -> R.string.import_error_file_too_large
    ImportFailure.INVALID_ENCODING -> R.string.import_error_encoding
    ImportFailure.BINARY_CONTENT -> R.string.import_error_binary
    ImportFailure.READ_FAILED -> R.string.import_error_read
    ImportFailure.UNSUPPORTED_FORMAT -> R.string.import_error_unsupported
}
