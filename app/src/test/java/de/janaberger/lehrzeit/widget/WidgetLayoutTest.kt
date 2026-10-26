// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutTest {
    @Test
    fun defaultSize_fitsTheStatusBarAndOneRowOfTwoTiles() {
        assertEquals(TileGrid(columns = 2, rows = 1), tileGrid(widthDp = 250f, heightDp = 110f))
    }

    @Test
    fun oneRowHigh_showsOnlyTheStatusBar() {
        assertEquals(0, tileGrid(widthDp = 250f, heightDp = 60f).capacity)
    }

    @Test
    fun columnsGrowWithTheWidth_upToFour() {
        assertEquals(1, tileGrid(widthDp = 180f, heightDp = 200f).columns)
        assertEquals(3, tileGrid(widthDp = 340f, heightDp = 200f).columns)
        assertEquals(4, tileGrid(widthDp = 900f, heightDp = 200f).columns)
    }

    @Test
    fun rowsGrowWithTheHeight() {
        assertEquals(3, tileGrid(widthDp = 250f, heightDp = 210f).rows)
    }
}
