// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.stats

import de.janaberger.lehrzeit.data.db.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StatsTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val hour = 3_600_000L

    private fun utc(dateTime: String): Long = LocalDateTime.parse(dateTime).atZone(berlin).toInstant().toEpochMilli()

    private fun entry(account: Long, activity: Long?, start: String, end: String?) = Entry(
        accountId = account, activityId = activity, startUtc = utc(start), endUtc = end?.let(::utc), createdAt = 0, updatedAt = 0,
    )

    @Test
    fun periods_weekStartsMonday_monthHasAllDays() {
        val sunday = LocalDate.parse("2026-10-25")

        val week = StatsPeriod.containing(sunday, PeriodType.WEEK)
        assertEquals(LocalDate.parse("2026-10-19"), week.start)
        assertEquals(7, week.days.size)
        assertTrue(sunday in week)
        assertFalse(LocalDate.parse("2026-10-26") in week)

        val month = StatsPeriod.containing(sunday, PeriodType.MONTH)
        assertEquals(LocalDate.parse("2026-10-01"), month.start)
        assertEquals(31, month.days.size)
        assertEquals(LocalDate.parse("2026-11-01"), month.shift(1).start)
        assertEquals(LocalDate.parse("2026-10-24"), StatsPeriod(PeriodType.DAY, sunday).shift(-1).start)
    }

    @Test
    fun computeStats_splitsAtPeriodAndDayBoundaries_andSortsByTime() {
        val week = StatsPeriod.containing(LocalDate.parse("2026-10-21"), PeriodType.WEEK) // Oct 19-25
        val entries = listOf(
            entry(1, 10, "2026-10-18T23:00", "2026-10-19T01:00"), // 1 h before the week, 1 h inside
            entry(1, 11, "2026-10-19T08:00", "2026-10-19T11:00"),
            entry(2, null, "2026-10-20T22:00", "2026-10-21T02:00"), // over midnight
            entry(2, 10, "2026-10-25T09:00", null), // running
        )

        val stats = computeStats(entries, week, berlin, now = utc("2026-10-25T10:30"))

        assertEquals(listOf(4 * hour, 2 * hour, 2 * hour, 0L, 0L, 0L, hour + hour / 2), stats.perDay.map { it.millis })
        assertEquals(9 * hour + hour / 2, stats.totalMillis)
        // Account 2 has 4 h + 1.5 h running, account 1 has 1 h + 3 h.
        assertEquals(listOf(2L, 1L), stats.byAccount.map { it.accountId })
        assertEquals(listOf(5 * hour + hour / 2, 4 * hour), stats.byAccount.map { it.millis })
        assertEquals(listOf(ActivityTotal(null, 4 * hour), ActivityTotal(10, hour + hour / 2)), stats.byAccount[0].activities)
        assertEquals(
            listOf(ActivityTotal(null, 4 * hour), ActivityTotal(11, 3 * hour), ActivityTotal(10, 2 * hour + hour / 2)),
            stats.byActivity,
        )
    }

    @Test
    fun hourScale_picksCleanSteps() {
        assertEquals(HourScale(1, 1), hourScale(0))
        assertEquals(HourScale(1, 4), hourScale(3 * hour + hour / 2))
        assertEquals(HourScale(2, 6), hourScale(5 * hour + hour / 2))
        assertEquals(HourScale(3, 9), hourScale(9 * hour))
        assertEquals(HourScale(4, 16), hourScale(13 * hour))
    }
}
