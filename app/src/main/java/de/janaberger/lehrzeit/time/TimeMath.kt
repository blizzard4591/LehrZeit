// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.time

import de.janaberger.lehrzeit.data.db.Entry
import java.time.LocalDate
import java.time.ZoneId

/** A local calendar day as a UTC range [startUtc, endUtc). Not always 24 h: DST days have 23 or 25. */
data class DayBounds(val date: LocalDate, val startUtc: Long, val endUtc: Long)

fun dayBounds(date: LocalDate, zone: ZoneId): DayBounds = DayBounds(
    date = date,
    startUtc = date.atStartOfDay(zone).toInstant().toEpochMilli(),
    endUtc = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

/** Milliseconds of [start, end) that fall within [from, to). */
fun overlapMillis(start: Long, end: Long, from: Long, to: Long): Long =
    (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0)

/**
 * Time of [entries] within [from, to). Entries crossing a boundary only count with the part
 * inside, so an entry over midnight is split between both days. Running entries count up to [now].
 */
fun totalWithin(entries: List<Entry>, from: Long, to: Long, now: Long): Long =
    entries.sumOf { overlapMillis(it.startUtc, it.endUtc ?: now, from, to) }
