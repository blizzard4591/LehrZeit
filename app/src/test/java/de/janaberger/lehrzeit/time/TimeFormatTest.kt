// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.util.Locale

class TimeFormatTest {
    private fun weekdays(vararg days: DayOfWeek) = formatWeekdays(days.toSet(), Locale.ENGLISH, everyDay = "Every day")

    @Test
    fun weekdaysInARow_becomeARange() {
        assertEquals("Mon–Fri", weekdays(FRIDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY))
        assertEquals("Mon–Wed, Fri", weekdays(MONDAY, TUESDAY, WEDNESDAY, FRIDAY))
        assertEquals("Mon, Tue, Thu", weekdays(MONDAY, TUESDAY, THURSDAY))
        assertEquals("Sat, Sun", weekdays(SATURDAY, SUNDAY))
        assertEquals("Wed", weekdays(WEDNESDAY))
        assertEquals("Every day", weekdays(*DayOfWeek.entries.toTypedArray()))
    }
}
