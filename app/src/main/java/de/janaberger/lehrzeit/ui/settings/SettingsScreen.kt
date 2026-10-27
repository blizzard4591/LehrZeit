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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.ReminderSettings
import de.janaberger.lehrzeit.data.ShortEntrySettings
import de.janaberger.lehrzeit.tracking.TrackingController
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.components.BackTopAppBar
import de.janaberger.lehrzeit.ui.components.SectionHeader
import de.janaberger.lehrzeit.ui.components.SwitchRow
import de.janaberger.lehrzeit.ui.components.minLengthText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val controller: TrackingController,
) : ViewModel() {
    val shortEntries: StateFlow<ShortEntrySettings?> =
        preferences.shortEntries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reminder: StateFlow<ReminderSettings?> =
        preferences.reminder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateShortEntries(transform: (ShortEntrySettings) -> ShortEntrySettings) {
        val current = shortEntries.value ?: return
        viewModelScope.launch { preferences.setShortEntries(transform(current)) }
    }

    /** Also reschedules (or cancels) the reminder for a running timer. */
    fun updateReminder(transform: (ReminderSettings) -> ReminderSettings) {
        val current = reminder.value ?: return
        viewModelScope.launch {
            preferences.setReminder(transform(current))
            controller.refreshNotification()
        }
    }
}

private enum class SettingsDialog { MIN_LENGTH, REMINDER_HOURS }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val shortEntries by viewModel.shortEntries.collectAsStateWithLifecycle()
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    Scaffold(topBar = { BackTopAppBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        val short = shortEntries ?: return@Scaffold
        val remind = reminder ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_section_tracking))
            SwitchRow(
                title = stringResource(R.string.settings_discard_short),
                summary = stringResource(R.string.settings_discard_short_summary),
                checked = short.enabled,
                onCheckedChange = { enabled -> viewModel.updateShortEntries { it.copy(enabled = enabled) } },
            )
            ValueRow(
                title = stringResource(R.string.settings_min_length),
                value = minLengthText(short.minSeconds),
                enabled = short.enabled,
                onClick = { dialog = SettingsDialog.MIN_LENGTH },
            )

            SectionHeader(stringResource(R.string.settings_section_reminder), Modifier.padding(top = 8.dp))
            SwitchRow(
                title = stringResource(R.string.settings_reminder),
                summary = stringResource(R.string.settings_reminder_summary),
                checked = remind.enabled,
                onCheckedChange = { enabled -> viewModel.updateReminder { it.copy(enabled = enabled) } },
            )
            ValueRow(
                title = stringResource(R.string.settings_reminder_after),
                value = pluralStringResource(R.plurals.duration_hours, remind.hours, remind.hours),
                enabled = remind.enabled,
                onClick = { dialog = SettingsDialog.REMINDER_HOURS },
            )
        }

        when (dialog) {
            SettingsDialog.MIN_LENGTH -> ChoiceDialog(
                title = stringResource(R.string.settings_min_length),
                choices = ShortEntrySettings.CHOICES_SECONDS.map { it to minLengthText(it) },
                selected = short.minSeconds,
                onSelect = { seconds -> viewModel.updateShortEntries { it.copy(minSeconds = seconds) } },
                onDismiss = { dialog = null },
            )
            SettingsDialog.REMINDER_HOURS -> ChoiceDialog(
                title = stringResource(R.string.settings_reminder_after),
                choices = ReminderSettings.CHOICES_HOURS.map { it to pluralStringResource(R.plurals.duration_hours, it, it) },
                selected = remind.hours,
                onSelect = { hours -> viewModel.updateReminder { it.copy(hours = hours) } },
                onDismiss = { dialog = null },
            )
            null -> Unit
        }
    }
}

/** A setting showing its current value; greyed out while the switch above it is off. */
@Composable
private fun ValueRow(title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        colors = if (enabled) ListItemDefaults.colors() else ListItemDefaults.colors(headlineColor = disabled, supportingColor = disabled),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(value)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
