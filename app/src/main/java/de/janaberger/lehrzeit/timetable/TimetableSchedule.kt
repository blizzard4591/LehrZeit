// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.EnumSet

/**
 * When the timetable is downloaded on its own: at each of [times] on each of [days], in the phone's
 * time zone. Without times or days, it's only downloaded when "Update" is tapped.
 */
data class TimetableSchedule(
    val times: List<LocalTime> = DEFAULT_TIMES,
    val days: Set<DayOfWeek> = DEFAULT_DAYS,
) {
    val isOff: Boolean get() = times.isEmpty() || days.isEmpty()

    /**
     * The first update time after [now], or null if there is none. A time that doesn't exist on the
     * day the clocks go forward is moved on by the gap, e.g. from 02:30 to 03:30.
     */
    fun nextAfter(now: Instant, zone: ZoneId): Instant? {
        if (isOff) return null
        val today = now.atZone(zone).toLocalDate()
        val sorted = times.sorted()
        // Today and the next seven days contain every weekday, including today's later in the week.
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek !in days) continue
            sorted.map { ZonedDateTime.of(date, it, zone).toInstant() }.firstOrNull { it > now }?.let { return it }
        }
        return null
    }

    /** "07:00", as stored in the settings and the backup. */
    fun timeTexts(): List<String> = times.sorted().map(LocalTime::toString)

    /** ISO day numbers, Monday = 1. */
    fun dayNumbers(): List<Int> = days.map(DayOfWeek::getValue).sorted()

    companion object {
        /**
         * Before school; after teachers have called in sick (until about 07:40); once cancellations
         * and substitutions are planned; and after school, for the day's last changes.
         */
        val DEFAULT_TIMES: List<LocalTime> =
            listOf(LocalTime.of(7, 0), LocalTime.of(7, 55), LocalTime.of(8, 30), LocalTime.of(17, 0))

        val DEFAULT_DAYS: Set<DayOfWeek> = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)

        /** The reverse of [timeTexts] and [dayNumbers]; entries that don't parse are left out. */
        fun of(timeTexts: List<String>, dayNumbers: List<Int>) = TimetableSchedule(
            times = timeTexts.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }.distinct().sorted(),
            days = dayNumbers.filter { it in 1..7 }.map(DayOfWeek::of).toSet(),
        )
    }
}
