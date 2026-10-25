// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.time

import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.ui.worklog.groupByDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EditTimeTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun utc(dateTime: String): Long = LocalDateTime.parse(dateTime).atZone(berlin).toInstant().toEpochMilli()

    private fun entry(id: Long, start: String, end: String?) = Entry(
        id = id, accountId = 1, activityId = null, startUtc = utc(start), endUtc = end?.let(::utc), createdAt = 0, updatedAt = 0,
    )

    @Test
    fun withTimeOfDay_keepsTheDay() {
        assertEquals(utc("2026-10-20T08:15"), withTimeOfDay(utc("2026-10-20T14:37:12"), 8, 15, berlin))
    }

    @Test
    fun firstOccurrenceAfter_rollsOverMidnight() {
        assertEquals(utc("2026-10-20T16:00"), firstOccurrenceAfter(utc("2026-10-20T14:00"), 16, 0, berlin))
        assertEquals(utc("2026-10-21T01:30"), firstOccurrenceAfter(utc("2026-10-20T22:00"), 1, 30, berlin))
        assertEquals(utc("2026-10-21T14:00"), firstOccurrenceAfter(utc("2026-10-20T14:00"), 14, 0, berlin))
    }

    @Test
    fun moveByDays_keepsLocalTimeAcrossDaylightSavingTime() {
        // 2026-10-25 has 25 hours in Germany; the local time must stay 09:00 anyway.
        val moved = moveByDays(utc("2026-10-24T09:00"), LocalDate.parse("2026-10-24"), LocalDate.parse("2026-10-26"), berlin)
        assertEquals(utc("2026-10-26T09:00"), moved)
    }

    @Test
    fun floorToMinutes_roundsDown() {
        assertEquals(utc("2026-10-20T14:35"), floorToMinutes(utc("2026-10-20T14:39:59"), 5))
    }

    @Test
    fun groupByDay_newestFirst_andEntriesOverMidnightInBothDays() {
        val morning = entry(1, "2026-10-20T08:00", "2026-10-20T09:00")
        val night = entry(2, "2026-10-20T22:00", "2026-10-21T01:00")
        val endsAtMidnight = entry(3, "2026-10-21T23:00", "2026-10-22T00:00")
        val running = entry(4, "2026-10-22T10:00", null)

        val days = groupByDay(listOf(morning, night, endsAtMidnight, running), berlin, now = utc("2026-10-22T11:00"))

        assertEquals(listOf("2026-10-22", "2026-10-21", "2026-10-20"), days.map { it.bounds.date.toString() })
        assertEquals(listOf(running), days[0].entries)
        assertEquals(listOf(endsAtMidnight, night), days[1].entries)
        assertEquals(listOf(night, morning), days[2].entries)
    }
}
