package com.pagecast.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pagecast.app.PagecastApp
import com.pagecast.app.playback.PlaybackClient
import com.pagecast.app.ui.importer.ImportViewModel
import com.pagecast.app.ui.library.LibraryViewModel
import com.pagecast.app.ui.reader.ReaderViewModel

/** 수동 DI: [com.pagecast.app.di.AppContainer]에서 의존성을 꺼내 ViewModel을 만든다. */
object AppViewModelFactory {
    val Factory = viewModelFactory {
        initializer {
            val container = (this[APPLICATION_KEY] as PagecastApp).container
            LibraryViewModel(container.bookRepository, container.bookImporter)
        }
        initializer {
            val container = (this[APPLICATION_KEY] as PagecastApp).container
            ImportViewModel(container.bookImporter)
        }
        initializer {
            val container = (this[APPLICATION_KEY] as PagecastApp).container
            ReaderViewModel(
                savedStateHandle = createSavedStateHandle(),
                repository = container.bookRepository,
                narration = container.narrationController,
                playback = PlaybackClient(container.appContext),
            )
        }
    }
}
