// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.worklog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.DeletedEntry
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.time.currentDayFlow
import de.janaberger.lehrzeit.time.dayBounds
import de.janaberger.lehrzeit.tracking.TrackingController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class WorklogUiState(
    val isLoading: Boolean = true,
    val days: List<WorklogDay> = emptyList(),
    val snapshot: CatalogSnapshot? = null,
    val today: LocalDate? = null,
    /** There are entries before the loaded window. */
    val hasOlder: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorklogViewModel(
    private val entries: EntryRepository,
    catalog: CatalogRepository,
    private val controller: TrackingController,
) : ViewModel() {
    /** The worklog loads this many days back; "Show older" extends it. */
    private val windowDays = MutableStateFlow(INITIAL_WINDOW_DAYS)

    private val windowEntries = combine(currentDayFlow(), windowDays) { today, days ->
        today to dayBounds(today.date.minusDays(days - 1), ZoneId.systemDefault()).startUtc
    }.flatMapLatest { (today, fromUtc) ->
        entries.observeFrom(fromUtc).map { Triple(today.date, fromUtc, it) }
    }

    val uiState: StateFlow<WorklogUiState> = combine(
        windowEntries,
        entries.observeEarliestStart(),
        catalog.observeSnapshot(),
    ) { (today, fromUtc, list), earliest, snapshot ->
        WorklogUiState(
            isLoading = false,
            days = groupByDay(list, ZoneId.systemDefault(), System.currentTimeMillis()).filter { it.bounds.endUtc > fromUtc },
            snapshot = snapshot,
            today = today,
            hasOlder = earliest != null && earliest < fromUtc,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorklogUiState())

    val recentlyDeleted: StateFlow<DeletedEntry?> = entries.recentlyDeleted

    fun showOlder() {
        windowDays.update { it + OLDER_STEP_DAYS }
    }

    fun undoDelete(deleted: DeletedEntry) {
        viewModelScope.launch {
            entries.restore(deleted)
            controller.refreshNotification()
        }
    }

    fun forgetDeleted(deleted: DeletedEntry) = entries.forgetDeleted(deleted)

    private companion object {
        const val INITIAL_WINDOW_DAYS = 60L
        const val OLDER_STEP_DAYS = 90L
    }
}
