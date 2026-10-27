// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.time.localDateOf
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.components.BackTopAppBar
import de.janaberger.lehrzeit.ui.components.NoteText
import de.janaberger.lehrzeit.ui.components.SectionHeader
import de.janaberger.lehrzeit.ui.components.formatDate
import de.janaberger.lehrzeit.ui.components.timeOfDay
import java.time.ZoneId

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources by rememberUpdatedState(LocalResources.current)
    val snackbarHostState = remember { SnackbarHostState() }
    val fileName = viewModel.suggestedFileName()
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.createBackup(uri, fileName)
    }
    val startRestore = rememberRestoreLauncher(viewModel)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    is BackupEvent.Saved -> resources.getString(R.string.backup_saved, event.fileName)
                    BackupEvent.Restored -> resources.getString(R.string.backup_restored)
                },
            )
        }
    }

    Scaffold(
        topBar = { BackTopAppBar(stringResource(R.string.backup_title), onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.backup_section_create))
            NoteText(stringResource(R.string.backup_create_summary))
            NoteText(
                state.lastBackupAt?.let { stringResource(R.string.backup_last, dateTime(it)) }
                    ?: stringResource(R.string.backup_never),
            )
            Button(
                onClick = { createLauncher.launch(fileName) },
                enabled = !state.isWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.backup_create)) }

            SectionHeader(stringResource(R.string.backup_section_restore), Modifier.padding(top = 16.dp))
            NoteText(stringResource(R.string.backup_restore_summary))
            OutlinedButton(
                onClick = startRestore,
                enabled = !state.isWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.backup_restore)) }
        }
    }

    BackupDialogs(state, viewModel)
}

/** Opens the file picker; the picked file is checked and then needs confirming (see [BackupDialogs]). */
@Composable
fun rememberRestoreLauncher(viewModel: BackupViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.inspect(uri)
    }
    // Some file managers label zip files differently, so everything is offered and checked after picking.
    return { launcher.launch(arrayOf("*/*")) }
}

/** The restore confirmation and error dialogs; used by the backup screen and the welcome setup. */
@Composable
fun BackupDialogs(state: BackupUiState, viewModel: BackupViewModel) {
    state.pendingRestore?.let { backup ->
        val activities = backup.activities.count { !it.isGeneral }
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.backup_confirm_summary,
                            dateTime(backup.createdAtUtc),
                            pluralStringResource(R.plurals.backup_activities, activities, activities),
                            pluralStringResource(R.plurals.backup_classes, backup.classes.size, backup.classes.size),
                            pluralStringResource(R.plurals.backup_entries, backup.entries.size, backup.entries.size),
                        ),
                    )
                    Text(stringResource(R.string.backup_confirm_warning), modifier = Modifier.padding(top = 12.dp))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRestore,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.backup_title)) },
            text = {
                Text(
                    stringResource(
                        when (error) {
                            BackupError.WRITE_FAILED -> R.string.backup_error_write
                            BackupError.NOT_A_BACKUP -> R.string.backup_error_not_backup
                            BackupError.DAMAGED -> R.string.backup_error_damaged
                            BackupError.TOO_NEW -> R.string.backup_error_too_new
                            BackupError.TOO_OLD -> R.string.backup_error_too_old
                            BackupError.RESTORE_FAILED -> R.string.backup_error_restore
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun dateTime(utc: Long): String = "${formatDate(localDateOf(utc, ZoneId.systemDefault()), withYear = true)}, ${timeOfDay(utc)}"
