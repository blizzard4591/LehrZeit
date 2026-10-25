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
     * Starts [key] at [now]. A running entry for another combination ends at the same instant.
     * Returns false if [key] is already running.
     */
    suspend fun start(key: ComboKey, now: Long): Boolean = db.withTransaction {
        val running = entries.getRunning()
        if (running != null) {
            if (running.comboKey == key) return@withTransaction false
            finish(running, now)
        }
        entries.insert(
            Entry(accountId = key.accountId, activityId = key.activityId, startUtc = now, endUtc = null, createdAt = now, updatedAt = now),
        )
        true
    }

    /** Ends the running entry at [now]. Returns the finished entry, or null if nothing was running. */
    suspend fun stop(now: Long): Entry? = db.withTransaction {
        entries.getRunning()?.let { finish(it, now) }
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

    /** An entry stopped within the same millisecond it started would be empty, so it is removed. */
    private suspend fun finish(entry: Entry, now: Long): Entry {
        if (now <= entry.startUtc) {
            entries.delete(entry)
            return entry.copy(endUtc = entry.startUtc)
        }
        val finished = entry.copy(endUtc = now, updatedAt = now)
        entries.update(finished)
        return finished
    }
}
