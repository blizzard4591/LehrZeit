// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import de.janaberger.lehrzeit.data.ReminderSettings
import de.janaberger.lehrzeit.data.ReminderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTest {
    private val hour = 3_600_000L

    @Test
    fun dueAfterTheChosenHours_fromTheStartOrTheLastKeepGoing() {
        val settings = ReminderSettings(enabled = true, hours = 3)
        assertEquals(3 * hour, nextReminderAt(entryStartUtc = 0, ReminderState(entryId = 1), settings))
        assertEquals(5 * hour, nextReminderAt(entryStartUtc = 0, ReminderState(entryId = 1, anchorUtc = 2 * hour), settings))
    }

    @Test
    fun noneWhenOff_orAlreadyShown() {
        assertNull(nextReminderAt(0, ReminderState(entryId = 1), ReminderSettings(enabled = false)))
        assertNull(nextReminderAt(0, ReminderState(entryId = 1, fired = true), ReminderSettings()))
    }
}
