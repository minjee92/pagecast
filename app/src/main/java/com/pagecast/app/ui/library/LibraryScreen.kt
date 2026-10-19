package com.pagecast.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import com.pagecast.app.data.importer.DocumentInspector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pagecast.app.R
import com.pagecast.app.domain.model.BookSummary
import com.pagecast.app.ui.AppViewModelFactory
import com.pagecast.app.ui.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onPasteText: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var pendingDelete by remember { mutableStateOf<BookSummary?>(null) }

    val openTxt = rememberLauncherForActivityResult(OpenDocumentContract()) { uri ->
        if (uri != null) viewModel.importFile(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.OpenBook -> onOpenBook(event.bookId)
                is LibraryEvent.ImportFailed ->
                    snackbarHostState.showSnackbar(resources.getString(event.reason.messageRes()))
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.library_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { if (!importing) openTxt.launch(DocumentInspector.PICKER_MIME_TYPES) },
                    icon = { Icon(painterResource(R.drawable.ic_file), contentDescription = null) },
                    text = { Text(stringResource(R.string.library_open_txt)) },
                )
                ExtendedFloatingActionButton(
                    onClick = onPasteText,
                    icon = { Icon(painterResource(R.drawable.ic_paste), contentDescription = null) },
                    text = { Text(stringResource(R.string.library_paste)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val list = books
            when {
                importing -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.library_importing))
                }
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                list.isEmpty() -> Text(
                    text = stringResource(R.string.library_empty),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.book.id }) { summary ->
                        BookRow(
                            summary = summary,
                            onClick = { onOpenBook(summary.book.id) },
                            onDelete = { pendingDelete = summary },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_message, summary.book.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBook(summary.book.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun BookRow(summary: BookSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    val progressText = summary.lastReadIndex?.let { index ->
        val percent = if (summary.segmentCount == 0) 0 else (index + 1) * 100 / summary.segmentCount
        stringResource(R.string.library_progress, summary.segmentCount, percent)
    } ?: stringResource(R.string.library_sentences, summary.segmentCount)

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(summary.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column { Text(progressText, style = MaterialTheme.typography.bodySmall) }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
            }
        },
    )
}
