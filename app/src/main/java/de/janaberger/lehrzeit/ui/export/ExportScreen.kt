// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.export

import android.content.ClipData
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.export.ExportFormat
import de.janaberger.lehrzeit.time.formatHoursMinutes
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.components.BackTopAppBar
import de.janaberger.lehrzeit.ui.components.NoteText
import de.janaberger.lehrzeit.ui.components.SectionHeader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources by rememberUpdatedState(LocalResources.current)
    val snackbarHostState = remember { SnackbarHostState() }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(state.format.mimeType)) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExportEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType(event.mimeType)
                        .putExtra(Intent.EXTRA_STREAM, event.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    // The clip data carries the read permission on to the chosen app.
                    send.clipData = ClipData.newRawUri(null, event.uri)
                    context.startActivity(Intent.createChooser(send, null))
                }
                is ExportEvent.Saved -> snackbarHostState.showSnackbar(resources.getString(R.string.export_saved, event.fileName))
                ExportEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.export_failed))
            }
        }
    }

    Scaffold(
        topBar = { BackTopAppBar(stringResource(R.string.export_title), onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val today = state.today
        if (state.isLoading || today == null) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.export_period))
            ExportRange.entries.forEach { range ->
                val (first, last) = range.days(today, state.customFirst ?: today.minusDays(6), state.customLast ?: today)
                ChoiceRow(
                    title = stringResource(
                        when (range) {
                            ExportRange.THIS_WEEK -> R.string.export_this_week
                            ExportRange.LAST_WEEK -> R.string.export_last_week
                            ExportRange.THIS_MONTH -> R.string.export_this_month
                            ExportRange.LAST_MONTH -> R.string.export_last_month
                            ExportRange.CUSTOM -> R.string.export_custom
                        },
                    ),
                    summary = if (range == ExportRange.CUSTOM && state.customFirst == null) null else dateRange(first, last, today),
                    selected = state.range == range,
                    onClick = { if (range == ExportRange.CUSTOM) showRangePicker = true else viewModel.setRange(range) },
                )
            }

            SectionHeader(stringResource(R.string.export_format))
            ExportFormat.entries.forEach { format ->
                ChoiceRow(
                    title = format.name,
                    summary = stringResource(
                        when (format) {
                            ExportFormat.CSV -> R.string.export_csv_summary
                            ExportFormat.ODS -> R.string.export_ods_summary
                            ExportFormat.PDF -> R.string.export_pdf_summary
                        },
                    ),
                    selected = state.format == format,
                    onClick = { viewModel.setFormat(format) },
                )
            }

            Text(
                if (state.entryCount == 0) {
                    stringResource(R.string.export_nothing)
                } else {
                    pluralStringResource(R.plurals.export_preview, state.entryCount, state.entryCount, formatHoursMinutes(state.totalMillis))
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            NoteText(stringResource(R.string.export_running_note))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                val enabled = state.entryCount > 0 && !state.isWorking
                OutlinedButton(onClick = viewModel::share, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.export_share))
                }
                Button(onClick = { state.fileName?.let(saveLauncher::launch) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.export_save))
                }
            }
        }
    }

    if (showRangePicker) {
        val today = state.today ?: LocalDate.now()
        RangePickerDialog(
            first = state.customFirst ?: today.minusDays(6),
            last = state.customLast ?: today,
            today = today,
            onConfirm = { first, last ->
                showRangePicker = false
                viewModel.setCustomRange(first, last)
            },
            onDismiss = { showRangePicker = false },
        )
    }
}

@Composable
private fun ChoiceRow(title: String, summary: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

/** "Oct 1 – Oct 31", with the year when it isn't this year's. */
@Composable
private fun dateRange(first: LocalDate, last: LocalDate, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val skeleton = if (first.year != today.year || last.year != today.year) "MMMdy" else "MMMd"
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    return if (first == last) formatter.format(first) else "${formatter.format(first)} – ${formatter.format(last)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(
    first: LocalDate,
    last: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The date pickers work with UTC midnights.
    fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.utcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val todayMillis = today.utcMillis()
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = first.utcMillis(),
        initialSelectedEndDateMillis = last.utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val start = pickerState.selectedStartDateMillis
            val end = pickerState.selectedEndDateMillis ?: start
            TextButton(onClick = { if (start != null && end != null) onConfirm(start.utcDate(), end.utcDate()) }, enabled = start != null) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DateRangePicker(pickerState, modifier = Modifier.weight(1f))
    }
}
