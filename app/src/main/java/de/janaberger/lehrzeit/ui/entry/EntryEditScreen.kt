// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.EntryError
import de.janaberger.lehrzeit.data.comboKey
import de.janaberger.lehrzeit.time.daysBetween
import de.janaberger.lehrzeit.time.formatHoursMinutes
import de.janaberger.lehrzeit.time.localDateOf
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.components.BackTopAppBar
import de.janaberger.lehrzeit.ui.components.ColorDot
import de.janaberger.lehrzeit.ui.components.DiscardChangesDialog
import de.janaberger.lehrzeit.ui.components.SectionHeader
import de.janaberger.lehrzeit.ui.components.TimePickerDialog
import de.janaberger.lehrzeit.ui.components.formatDate
import de.janaberger.lehrzeit.ui.components.label
import de.janaberger.lehrzeit.ui.components.rememberNow
import de.janaberger.lehrzeit.ui.components.timeOfDay
import de.janaberger.lehrzeit.ui.components.timeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private enum class TimeField { START, END }

private val NUDGE_MINUTES = listOf(-15, -5, 5, 15)

@Composable
fun EntryEditScreen(
    onDone: () -> Unit,
    viewModel: EntryEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscard by remember { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var timePickerFor by rememberSaveable { mutableStateOf<TimeField?>(null) }
    val now by rememberNow(ticking = state.isRunning)

    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    BackHandler(enabled = state.hasChanges) { showDiscard = true }
    val requestBack = { if (state.hasChanges) showDiscard = true else onDone() }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = stringResource(if (state.isNew) R.string.entry_new_title else R.string.entry_edit_title),
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
        val zone = ZoneId.systemDefault()
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.entry_booked_on))
            state.combo?.let { combo ->
                ListItem(
                    headlineContent = { Text(combo.label()) },
                    leadingContent = { ColorDot(combo.activity.color) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null) },
                    modifier = Modifier.clickable { showPicker = true },
                )
            }

            SectionHeader(stringResource(R.string.entry_time))
            FieldRow(stringResource(R.string.entry_date)) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(formatDate(localDateOf(state.startUtc, zone), withYear = true))
                }
            }
            FieldRow(stringResource(R.string.entry_start)) {
                OutlinedButton(onClick = { timePickerFor = TimeField.START }) { Text(timeOfDay(state.startUtc)) }
            }
            NudgeRow(onNudge = viewModel::nudgeStart)
            val end = state.endUtc
            if (end == null) {
                FieldRow(stringResource(R.string.entry_end)) {
                    Text(stringResource(R.string.entry_running), color = MaterialTheme.colorScheme.primary)
                }
            } else {
                FieldRow(stringResource(R.string.entry_end)) {
                    OutlinedButton(onClick = { timePickerFor = TimeField.END }) { Text(timeOfDay(end)) }
                    val days = daysBetween(state.startUtc, end, zone).toInt()
                    if (days > 0) {
                        Text(
                            pluralStringResource(R.plurals.days_later, days, days),
                            modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                NudgeRow(onNudge = viewModel::nudgeEnd)
            }
            val message = when {
                state.isStale -> stringResource(R.string.error_entry_stale)
                state.error == EntryError.IN_FUTURE -> stringResource(R.string.error_in_future)
                state.error == EntryError.END_NOT_AFTER_START -> stringResource(R.string.error_end_not_after_start)
                else -> null
            }
            Text(
                message ?: stringResource(R.string.entry_duration, formatHoursMinutes((end ?: now) - state.startUtc)),
                color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            SectionHeader(stringResource(R.string.entry_note))
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (!state.isNew) {
                OutlinedButton(
                    onClick = viewModel::delete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }

    if (showPicker) {
        state.snapshot?.let { snapshot ->
            PickerSheet(
                snapshot = snapshot,
                selected = state.combo?.key,
                onSelect = { key ->
                    showPicker = false
                    viewModel.onComboSelected(key)
                },
                onDismiss = { showPicker = false },
            )
        }
    }
    if (showDatePicker) {
        EntryDatePickerDialog(
            date = localDateOf(state.startUtc, ZoneId.systemDefault()),
            onConfirm = {
                showDatePicker = false
                viewModel.onDateSelected(it)
            },
            onDismiss = { showDatePicker = false },
        )
    }
    timePickerFor?.let { field ->
        val initial = if (field == TimeField.START) state.startUtc else state.endUtc ?: state.startUtc
        val time = Instant.ofEpochMilli(initial).atZone(ZoneId.systemDefault())
        TimePickerDialog(
            initialHour = time.hour,
            initialMinute = time.minute,
            onConfirm = { hour, minute ->
                timePickerFor = null
                if (field == TimeField.START) viewModel.onStartTimeSelected(hour, minute) else viewModel.onEndTimeSelected(hour, minute)
            },
            onDismiss = { timePickerFor = null },
        )
    }
    state.pendingChanges?.let { changes ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.overlap_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.overlap_text))
                    changes.forEach { change ->
                        val label = state.snapshot?.combo(change.original.comboKey)?.label().orEmpty()
                        val original = timeRange(change.original.startUtc, change.original.endUtc)
                        val adjusted = change.adjusted
                        Text(
                            if (adjusted == null) {
                                stringResource(R.string.overlap_deleted, label, original)
                            } else {
                                stringResource(R.string.overlap_shortened, label, original, timeRange(adjusted.startUtc, adjusted.endUtc))
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmChanges) { Text(stringResource(R.string.action_adjust)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    state.blockedBy?.let { other ->
        val label = state.snapshot?.combo(other.comboKey)?.label().orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.overlap_blocked_title)) },
            text = { Text(stringResource(R.string.overlap_blocked_text, label, timeRange(other.startUtc, other.endUtc))) },
            confirmButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.action_ok)) } },
        )
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
}

@Composable
private fun FieldRow(label: String, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(label, modifier = Modifier.width(72.dp))
        content()
    }
}

/** −15 / −5 / +5 / +15 minutes, so most corrections need no time picker. */
@Composable
private fun NudgeRow(onNudge: (minutes: Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 88.dp, end = 16.dp, bottom = 8.dp),
    ) {
        NUDGE_MINUTES.forEach { minutes ->
            FilledTonalButton(onClick = { onNudge(minutes) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(if (minutes > 0) "+$minutes" else "−${-minutes}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDatePickerDialog(date: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The date picker works with UTC midnights.
    val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(pickerState)
    }
}
