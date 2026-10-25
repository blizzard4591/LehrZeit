// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.time

import de.janaberger.lehrzeit.data.db.Entry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimeMathTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val hour = 3_600_000L

    private fun utc(dateTime: String): Long = LocalDateTime.parse(dateTime).atZone(berlin).toInstant().toEpochMilli()

    private fun entry(start: String, end: String?) = Entry(
        accountId = 1, activityId = null, startUtc = utc(start), endUtc = end?.let(::utc), createdAt = 0, updatedAt = 0,
    )

    @Test
    fun overlap_clipsToRangeAndNeverGoesNegative() {
        assertEquals(5L, overlapMillis(start = 10, end = 20, from = 15, to = 30))
        assertEquals(10L, overlapMillis(start = 10, end = 20, from = 0, to = 30))
        assertEquals(0L, overlapMillis(start = 10, end = 20, from = 25, to = 30))
    }

    @Test
    fun entryOverMidnight_isSplitBetweenTheTwoDays() {
        val entries = listOf(entry("2026-10-20T22:00", "2026-10-21T01:30"))
        val day1 = dayBounds(LocalDate.parse("2026-10-20"), berlin)
        val day2 = dayBounds(LocalDate.parse("2026-10-21"), berlin)

        assertEquals(2 * hour, totalWithin(entries, day1.startUtc, day1.endUtc, now = 0))
        assertEquals(hour + hour / 2, totalWithin(entries, day2.startUtc, day2.endUtc, now = 0))
    }

    @Test
    fun runningEntry_countsUpToNow() {
        val entries = listOf(entry("2026-10-20T08:00", "2026-10-20T09:00"), entry("2026-10-20T14:00", null))
        val day = dayBounds(LocalDate.parse("2026-10-20"), berlin)

        assertEquals(3 * hour, totalWithin(entries, day.startUtc, day.endUtc, now = utc("2026-10-20T16:00")))
    }

    @Test
    fun dayBounds_followDaylightSavingTime() {
        // 2026-10-25: clocks go back one hour in Germany, so the day has 25 hours.
        val day = dayBounds(LocalDate.parse("2026-10-25"), berlin)
        assertEquals(25 * hour, day.endUtc - day.startUtc)
    }

    @Test
    fun formats() {
        assertEquals("1:02:03", formatElapsed(hour + 2 * 60_000 + 3_000))
        assertEquals("0:00:00", formatElapsed(-5))
        assertEquals("12:05", formatHoursMinutes(12 * hour + 5 * 60_000 + 59_000))
    }
}
