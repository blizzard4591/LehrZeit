// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.stats.PeriodType
import de.janaberger.lehrzeit.stats.StatsPeriod
import de.janaberger.lehrzeit.time.currentDayFlow
import de.janaberger.lehrzeit.time.dayBounds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

enum class BreakdownMode { ACCOUNT, ACTIVITY }

data class StatsUiState(
    val isLoading: Boolean = true,
    val period: StatsPeriod? = null,
    val today: LocalDate? = null,
    /** Entries overlapping the period; the screen sums them up with the current time. */
    val entries: List<Entry> = emptyList(),
    val snapshot: CatalogSnapshot? = null,
    val breakdown: BreakdownMode = BreakdownMode.ACCOUNT,
    /** The bar the user tapped in the week or month chart. */
    val selectedDay: LocalDate? = null,
) {
    val canGoNext: Boolean get() = period != null && today != null && period.shift(1).start <= today
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    entries: EntryRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    /** Null until the user picks a period: then it follows the current week. */
    private val chosenPeriod = MutableStateFlow<StatsPeriod?>(null)
    private val breakdown = MutableStateFlow(BreakdownMode.ACCOUNT)
    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    private val today = currentDayFlow().map { it.date }

    private val periodEntries = combine(chosenPeriod, today) { chosen, today ->
        today to (chosen ?: StatsPeriod.containing(today, PeriodType.WEEK))
    }.flatMapLatest { (today, period) ->
        val zone = ZoneId.systemDefault()
        entries.observeEntriesOverlapping(dayBounds(period.start, zone).startUtc, dayBounds(period.endExclusive, zone).startUtc)
            .map { Triple(today, period, it) }
    }

    val uiState: StateFlow<StatsUiState> = combine(
        periodEntries,
        catalog.observeSnapshot(),
        breakdown,
        selectedDay,
    ) { (today, period, list), snapshot, mode, day ->
        StatsUiState(
            isLoading = false,
            period = period,
            today = today,
            entries = list,
            snapshot = snapshot,
            breakdown = mode,
            selectedDay = day?.takeIf { it in period && period.type != PeriodType.DAY },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /** Switches between day, week and month around the selected day, today, or the current period's start. */
    fun setType(type: PeriodType) {
        val state = uiState.value
        val period = state.period ?: return
        val today = state.today ?: return
        val anchor = state.selectedDay ?: if (today in period) today else period.start
        chosenPeriod.value = StatsPeriod.containing(anchor, type)
        selectedDay.value = null
    }

    fun previous() = shift(-1)

    fun next() {
        if (uiState.value.canGoNext) shift(1)
    }

    private fun shift(steps: Long) {
        val period = uiState.value.period ?: return
        chosenPeriod.value = period.shift(steps)
        selectedDay.value = null
    }

    fun selectDay(date: LocalDate) {
        selectedDay.value = if (selectedDay.value == date) null else date
    }

    fun openDay(date: LocalDate) {
        chosenPeriod.value = StatsPeriod(PeriodType.DAY, date)
        selectedDay.value = null
    }

    fun setBreakdown(mode: BreakdownMode) {
        breakdown.value = mode
    }
}
