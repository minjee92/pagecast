package com.pagecast.app.ui.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pagecast.app.R
import com.pagecast.app.domain.text.TextLimits
import com.pagecast.app.ui.AppViewModelFactory
import com.pagecast.app.ui.messageRes
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onImported: (Long) -> Unit,
    viewModel: ImportViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val importedId = viewModel.importedBookId
    LaunchedEffect(importedId) {
        if (importedId != null) {
            viewModel.onNavigated()
            onImported(importedId)
        }
    }
    val numberFormat = NumberFormat.getIntegerInstance()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.import_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.body,
                onValueChange = viewModel::onBodyChange,
                label = { Text(stringResource(R.string.import_field_body)) },
                isError = viewModel.rejectedTooLong || viewModel.failure != null,
                supportingText = {
                    val message = when {
                        viewModel.rejectedTooLong ->
                            stringResource(R.string.import_too_long, numberFormat.format(TextLimits.MAX_PASTE_CHARS))
                        viewModel.failure != null -> stringResource(viewModel.failure!!.messageRes())
                        else -> stringResource(
                            R.string.import_char_count,
                            numberFormat.format(viewModel.body.length),
                            numberFormat.format(TextLimits.MAX_PASTE_CHARS),
                        )
                    }
                    Text(message, style = MaterialTheme.typography.bodySmall)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Button(
                onClick = viewModel::save,
                enabled = viewModel.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_save))
            }
        }
    }
}
