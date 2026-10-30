// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import androidx.room.withTransaction
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.db.EntryWithCombo
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Running and finished entries.
 * At most one entry runs at a time; it is the one without an end.
 * Changes to a running entry must be followed by [de.janaberger.lehrzeit.tracking.TrackingController.refreshNotification].
 */
class EntryRepository(private val db: LehrZeitDatabase) {
    private val entries = db.entryDao()

    fun observeRunning(): Flow<EntryWithCombo?> = entries.observeRunningWithCombo()

    suspend fun getRunning(): EntryWithCombo? = entries.getRunningWithCombo()

    fun observeEntriesOverlapping(fromUtc: Long, toUtc: Long): Flow<List<Entry>> =
        entries.observeOverlapping(fromUtc, toUtc)

    /**
     * Starts [key] at [now]. A running entry for another combination ends at the same instant
     * (and is discarded if shorter than [minDurationMillis]). Nothing happens if [key] is already running.
     */
    suspend fun start(key: ComboKey, now: Long, minDurationMillis: Long = 0): StartResult = db.withTransaction {
        val running = entries.getRunning()
        if (running != null && running.comboKey == key) return@withTransaction StartResult(started = false, previous = null)
        val previous = running?.let { finish(it, now, minDurationMillis) }
        entries.insert(
            Entry(activityId = key.activityId, classId = key.classId, startUtc = now, endUtc = null, createdAt = now, updatedAt = now),
        )
        StartResult(started = true, previous = previous)
    }

    /**
     * Ends the running entry at [now], discarding it if shorter than [minDurationMillis].
     * Returns null if nothing was running.
     */
    suspend fun stop(now: Long, minDurationMillis: Long = 0): Finished? = db.withTransaction {
        entries.getRunning()?.let { finish(it, now, minDurationMillis) }
    }

    /** The entry that ended last before the running one started; null while nothing runs. */
    fun observeBeforeRunning(): Flow<Entry?> = entries.observeBeforeRunning()

    /**
     * Moves the running entry's start by [deltaMillis], shortening the previous entry if it's in
     * the way. Returns null, changing nothing, if the full step doesn't fit (see [nudgedStart]).
     */
    suspend fun nudgeRunningStart(deltaMillis: Long, now: Long): StartNudge? = db.withTransaction {
        val running = entries.getRunning() ?: return@withTransaction null
        val previous = entries.getLatestEndingAtOrBefore(running.startUtc)
        val start = nudgedStart(running.startUtc, previous, deltaMillis, now) ?: return@withTransaction null
        val shortened = previous?.takeIf { it.endUtc!! > start }?.let { EntryChange(it, it.copy(endUtc = start, updatedAt = now)) }
        val moved = EntryChange(running, running.copy(startUtc = start, updatedAt = now))
        shortened?.let { entries.update(it.changed) }
        entries.update(moved.changed)
        StartNudge(moved, shortened)
    }

    /** Undo for [nudgeRunningStart]. Returns false, changing nothing, if either entry was changed since. */
    suspend fun undoNudge(nudge: StartNudge, now: Long): Boolean = db.withTransaction {
        val originalStart = nudge.running.original.startUtc
        val running = entries.get(nudge.running.changed.id)
            ?.takeIf { it.startUtc == nudge.running.changed.startUtc && (it.endUtc == null || it.endUtc > originalStart) }
            ?: return@withTransaction false
        val shortened = nudge.previous
        val previous = shortened?.let { change ->
            entries.get(change.changed.id)?.takeIf { it.endUtc == change.changed.endUtc } ?: return@withTransaction false
        }
        entries.update(running.copy(startUtc = originalStart, updatedAt = now))
        if (previous != null && shortened != null) entries.update(previous.copy(endUtc = shortened.original.endUtc, updatedAt = now))
        true
    }

    /** Sets the note of entry [id], running or not. */
    suspend fun setNote(id: Long, note: String, now: Long) = entries.setNote(id, note.trim().take(MAX_NOTE_LENGTH), now)

    /** Entries that end after [fromUtc] or are still running, oldest first. */
    fun observeFrom(fromUtc: Long): Flow<List<Entry>> = entries.observeOverlapping(fromUtc, Long.MAX_VALUE)

