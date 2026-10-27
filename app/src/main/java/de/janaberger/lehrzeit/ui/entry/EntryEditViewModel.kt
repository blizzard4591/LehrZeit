// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.EntryError
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.data.MAX_NOTE_LENGTH
import de.janaberger.lehrzeit.data.OverlapChange
import de.janaberger.lehrzeit.data.OverlapPlan
import de.janaberger.lehrzeit.data.SaveEntryResult
import de.janaberger.lehrzeit.data.comboKey
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.validateEntry
import de.janaberger.lehrzeit.time.firstOccurrenceAfter
import de.janaberger.lehrzeit.time.floorToMinutes
import de.janaberger.lehrzeit.time.localDateOf
import de.janaberger.lehrzeit.time.moveByDays
import de.janaberger.lehrzeit.time.withTimeOfDay
import de.janaberger.lehrzeit.tracking.TrackingController
import de.janaberger.lehrzeit.ui.navigation.EntryEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** The editable part of an entry, to tell whether anything changed. */
data class EntryFields(val key: ComboKey?, val startUtc: Long, val endUtc: Long?, val note: String)

data class EntryEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val isRunning: Boolean = false,
    val combo: Combo? = null,
    val startUtc: Long = 0,
    /** Null while the entry is running. */
    val endUtc: Long? = null,
    val note: String = "",
    val error: EntryError? = null,
    /** The entry was stopped or deleted elsewhere while this screen was open. */
    val isStale: Boolean = false,
    /** Waiting for the user to confirm these changes to overlapped entries. */
    val pendingChanges: List<OverlapChange>? = null,
    /** Saving is impossible because the entry would lie inside this one. */
    val blockedBy: Entry? = null,
    val snapshot: CatalogSnapshot? = null,
    val favorites: Set<ComboKey> = emptySet(),
    val isSaving: Boolean = false,
    /** Set once the entry was saved or deleted; the screen then closes. */
    val isFinished: Boolean = false,
    private val original: EntryFields? = null,
) {
    val hasChanges: Boolean get() = original != null && original != EntryFields(combo?.key, startUtc, endUtc, note)

    internal fun withOriginal() = copy(original = EntryFields(combo?.key, startUtc, endUtc, note))
}

class EntryEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val entries: EntryRepository,
    private val catalog: CatalogRepository,
    private val controller: TrackingController,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val entryId: Long? = savedStateHandle.toRoute<EntryEditRoute>().id
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private var loaded: Entry? = null

    private val _uiState = MutableStateFlow(EntryEditUiState())
    val uiState: StateFlow<EntryEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            combine(catalog.observeSnapshot(), entries.observeFavorites()) { snapshot, favorites -> snapshot to favorites }
                .collect { (snapshot, favorites) -> _uiState.update { it.copy(snapshot = snapshot, favorites = favorites.toSet()) } }
        }
    }

    private suspend fun load() {
        if (entryId == null) {
            // A new entry: the last hour, on General.
            val end = floorToMinutes(now(), 5)
            val general = catalog.observeSnapshot().first().general
            _uiState.update {
                it.copy(
                    isLoading = false,
                    combo = general?.let { account -> Combo(account, null) },
                    startUtc = end - 60 * 60_000L,
                    endUtc = end,
                ).withOriginal()
            }
            return
        }
        val entry = entries.get(entryId)
        if (entry == null) {
            _uiState.update { it.copy(isFinished = true) }
            return
        }
        loaded = entry
        val combo = catalog.getCombo(entry.comboKey)
        _uiState.update {
            it.copy(
                isLoading = false,
                isNew = false,
                isRunning = entry.endUtc == null,
                combo = combo,
                startUtc = entry.startUtc,
                endUtc = entry.endUtc,
                note = entry.note,
            ).withOriginal()
        }
    }

    fun onComboSelected(key: ComboKey) {
        val combo = _uiState.value.snapshot?.combo(key) ?: return
        _uiState.update { it.copy(combo = combo) }
    }

    /** Moves the entry to another day, keeping its times. */
    fun onDateSelected(date: LocalDate) = edit { state ->
        val from = localDateOf(state.startUtc, zone)
        state.copy(
            startUtc = moveByDays(state.startUtc, from, date, zone),
            endUtc = state.endUtc?.let { moveByDays(it, from, date, zone) },
        )
    }

    fun onStartTimeSelected(hour: Int, minute: Int) = edit { it.copy(startUtc = withTimeOfDay(it.startUtc, hour, minute, zone)) }

    /** The end is the first [hour]:[minute] after the start, so an end before the start means the next day. */
    fun onEndTimeSelected(hour: Int, minute: Int) = edit { it.copy(endUtc = firstOccurrenceAfter(it.startUtc, hour, minute, zone)) }

    fun nudgeStart(minutes: Int) = edit { it.copy(startUtc = it.startUtc + minutes * 60_000L) }

    fun nudgeEnd(minutes: Int) = edit { state -> state.copy(endUtc = state.endUtc?.plus(minutes * 60_000L)) }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note.take(MAX_NOTE_LENGTH)) }
    }

    private fun edit(transform: (EntryEditUiState) -> EntryEditUiState) {
        _uiState.update { transform(it).copy(error = null) }
    }

    /** Validates, then saves directly or first asks how to handle overlapped entries. */
    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        val error = validateEntry(state.startUtc, state.endUtc, now())
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        val edited = editedEntry(state) ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val plan = entries.planSave(edited)) {
                OverlapPlan.None -> persist(edited)
                is OverlapPlan.Adjust -> _uiState.update { it.copy(isSaving = false, pendingChanges = plan.changes) }
                is OverlapPlan.Blocked -> _uiState.update { it.copy(isSaving = false, blockedBy = plan.by) }
            }
        }
    }

    fun confirmChanges() {
        val edited = editedEntry(_uiState.value) ?: return
        _uiState.update { it.copy(pendingChanges = null, isSaving = true) }
        viewModelScope.launch { persist(edited) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(pendingChanges = null, blockedBy = null) }
    }

    fun delete() {
        val id = entryId ?: return
        viewModelScope.launch {
            entries.delete(id, now())
            controller.refreshNotification()
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    private suspend fun persist(edited: Entry) {
        when (val result = entries.save(edited, now())) {
            is SaveEntryResult.Saved -> {
                controller.refreshNotification()
                _uiState.update { it.copy(isSaving = false, isFinished = true) }
            }
            is SaveEntryResult.Blocked -> _uiState.update { it.copy(isSaving = false, blockedBy = result.by) }
            SaveEntryResult.Stale -> _uiState.update { it.copy(isSaving = false, isStale = true) }
        }
    }

    private fun editedEntry(state: EntryEditUiState): Entry? {
        val key = state.combo?.key ?: return null
        val base = loaded ?: Entry(accountId = key.accountId, activityId = key.activityId, startUtc = 0, endUtc = null, createdAt = 0, updatedAt = 0)
        return base.copy(
            accountId = key.accountId,
            activityId = key.activityId,
            startUtc = state.startUtc,
            endUtc = state.endUtc,
            note = state.note.trim(),
        )
    }
}
