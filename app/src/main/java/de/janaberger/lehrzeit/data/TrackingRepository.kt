// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import androidx.room.withTransaction
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.db.EntryWithCombo
import de.janaberger.lehrzeit.data.db.Favorite
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Running and finished entries, plus the pinned favorites.
 * At most one entry runs at a time; it is the one without an end.
 */
class TrackingRepository(private val db: LehrZeitDatabase) {
    private val entries = db.entryDao()
    private val favorites = db.favoriteDao()

    fun observeRunning(): Flow<EntryWithCombo?> = entries.observeRunningWithCombo()

    suspend fun getRunning(): EntryWithCombo? = entries.getRunningWithCombo()

    fun observeEntriesOverlapping(fromUtc: Long, toUtc: Long): Flow<List<Entry>> =
        entries.observeOverlapping(fromUtc, toUtc)

    fun observeMostUsed(sinceUtc: Long): Flow<List<ComboKey>> = entries.observeMostUsed(sinceUtc)

    fun observeFavorites(): Flow<List<ComboKey>> =
        favorites.observeAll().map { list -> list.map { ComboKey(it.accountId, it.activityId) } }

    suspend fun toggleFavorite(key: ComboKey) {
        db.withTransaction {
            if (favorites.count(key.accountId, key.activityId) > 0) {
                favorites.delete(key.accountId, key.activityId)
            } else {
                favorites.insert(Favorite(accountId = key.accountId, activityId = key.activityId, sortOrder = favorites.maxSortOrder() + 1))
            }
        }
    }

    /**
     * Starts [key] at [now]. A running entry for another combination ends at the same instant
     * (and is discarded if shorter than [minDurationMillis]). Nothing happens if [key] is already running.
     */
    suspend fun start(key: ComboKey, now: Long, minDurationMillis: Long = 0): StartResult = db.withTransaction {
        val running = entries.getRunning()
        if (running != null && running.comboKey == key) return@withTransaction StartResult(started = false, previous = null)
        val previous = running?.let { finish(it, now, minDurationMillis) }
        entries.insert(
            Entry(accountId = key.accountId, activityId = key.activityId, startUtc = now, endUtc = null, createdAt = now, updatedAt = now),
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

    /**
     * Moves the running entry's start by [deltaMillis], but not before the previous entry's end
     * and not after [now]. Returns false if nothing changed.
     */
    suspend fun adjustRunningStart(deltaMillis: Long, now: Long): Boolean = db.withTransaction {
        val running = entries.getRunning() ?: return@withTransaction false
        val earliest = entries.latestEndAtOrBefore(running.startUtc) ?: Long.MIN_VALUE
        val newStart = (running.startUtc + deltaMillis).coerceIn(earliest, maxOf(earliest, now))
        if (newStart == running.startUtc) return@withTransaction false
        entries.update(running.copy(startUtc = newStart, updatedAt = now))
        true
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