    fun observeEarliestStart(): Flow<Long?> = entries.observeEarliestStart()

    /** Finished entries that start in [fromUtc, toUtc), oldest first; what an export contains. */
    fun observeFinishedStartingIn(fromUtc: Long, toUtc: Long): Flow<List<Entry>> =
        entries.observeFinishedStartingIn(fromUtc, toUtc)

    suspend fun get(id: Long): Entry? = entries.get(id)

    /** What saving [edited] would do to the entries it overlaps. */
    suspend fun planSave(edited: Entry): OverlapPlan =
        planOverlaps(edited, entries.getOverlapping(edited.startUtc, edited.endUtc ?: Long.MAX_VALUE))

    /**
     * Inserts ([Entry.id] == 0) or updates [edited] and shortens or deletes the entries it overlaps,
     * all in one transaction. Fails with [SaveEntryResult.Stale] if the entry was deleted or stopped
     * in the meantime, so an old copy of a running entry can't restart it.
     */
    suspend fun save(edited: Entry, now: Long): SaveEntryResult = db.withTransaction {
        if (edited.id != 0L) {
            val stored = entries.get(edited.id) ?: return@withTransaction SaveEntryResult.Stale
            if (edited.endUtc == null && stored.endUtc != null) return@withTransaction SaveEntryResult.Stale
        } else {
            require(edited.endUtc != null) { "New entries are always finished" }
        }
        when (val plan = planSave(edited)) {
            is OverlapPlan.Blocked -> return@withTransaction SaveEntryResult.Blocked(plan.by)
            is OverlapPlan.Adjust -> plan.changes.forEach { change ->
                if (change.adjusted == null) entries.delete(change.original) else entries.update(change.adjusted.copy(updatedAt = now))
            }
            OverlapPlan.None -> Unit
        }
        val id = if (edited.id == 0L) {
            entries.insert(edited.copy(createdAt = now, updatedAt = now))
        } else {
            entries.update(edited.copy(updatedAt = now))
            edited.id
        }
        SaveEntryResult.Saved(id)
    }

    private val _recentlyDeleted = MutableStateFlow<DeletedEntry?>(null)

    /** The last deleted entry, until it was restored or its undo offer has been shown. */
    val recentlyDeleted: StateFlow<DeletedEntry?> = _recentlyDeleted.asStateFlow()

    suspend fun delete(id: Long, now: Long): DeletedEntry? = db.withTransaction {
        val entry = entries.get(id) ?: return@withTransaction null
        entries.delete(entry)
        DeletedEntry(entry, deletedAt = now).also { _recentlyDeleted.value = it }
    }

    /** Undo for [delete]. A running entry comes back finished if another one runs by now. */
    suspend fun restore(deleted: DeletedEntry) {
        _recentlyDeleted.compareAndSet(deleted, null)
        db.withTransaction {
            val entry = deleted.entry
            val restored = if (entry.endUtc == null && entries.getRunning() != null) entry.copy(endUtc = deleted.deletedAt) else entry
            entries.insert(restored)
        }
    }

    fun forgetDeleted(deleted: DeletedEntry) {
        _recentlyDeleted.compareAndSet(deleted, null)
    }

    /** After a restore, an earlier deletion must not be undone into the restored data. */
    fun forgetAllDeleted() {
        _recentlyDeleted.value = null
    }

    /** Too-short entries are deleted; an empty one (stopped in the same millisecond) always is. */
    private suspend fun finish(entry: Entry, now: Long, minDurationMillis: Long): Finished {
        val finished = entry.copy(endUtc = maxOf(now, entry.startUtc), updatedAt = now)
        if (finished.endUtc!! - entry.startUtc < maxOf(minDurationMillis, 1)) {
            entries.delete(entry)
            return Finished(finished, discarded = true)
        }
        entries.update(finished)
        return Finished(finished, discarded = false)
    }
}

data class Finished(val entry: Entry, val discarded: Boolean)

data class StartResult(val started: Boolean, val previous: Finished?)

data class DeletedEntry(val entry: Entry, val deletedAt: Long)

sealed interface SaveEntryResult {
    data class Saved(val id: Long) : SaveEntryResult
    data class Blocked(val by: Entry) : SaveEntryResult
    data object Stale : SaveEntryResult
}
