// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.ShortEntrySettings
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.components.BackTopAppBar
import de.janaberger.lehrzeit.ui.components.SectionHeader
import de.janaberger.lehrzeit.ui.components.minLengthText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val preferences: AppPreferences) : ViewModel() {
    val shortEntries: StateFlow<ShortEntrySettings?> =
        preferences.shortEntries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (ShortEntrySettings) -> ShortEntrySettings) {
        val current = shortEntries.value ?: return
        viewModelScope.launch { preferences.setShortEntries(transform(current)) }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val shortEntries by viewModel.shortEntries.collectAsStateWithLifecycle()
    var showMinLengthDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { BackTopAppBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        val settings = shortEntries ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_section_tracking))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_discard_short)) },
                supportingContent = { Text(stringResource(R.string.settings_discard_short_summary)) },
                trailingContent = { Switch(checked = settings.enabled, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = settings.enabled,
                    role = Role.Switch,
                    onValueChange = { enabled -> viewModel.update { it.copy(enabled = enabled) } },
                ),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_min_length)) },
                supportingContent = { Text(minLengthText(settings.minSeconds)) },
                colors = if (settings.enabled) {
                    ListItemDefaults.colors()
                } else {
                    ListItemDefaults.colors(
                        headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        supportingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                },
                modifier = Modifier.clickable(enabled = settings.enabled) { showMinLengthDialog = true },
            )
        }

        if (showMinLengthDialog) {
            AlertDialog(
                onDismissRequest = { showMinLengthDialog = false },
                title = { Text(stringResource(R.string.settings_min_length)) },
                text = {
                    Column {
                        ShortEntrySettings.CHOICES_SECONDS.forEach { seconds ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = seconds == settings.minSeconds,
                                        role = Role.RadioButton,
                                        onClick = {
                                            viewModel.update { it.copy(minSeconds = seconds) }
                                            showMinLengthDialog = false
                                        },
                                    )
                                    .padding(vertical = 4.dp),
                            ) {
                                RadioButton(selected = seconds == settings.minSeconds, onClick = null)
                                Text(minLengthText(seconds), modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMinLengthDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}
