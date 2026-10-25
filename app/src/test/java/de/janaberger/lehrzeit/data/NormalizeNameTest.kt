// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeNameTest {
    @Test
    fun trimsAndCollapsesWhitespace() {
        assertEquals("7b Mathe", normalizeName("  7b \t  Mathe \n"))
    }

    @Test
    fun keepsInnerPunctuationAndCase() {
        assertEquals("Fahrten & Veranstaltungen", normalizeName("Fahrten & Veranstaltungen"))
    }

    @Test
    fun blankBecomesEmpty() {
        assertEquals("", normalizeName("   "))
    }
}
