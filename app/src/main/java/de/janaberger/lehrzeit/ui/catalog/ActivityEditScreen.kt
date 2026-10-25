// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.components.ArchiveDeleteSection
import de.janaberger.lehrzeit.ui.components.BackTopAppBar
import de.janaberger.lehrzeit.ui.components.CheckRow
import de.janaberger.lehrzeit.ui.components.ColorDot
import de.janaberger.lehrzeit.ui.components.DeleteConfirmDialog
import de.janaberger.lehrzeit.ui.components.DiscardChangesDialog
import de.janaberger.lehrzeit.ui.components.NameField
import de.janaberger.lehrzeit.ui.components.NoteText
import de.janaberger.lehrzeit.ui.components.SectionHeader
import de.janaberger.lehrzeit.ui.components.SelectAllNoneActions

@Composable
fun ActivityEditScreen(
    onDone: () -> Unit,
    viewModel: ActivityEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    BackHandler(enabled = state.hasChanges) { showDiscard = true }
    val requestBack = { if (state.hasChanges) showDiscard = true else onDone() }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = stringResource(if (state.isNew) R.string.activity_new_title else R.string.activity_edit_title),
                onBack = requestBack,
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.isLoading && !state.isSaving) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(contentPadding = padding, modifier = Modifier.imePadding()) {
            item(key = "name") {
                NameField(value = state.name, onValueChange = viewModel::onNameChange, error = state.nameError)
            }
            if (state.isArchived) {
                item(key = "archived-note") { NoteText(stringResource(R.string.activity_archived_note)) }
            }
            item(key = "accounts-header") {
                SectionHeader(
                    title = stringResource(R.string.accounts_title),
                    hint = stringResource(R.string.activity_accounts_hint),
                    actions = {
                        if (state.accounts.isNotEmpty()) {
                            SelectAllNoneActions(
                                onSelectAll = { viewModel.onSelectAll(true) },
                                onSelectNone = { viewModel.onSelectAll(false) },
                            )
                        }
                    },
                )
            }
            if (state.accounts.isEmpty()) {
                item(key = "accounts-empty") { NoteText(stringResource(R.string.activity_accounts_empty)) }
            }
            items(state.accounts, key = { it.id }) { account ->
                CheckRow(
                    label = account.name,
                    checked = account.id in state.checked,
                    onToggle = { viewModel.onToggleAccount(account.id) },
                    trailing = { ColorDot(account.color) },
                )
            }
            if (!state.isNew) {
                item(key = "archive-delete") {
                    ArchiveDeleteSection(
                        isArchived = state.isArchived,
                        entryCount = state.entryCount,
                        onArchive = viewModel::archive,
                        onUnarchive = viewModel::unarchive,
                        onDelete = { showDelete = true },
                    )
                }
            }
        }
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                showDiscard = false
                onDone()
            },
            onDismiss = { showDiscard = false },
        )
    }
    if (showDelete) {
        DeleteConfirmDialog(
            name = state.name,
            onConfirm = {
                showDelete = false
                viewModel.delete()
            },
            onDismiss = { showDelete = false },
        )
    }
}
