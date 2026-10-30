// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.data.db.WorkActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class RunningTimer(val entryId: Long, val combo: Combo, val startUtc: Long, val note: String)

data class PausedTimer(val combo: Combo, val pausedAt: Long)

/** The timer's state and the tiles, as the Track screen and the widget show them. */
data class TrackingOverview(
    val snapshot: CatalogSnapshot,
    val running: RunningTimer?,
    /** Only set while nothing runs. */
    val paused: PausedTimer?,
) {
    /** One tile per non-archived activity: General first, then in the order from Manage activities. */
    val tiles: List<WorkActivity> get() = snapshot.activeActivities
}

fun observeTrackingOverview(
    catalog: CatalogRepository,
    entries: EntryRepository,
    preferences: AppPreferences,
): Flow<TrackingOverview> = combine(
    catalog.observeSnapshot(),
    entries.observeRunning(),
    preferences.paused,
) { snapshot, running, paused ->
    TrackingOverview(
        snapshot = snapshot,
        running = running?.let { RunningTimer(it.entry.id, Combo(it.activity, it.schoolClass), it.entry.startUtc, it.entry.note) },
        paused = paused?.takeIf { running == null }?.let { p -> snapshot.combo(p.key)?.let { PausedTimer(it, p.pausedAt) } },
    )
}
