// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.worklog

import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.time.DayBounds
import de.janaberger.lehrzeit.time.dayBounds
import de.janaberger.lehrzeit.time.localDateOf
import java.time.LocalDate
import java.time.ZoneId

/** One day in the worklog; [entries] are newest first. */
data class WorklogDay(val bounds: DayBounds, val entries: List<Entry>)

/**
 * Groups entries by local day, newest day first. An entry crossing midnight appears under every
 * day it touches, so each day's total (computed per day) is complete. A running entry counts up to [now].
 */
fun groupByDay(entries: List<Entry>, zone: ZoneId, now: Long): List<WorklogDay> {
    val byDay = sortedMapOf<LocalDate, MutableList<Entry>>(Comparator.reverseOrder())
    for (entry in entries) {
        val end = entry.endUtc ?: maxOf(now, entry.startUtc)
        // The end is exclusive: an entry ending exactly at midnight doesn't touch the next day.
        val lastDay = localDateOf(maxOf(end - 1, entry.startUtc), zone)
        var day = localDateOf(entry.startUtc, zone)
        while (!day.isAfter(lastDay)) {
            byDay.getOrPut(day) { mutableListOf() } += entry
            day = day.plusDays(1)
        }
    }
    return byDay.map { (day, dayEntries) -> WorklogDay(dayBounds(day, zone), dayEntries.sortedByDescending { it.startUtc }) }
}
