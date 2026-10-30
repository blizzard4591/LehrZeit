// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.DeletedEntry
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.data.ShortEntrySettings
import de.janaberger.lehrzeit.data.StartNudge
import de.janaberger.lehrzeit.data.comboKey
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.db.WorkActivity
import de.janaberger.lehrzeit.time.DayBounds
import de.janaberger.lehrzeit.time.currentDayFlow
import de.janaberger.lehrzeit.tracking.PausedTimer
import de.janaberger.lehrzeit.tracking.RunningTimer
import de.janaberger.lehrzeit.tracking.TrackingController
import de.janaberger.lehrzeit.tracking.observeTrackingOverview
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
import kotlinx.coroutines.launch

data class TrackUiState(
    val isLoading: Boolean = true,
    val running: RunningTimer? = null,
    val paused: PausedTimer? = null,
    val tiles: List<WorkActivity> = emptyList(),
    val snapshot: CatalogSnapshot? = null,
    /** Today's entries; the screen sums them up with the current time so the total ticks. */
    val today: DayBounds? = null,
    val todayEntries: List<Entry> = emptyList(),
    /** The entry that ended last before the running one started, which limits moving its start. */
    val beforeRunning: Entry? = null,
    /** Waiting for the user to confirm switching from the running combination to this one. */
    val pendingSwitch: Combo? = null,
)

/** Moving the running entry's start shortened [combo]'s entry, so it now ends at [newEnd]. */
data class ShortenedNotice(val nudge: StartNudge, val combo: Combo, val newEnd: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class TrackViewModel(
    private val entries: EntryRepository,
    catalog: CatalogRepository,
    preferences: AppPreferences,
    private val controller: TrackingController,
) : ViewModel() {
    private val pendingSwitch = MutableStateFlow<ComboKey?>(null)

    private val shortEntries = preferences.shortEntries.stateIn(viewModelScope, SharingStarted.Eagerly, ShortEntrySettings())

    private val discardedChannel = Channel<Int>(Channel.BUFFERED)

    /** Emits the minimum length in seconds each time an entry was discarded for being shorter. */
    val discardedNotices: Flow<Int> = discardedChannel.receiveAsFlow()

    private val todayEntries = currentDayFlow().flatMapLatest { bounds ->
        entries.observeEntriesOverlapping(bounds.startUtc, bounds.endUtc).map { bounds to it }
    }

    private val shortenedChannel = Channel<ShortenedNotice>(Channel.BUFFERED)

    /** Emits each time moving the start shortened the previous entry, to offer undo. */
    val shortenedNotices: Flow<ShortenedNotice> = shortenedChannel.receiveAsFlow()

    val uiState: StateFlow<TrackUiState> = combine(
        observeTrackingOverview(catalog, entries, preferences),
        todayEntries,
        entries.observeBeforeRunning(),
        pendingSwitch,
    ) { overview, (today, todayEntries), beforeRunning, pending ->
        TrackUiState(
            isLoading = false,
            running = overview.running,
            paused = overview.paused,
            tiles = overview.tiles,
            snapshot = overview.snapshot,
            today = today,
            todayEntries = todayEntries,
            beforeRunning = beforeRunning,
            pendingSwitch = pending?.let(overview.snapshot::combo),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackUiState())

    val recentlyDeleted: StateFlow<DeletedEntry?> = entries.recentlyDeleted

    fun undoDelete(deleted: DeletedEntry) {
        viewModelScope.launch {
            entries.restore(deleted)
            controller.refreshNotification()
        }
    }

    fun forgetDeleted(deleted: DeletedEntry) = entries.forgetDeleted(deleted)

    /** A tile without classes was tapped. Switching away from a running combination asks first. */
    fun onSelect(key: ComboKey) {
        val running = uiState.value.running
        when {
            running == null -> viewModelScope.launch { notifyIfDiscarded(controller.start(key)) }
            running.combo.key == key -> Unit
            else -> pendingSwitch.value = key
        }
    }

    /** A row in the class sheet was picked: that was the second tap, so a running entry is switched without asking. */
    fun onClassChosen(key: ComboKey) {
        if (uiState.value.running?.combo?.key == key) return
        viewModelScope.launch { notifyIfDiscarded(controller.start(key)) }
    }

    fun confirmSwitch() {
        val key = pendingSwitch.value ?: return
        pendingSwitch.value = null
        viewModelScope.launch { notifyIfDiscarded(controller.start(key)) }
    }

    fun cancelSwitch() {
        pendingSwitch.value = null
    }

    fun stop() {
        viewModelScope.launch { notifyIfDiscarded(controller.stop()) }
    }

    fun pause() {
        viewModelScope.launch { notifyIfDiscarded(controller.pause()) }
    }

    fun resume() {
        viewModelScope.launch { controller.resume() }
    }

    fun dismissPaused() {
        viewModelScope.launch { controller.dismissPaused() }
    }

    fun nudgeStart(minutes: Int) {
        viewModelScope.launch {
            val nudge = controller.nudgeRunningStart(minutes * 60_000L) ?: return@launch
            val shortened = nudge.previous?.changed ?: return@launch
            val combo = uiState.value.snapshot?.combo(shortened.comboKey) ?: return@launch
            shortenedChannel.trySend(ShortenedNotice(nudge, combo, shortened.endUtc!!))
        }
    }

    fun undoNudge(nudge: StartNudge) {
        viewModelScope.launch { controller.undoNudge(nudge) }
    }

    fun setNote(entryId: Long, note: String) {
        viewModelScope.launch { entries.setNote(entryId, note, System.currentTimeMillis()) }
    }

    private fun notifyIfDiscarded(discarded: Boolean) {
        if (discarded) discardedChannel.trySend(shortEntries.value.minSeconds)
    }

    /** Called once notifications were allowed, so a timer started before that gets its notification. */
    fun refreshNotification() {
        viewModelScope.launch { controller.refreshNotification() }
    }
}
