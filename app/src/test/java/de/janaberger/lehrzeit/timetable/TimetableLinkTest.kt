// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimetableLinkTest {
    @Test
    fun httpsAndWebcalLinks_areAccepted() {
        val link = "https://school.webuntis.com/WebUntis/ical_export?school=school&id=ABC&elemId=1&token=secret"
        assertEquals(link, normalizeTimetableUrl("  $link\n"))
        assertEquals(link, normalizeTimetableUrl(link.replace("https://", "webcal://")))
        assertEquals("wrapped in an email", link, normalizeTimetableUrl(link.replace("ical_export", "ical_\r\nexport")))
    }

    @Test
    fun otherInput_isRejected() {
        assertNull(normalizeTimetableUrl("http://school.webuntis.com/ical"))
        assertNull(normalizeTimetableUrl("school.webuntis.com/ical"))
        assertNull(normalizeTimetableUrl("https:///ical"))
        assertNull(normalizeTimetableUrl("https://school.webuntis.com/{token}"))
    }

    @Test
    fun host_leavesOutTheToken() {
        assertEquals("school.webuntis.com", timetableHost("https://school.webuntis.com/WebUntis/ical_export?token=secret"))
    }
}
