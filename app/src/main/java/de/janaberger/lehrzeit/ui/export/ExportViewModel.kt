// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.export.ExportFormat
import de.janaberger.lehrzeit.export.ExportWriter
import de.janaberger.lehrzeit.export.exportFileName
import de.janaberger.lehrzeit.stats.PeriodType
import de.janaberger.lehrzeit.stats.StatsPeriod
import de.janaberger.lehrzeit.time.currentDayFlow
import de.janaberger.lehrzeit.time.dayBounds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class ExportRange { THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, CUSTOM }

/** The days [first]..[last] that [range] covers as of [today]. */
fun ExportRange.days(today: LocalDate, customFirst: LocalDate, customLast: LocalDate): Pair<LocalDate, LocalDate> {
    val period = when (this) {
        ExportRange.THIS_WEEK -> StatsPeriod.containing(today, PeriodType.WEEK)
        ExportRange.LAST_WEEK -> StatsPeriod.containing(today, PeriodType.WEEK).shift(-1)
        ExportRange.THIS_MONTH -> StatsPeriod.containing(today, PeriodType.MONTH)
        ExportRange.LAST_MONTH -> StatsPeriod.containing(today, PeriodType.MONTH).shift(-1)
        ExportRange.CUSTOM -> return customFirst to customLast
    }
    return period.start to period.endExclusive.minusDays(1)
}

private data class ExportSelection(
    val range: ExportRange = ExportRange.THIS_MONTH,
    val format: ExportFormat = ExportFormat.CSV,
    /** Null until the user picks a custom range; then the last seven days. */
    val customFirst: LocalDate? = null,
    val customLast: LocalDate? = null,
)

data class ExportUiState(
    val isLoading: Boolean = true,
    val today: LocalDate? = null,
    val range: ExportRange = ExportRange.THIS_MONTH,
    val format: ExportFormat = ExportFormat.CSV,
    val customFirst: LocalDate? = null,
    val customLast: LocalDate? = null,
    val firstDay: LocalDate? = null,
    val lastDay: LocalDate? = null,
    val entryCount: Int = 0,
    val totalMillis: Long = 0,
    val isWorking: Boolean = false,
) {
    val fileName: String? get() = if (firstDay != null && lastDay != null) exportFileName(firstDay, lastDay, format) else null
}

sealed interface ExportEvent {
    data class Share(val uri: Uri, val mimeType: String) : ExportEvent
    data class Saved(val fileName: String) : ExportEvent
    data object Failed : ExportEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModel(
    entries: EntryRepository,
    private val writer: ExportWriter,
) : ViewModel() {
    private val selection = MutableStateFlow(ExportSelection())
    private val isWorking = MutableStateFlow(false)
    private val eventChannel = Channel<ExportEvent>(Channel.BUFFERED)
    val events: Flow<ExportEvent> = eventChannel.receiveAsFlow()

    private val preview = combine(currentDayFlow(), selection) { today, selection ->
        val customFirst = selection.customFirst ?: today.date.minusDays(6)
        val customLast = selection.customLast ?: today.date
        Triple(today.date, selection, selection.range.days(today.date, customFirst, customLast))
    }.flatMapLatest { (today, selection, days) ->
        val zone = ZoneId.systemDefault()
        entries.observeFinishedStartingIn(dayBounds(days.first, zone).startUtc, dayBounds(days.second, zone).endUtc)
            .map { list ->
                ExportUiState(
                    isLoading = false,
                    today = today,
                    range = selection.range,
                    format = selection.format,
                    customFirst = selection.customFirst,
                    customLast = selection.customLast,
                    firstDay = days.first,
                    lastDay = days.second,
                    entryCount = list.size,
                    totalMillis = list.sumOf { it.endUtc!! - it.startUtc },
                )
            }
    }

    val uiState: StateFlow<ExportUiState> = combine(preview, isWorking) { state, working -> state.copy(isWorking = working) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportUiState())

    fun setRange(range: ExportRange) {
        selection.update { it.copy(range = range) }
    }

    fun setCustomRange(first: LocalDate, last: LocalDate) {
        selection.update { it.copy(range = ExportRange.CUSTOM, customFirst = first, customLast = last) }
    }

    fun setFormat(format: ExportFormat) {
        selection.update { it.copy(format = format) }
    }

    fun saveTo(uri: Uri) = run { state ->
        writer.saveTo(uri, state.format, state.firstDay!!, state.lastDay!!)
        ExportEvent.Saved(state.fileName.orEmpty())
    }

    fun share() = run { state ->
        ExportEvent.Share(writer.writeForSharing(state.format, state.firstDay!!, state.lastDay!!), state.format.mimeType)
    }

    private fun run(block: suspend (ExportUiState) -> ExportEvent) {
        val state = uiState.value
        if (state.isLoading || state.isWorking || state.firstDay == null) return
        isWorking.value = true
        viewModelScope.launch {
            val event = try {
                block(state)
            } catch (e: Exception) {
                ExportEvent.Failed
            } finally {
                isWorking.value = false
            }
            eventChannel.send(event)
        }
    }
}
