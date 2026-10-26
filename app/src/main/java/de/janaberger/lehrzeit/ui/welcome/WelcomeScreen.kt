// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.backup.BackupDialogs
import de.janaberger.lehrzeit.ui.backup.BackupViewModel
import de.janaberger.lehrzeit.ui.backup.rememberRestoreLauncher

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
    backupViewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    // A restored backup completes the welcome setup, so the app moves on by itself.
    val startRestore = rememberRestoreLauncher(backupViewModel)
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                )
                Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.welcome_intro), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.welcome_question), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = viewModel::onInputChange,
                        label = { Text(stringResource(R.string.welcome_field)) },
                        singleLine = true,
                        isError = state.inputError != null,
                        supportingText = state.inputError?.let { error ->
                            {
                                Text(
                                    stringResource(
                                        when (error) {
                                            WelcomeInputError.DUPLICATE -> R.string.welcome_error_duplicate
                                            WelcomeInputError.TAKEN -> R.string.error_name_taken
                                        },
                                    ),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { viewModel.addInput() }),
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = viewModel::addInput,
                        enabled = state.input.isNotBlank(),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add))
                    }
                }
                if (state.names.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.names.forEach { name ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.remove(name) },
                                label = { Text(name) },
                                trailingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_close),
                                        contentDescription = stringResource(R.string.welcome_remove, name),
                                        modifier = Modifier.size(InputChipDefaults.AvatarSize),
                                    )
                                },
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.welcome_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = startRestore, enabled = !backupState.isWorking) {
                    Text(stringResource(R.string.welcome_restore))
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                TextButton(onClick = viewModel::skip, enabled = !state.isSaving) {
                    Text(stringResource(R.string.welcome_skip))
                }
                Button(onClick = viewModel::finish, enabled = state.canFinish) {
                    Text(stringResource(R.string.welcome_done))
                }
            }
        }
    }
    BackupDialogs(backupState, backupViewModel)
}
