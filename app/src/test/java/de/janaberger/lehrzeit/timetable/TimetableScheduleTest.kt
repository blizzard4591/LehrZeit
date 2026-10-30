// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TimetableScheduleTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val defaults = TimetableSchedule()

    private fun at(dateTime: String): Instant = LocalDateTime.parse(dateTime).atZone(berlin).toInstant()

    private fun TimetableSchedule.next(dateTime: String) = nextAfter(at(dateTime), berlin)

    @Test
    fun theNextTime_isLaterTheSameDay() {
        assertEquals(at("2026-11-02T07:00"), defaults.next("2026-11-02T06:10")) // Monday
        assertEquals(at("2026-11-02T07:55"), defaults.next("2026-11-02T07:30"))
        // Right at a time, that update is due now; the next one comes after it.
        assertEquals(at("2026-11-02T08:30"), defaults.next("2026-11-02T07:55"))
        assertEquals(at("2026-11-02T17:00"), defaults.next("2026-11-02T08:31"))
    }

    @Test
    fun afterTheLastTime_itsTheNextChosenDay() {
        assertEquals(at("2026-11-03T07:00"), defaults.next("2026-11-02T17:00"))
        // Friday evening and the weekend wait for Monday.
        assertEquals(at("2026-11-09T07:00"), defaults.next("2026-11-06T17:30"))
        assertEquals(at("2026-11-09T07:00"), defaults.next("2026-11-08T12:00"))
        // With only Wednesday chosen, from Wednesday evening to the next Wednesday.
        val wednesdays = defaults.copy(days = setOf(DayOfWeek.WEDNESDAY))
        assertEquals(at("2026-11-11T07:00"), wednesdays.next("2026-11-04T18:00"))
    }

    @Test
    fun theTimesNeedNoOrder() {
        val schedule = TimetableSchedule(times = listOf(LocalTime.of(17, 0), LocalTime.of(7, 0)))
        assertEquals(at("2026-11-02T07:00"), schedule.next("2026-11-02T06:00"))
    }

    @Test
    fun withoutTimesOrDays_thereIsNoAutomaticUpdate() {
        assertTrue(TimetableSchedule(times = emptyList()).isOff)
        assertNull(TimetableSchedule(times = emptyList()).next("2026-11-02T06:00"))
        assertNull(TimetableSchedule(days = emptySet()).next("2026-11-02T06:00"))
    }

    @Test
    fun theTimesFollowTheClockAcrossSummerAndWinterTime() {
        // Winter time starts on Sunday, 25 Oct 2026: Monday's 07:00 is 06:00 UTC, no longer 05:00.
        assertEquals(Instant.parse("2026-10-26T06:00:00Z"), defaults.next("2026-10-23T17:30"))
        // On 28 Mar 2027 there is no 02:30; the update moves to 03:30 summer time.
        val night = TimetableSchedule(times = listOf(LocalTime.of(2, 30)), days = DayOfWeek.entries.toSet())
        assertEquals(Instant.parse("2027-03-28T01:30:00Z"), night.next("2027-03-28T00:00"))
    }

    @Test
    fun storedTexts_roundTrip_andBrokenEntriesAreSkipped() {
        assertEquals(defaults, TimetableSchedule.of(defaults.timeTexts(), defaults.dayNumbers()))
        assertEquals(listOf("07:00", "07:55", "08:30", "17:00"), defaults.timeTexts())
        assertEquals(listOf(1, 2, 3, 4, 5), defaults.dayNumbers())
        assertEquals(
            TimetableSchedule(times = listOf(LocalTime.of(6, 45)), days = setOf(DayOfWeek.MONDAY)),
            TimetableSchedule.of(listOf("06:45", "25:00", "x", "06:45"), listOf(1, 0, 8)),
        )
    }
}
