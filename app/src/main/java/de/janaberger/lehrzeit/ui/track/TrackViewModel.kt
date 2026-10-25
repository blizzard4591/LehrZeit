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
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.time.DayBounds
import de.janaberger.lehrzeit.time.currentDayFlow
import de.janaberger.lehrzeit.tracking.TrackingController
import de.janaberger.lehrzeit.tracking.buildTiles
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
import kotlin.time.Duration.Companion.days

data class RunningUi(val entryId: Long, val combo: Combo, val startUtc: Long)

data class PausedUi(val combo: Combo, val pausedAt: Long)

data class TrackUiState(
    val isLoading: Boolean = true,
    val running: RunningUi? = null,
    val paused: PausedUi? = null,
    val tiles: List<Combo> = emptyList(),
    val snapshot: CatalogSnapshot? = null,
    val favorites: Set<ComboKey> = emptySet(),
    /** Today's entries; the screen sums them up with the current time so the total ticks. */
    val today: DayBounds? = null,
    val todayEntries: List<Entry> = emptyList(),
    /** Waiting for the user to confirm switching from the running combination to this one. */
    val pendingSwitch: Combo? = null,
)

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

    private val mostUsed = entries.observeMostUsed(sinceUtc = System.currentTimeMillis() - 30.days.inWholeMilliseconds)

    val uiState: StateFlow<TrackUiState> = combine(
        catalog.observeSnapshot(),
        entries.observeFavorites(),
        mostUsed,
        combine(entries.observeRunning(), preferences.paused, ::Pair),
        combine(todayEntries, pendingSwitch, ::Pair),
    ) { snapshot, favorites, used, (running, paused), (todayData, pending) ->
        TrackUiState(
            isLoading = false,
            running = running?.let { RunningUi(it.entry.id, Combo(it.account, it.activity), it.entry.startUtc) },
            paused = paused?.takeIf { running == null }?.let { p -> snapshot.combo(p.key)?.let { PausedUi(it, p.pausedAt) } },
            tiles = buildTiles(snapshot, favorites, used),
            snapshot = snapshot,
            favorites = favorites.toSet(),
            today = todayData.first,
            todayEntries = todayData.second,
            pendingSwitch = pending?.let(snapshot::combo),
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

    /** A tile or picker row was tapped. Switching away from a running combination asks first. */
    fun onSelect(key: ComboKey) {
        val running = uiState.value.running
        when {
            running == null -> viewModelScope.launch { notifyIfDiscarded(controller.start(key)) }
            running.combo.key == key -> Unit
            else -> pendingSwitch.value = key
        }
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
        viewModelScope.launch { controller.adjustRunningStart(minutes * 60_000L) }
    }

    fun toggleFavorite(key: ComboKey) {
        viewModelScope.launch { entries.toggleFavorite(key) }
    }

    private fun notifyIfDiscarded(discarded: Boolean) {
        if (discarded) discardedChannel.trySend(shortEntries.value.minSeconds)
    }

    /** Called once notifications were allowed, so a timer started before that gets its notification. */
    fun refreshNotification() {
        viewModelScope.launch { controller.refreshNotification() }
    }
}
