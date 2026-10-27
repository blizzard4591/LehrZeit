// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class IcsTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun utc(dateTime: String, zone: ZoneId = berlin) = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    /** A calendar around [events], with CRLF line ends like real files. */
    private fun calendar(vararg events: String) =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "REFRESH-INTERVAL;VALUE=DURATION:P1D") + events + "END:VCALENDAR")
            .joinToString("\r\n") { it.trimIndent().replace("\n", "\r\n") }

    @Test
    fun webUntisEvent_isRead() {
        val text = calendar(
            """
            BEGIN:VEVENT
            DTSTAMP:20261027T095932Z
            UID:79925-4040001
            STATUS:CONFIRMED
            DTSTART;TZID=Europe/Berlin:20261106T085000
            DTEND;TZID=Europe/Berlin:20261106T093500
            SUMMARY:V_Std.DoBe
            LOCATION:O47
            DESCRIPTION:07.3\; V-DoBe XYZ
            END:VEVENT
            """,
        )

        assertEquals(
            listOf(IcsEvent("79925-4040001", utc("2026-11-06T08:50"), utc("2026-11-06T09:35"), "V_Std.DoBe", "07.3; V-DoBe XYZ", "O47")),
            // The default zone doesn't matter when the event names its own.
            parseIcs(text, ZoneOffset.UTC),
        )
    }

    @Test
    fun foldedLines_escapes_utcAndFloatingTimes() {
        val text = calendar(
            """
            BEGIN:VEVENT
            UID:a
            DTSTART:20261102T070000Z
            DTEND:20261102T074500Z
            SUMMARY:Lesson prep\, part 2
            DESCRIPTION:First line\nsecond li
             ne
            END:VEVENT
            """,
            """
            BEGIN:VEVENT
            UID:b
            DTSTART:20261102T080000
            DURATION:PT45M
            END:VEVENT
            """,
        )

        val (utcEvent, floating) = parseIcs(text, berlin)!!
        assertEquals(utc("2026-11-02T08:00"), utcEvent.startUtc)
        assertEquals("Lesson prep, part 2", utcEvent.summary)
        assertEquals("First line\nsecond line", utcEvent.description)
        assertEquals(utc("2026-11-02T08:00"), floating.startUtc)
        assertEquals(utc("2026-11-02T08:45"), floating.endUtc)
    }

    @Test
    fun allDayCancelledAndEndlessEvents_areSkipped_andAlarmsIgnored() {
        val text = calendar(
            """
            BEGIN:VEVENT
            UID:holiday
            DTSTART;VALUE=DATE:20261223
            DTEND;VALUE=DATE:20270107
            END:VEVENT
            """,
            """
            BEGIN:VEVENT
            UID:cancelled
            STATUS:CANCELLED
            DTSTART;TZID=Europe/Berlin:20261102T080000
            DTEND;TZID=Europe/Berlin:20261102T084500
            END:VEVENT
            """,
            """
            BEGIN:VEVENT
            UID:no-end
            DTSTART;TZID=Europe/Berlin:20261102T080000
            END:VEVENT
            """,
            """
            BEGIN:VEVENT
            UID:with-alarm
            DTSTART;TZID=Europe/Berlin:20261102T080000
            DTEND;TZID=Europe/Berlin:20261102T084500
            BEGIN:VALARM
            DESCRIPTION:Reminder
            END:VALARM
            DESCRIPTION:Q1 XYZ
            END:VEVENT
            """,
        )

        val events = parseIcs(text, berlin)!!
        assertEquals(listOf("with-alarm"), events.map { it.uid })
        assertEquals("Q1 XYZ", events.single().description)
    }

    @Test
    fun unknownZone_usesTheDefault_andMissingUidIsGenerated() {
        val text = calendar(
            """
            BEGIN:VEVENT
            DTSTART;TZID="W. Europe Standard Time":20261102T080000
            DTEND;TZID="W. Europe Standard Time":20261102T084500
            SUMMARY:BI
            END:VEVENT
            """,
        )

        val event = parseIcs(text, berlin)!!.single()
        assertEquals(utc("2026-11-02T08:00"), event.startUtc)
        assertEquals("the same for the same lesson", parseIcs(text, berlin)!!.single().uid, event.uid)
    }

    @Test
    fun otherFiles_areNotCalendars() {
        assertNull(parseIcs("<!DOCTYPE html><html>Login</html>", berlin))
        assertNull(parseIcs("", berlin))
        assertTrue(parseIcs("\uFEFFBEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n", berlin)!!.isEmpty())
    }
}
