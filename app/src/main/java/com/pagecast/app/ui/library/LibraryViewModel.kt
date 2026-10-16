package com.pagecast.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pagecast.app.data.importer.BookImporter
import com.pagecast.app.data.importer.ImportFailure
import com.pagecast.app.data.importer.ImportResult
import com.pagecast.app.domain.model.BookSummary
import com.pagecast.app.domain.repository.BookRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: BookRepository,
    private val importer: BookImporter,
) : ViewModel() {

    val books: StateFlow<List<BookSummary>?> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    fun importTxt(uri: Uri) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            val event = when (val result = importer.importTxt(uri)) {
                is ImportResult.Success -> LibraryEvent.OpenBook(result.bookId)
                is ImportResult.Failure -> LibraryEvent.ImportFailed(result.reason)
            }
            _importing.value = false
            _events.send(event)
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch { repository.deleteBook(bookId) }
    }
}

sealed interface LibraryEvent {
    data class OpenBook(val bookId: Long) : LibraryEvent
    data class ImportFailed(val reason: ImportFailure) : LibraryEvent
}
