package com.pagecast.app.ui.importer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pagecast.app.data.importer.BookImporter
import com.pagecast.app.data.importer.ImportFailure
import com.pagecast.app.data.importer.ImportResult
import com.pagecast.app.domain.text.TextLimits
import kotlinx.coroutines.launch

/**
 * 붙여넣기 화면 상태.
 * 본문은 ViewModel 메모리에만 둔다. SavedState 번들에 넣으면 큰 글에서
 * TransactionTooLargeException이 날 수 있고, 본문이 디스크(상태 저장)에 남기 때문이다.
 */
class ImportViewModel(private val importer: BookImporter) : ViewModel() {

    var title by mutableStateOf("")
        private set
    var body by mutableStateOf("")
        private set

    /** 한도를 넘는 입력을 시도했는지. 넘는 입력은 반영하지 않고 TXT 불러오기를 안내한다. */
    var rejectedTooLong by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var failure by mutableStateOf<ImportFailure?>(null)
        private set
    var importedBookId by mutableStateOf<Long?>(null)
        private set

    val canSave: Boolean get() = body.isNotBlank() && !saving

    fun onTitleChange(value: String) {
        title = value.take(TextLimits.MAX_TITLE_CHARS)
    }

    fun onBodyChange(value: String) {
        if (value.length > TextLimits.MAX_PASTE_CHARS) {
            rejectedTooLong = true
            return
        }
        rejectedTooLong = false
        failure = null
        body = value
    }

    fun save() {
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            when (val result = importer.importPasted(title, body)) {
                is ImportResult.Success -> {
                    body = ""
                    importedBookId = result.bookId
                }
                is ImportResult.Failure -> failure = result.reason
            }
            saving = false
        }
    }

    fun onNavigated() {
        importedBookId = null
    }
}
