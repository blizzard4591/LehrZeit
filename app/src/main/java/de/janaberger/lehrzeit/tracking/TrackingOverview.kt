// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.EntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Duration.Companion.days

data class RunningTimer(val entryId: Long, val combo: Combo, val startUtc: Long)

data class PausedTimer(val combo: Combo, val pausedAt: Long)

/** The timer's state and the tiles, as the Track screen and the widget show them. */
data class TrackingOverview(
    val snapshot: CatalogSnapshot,
    val favorites: List<ComboKey>,
    val running: RunningTimer?,
    /** Only set while nothing runs. */
    val paused: PausedTimer?,
    val tiles: List<Combo>,
)

/** "Most used" counts the entries of the last 30 days, counted from the moment this is called. */
fun observeTrackingOverview(
    catalog: CatalogRepository,
    entries: EntryRepository,
    preferences: AppPreferences,
    now: Long = System.currentTimeMillis(),
): Flow<TrackingOverview> = combine(
    catalog.observeSnapshot(),
    entries.observeFavorites(),
    entries.observeMostUsed(sinceUtc = now - 30.days.inWholeMilliseconds),
    entries.observeRunning(),
    preferences.paused,
) { snapshot, favorites, mostUsed, running, paused ->
    TrackingOverview(
        snapshot = snapshot,
        favorites = favorites,
        running = running?.let { RunningTimer(it.entry.id, Combo(it.account, it.activity), it.entry.startUtc) },
        paused = paused?.takeIf { running == null }?.let { p -> snapshot.combo(p.key)?.let { PausedTimer(it, p.pausedAt) } },
        tiles = buildTiles(snapshot, favorites, mostUsed),
    )
}
