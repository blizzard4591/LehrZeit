// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.worklog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.comboKey
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.time.formatHoursMinutes
import de.janaberger.lehrzeit.time.totalWithin
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.components.ColorDot
import de.janaberger.lehrzeit.ui.components.DeletedEntryUndoEffect
import de.janaberger.lehrzeit.ui.components.NoteText
import de.janaberger.lehrzeit.ui.components.dayLabel
import de.janaberger.lehrzeit.ui.components.label
import de.janaberger.lehrzeit.ui.components.rememberNow
import de.janaberger.lehrzeit.ui.components.timeRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorklogScreen(
    onOpenEntry: (id: Long?) -> Unit,
    viewModel: WorklogViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleted by viewModel.recentlyDeleted.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    DeletedEntryUndoEffect(deleted, snackbarHostState, viewModel::undoDelete, viewModel::forgetDeleted)
    val hasRunning = state.days.any { day -> day.entries.any { it.endUtc == null } }
    val now by rememberNow(ticking = hasRunning)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_worklog)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenEntry(null) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.worklog_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val snapshot = state.snapshot
        val today = state.today
        if (state.isLoading || snapshot == null || today == null) return@Scaffold
        if (state.days.isEmpty() && !state.hasOlder) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                NoteText(stringResource(R.string.worklog_empty))
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
        ) {
            state.days.forEach { day ->
                stickyHeader(key = "day-${day.bounds.date}") {
                    DayHeader(
                        label = dayLabel(day.bounds.date, today),
                        totalMillis = totalWithin(day.entries, day.bounds.startUtc, day.bounds.endUtc, now),
                    )
                }
                items(day.entries, key = { "${day.bounds.date}-${it.id}" }) { entry ->
                    EntryRow(entry, snapshot, now, onClick = { onOpenEntry(entry.id) })
                }
            }
            if (state.hasOlder) {
                item(key = "older") {
                    TextButton(onClick = viewModel::showOlder, modifier = Modifier.padding(8.dp)) {
                        Text(stringResource(R.string.worklog_show_older))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, totalMillis: Long) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.hours_value, formatHoursMinutes(totalMillis)), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, snapshot: CatalogSnapshot, now: Long, onClick: () -> Unit) {
    val combo = snapshot.combo(entry.comboKey)
    val isRunning = entry.endUtc == null
    ListItem(
        headlineContent = { Text(combo?.label().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(timeRange(entry.startUtc, entry.endUtc))
                if (entry.note.isNotBlank()) {
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        leadingContent = { combo?.let { ColorDot(it.activity.color) } },
        trailingContent = {
            Text(
                formatHoursMinutes((entry.endUtc ?: now) - entry.startUtc),
                style = MaterialTheme.typography.titleMedium,
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
