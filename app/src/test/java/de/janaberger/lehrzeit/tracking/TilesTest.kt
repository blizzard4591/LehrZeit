// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.db.SchoolClass
import de.janaberger.lehrzeit.data.db.WorkActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class TilesTest {
    private val general = WorkActivity(id = 1, name = "General", color = 0, isGeneral = true)
    private val correcting = WorkActivity(id = 2, name = "Correcting", color = 0, sortOrder = 1)
    private val meetings = WorkActivity(id = 3, name = "Meetings", color = 0, perClass = false, sortOrder = 2)
    private val old = WorkActivity(id = 4, name = "old", color = 0, archived = true, sortOrder = 3)
    private val c7b = SchoolClass(id = 10, name = "7b")
    private val c9a = SchoolClass(id = 11, name = "9a")
    private val c5c = SchoolClass(id = 12, name = "5c", archived = true)

    private val snapshot = CatalogSnapshot(
        activities = listOf(general, correcting, meetings, old),
        classes = listOf(c7b, c9a, c5c),
    )

    private fun keys(vararg pairs: Pair<Long, Long?>) = pairs.map { ComboKey(it.first, it.second) }

    @Test
    fun generalFirst_thenFavorites_thenMostUsed_thenActivitiesAlone() {
        val tiles = buildTiles(snapshot, favorites = keys(2L to 10L, 1L to 11L), mostUsed = keys(2L to 11L, 2L to 10L))

        assertEquals(
            keys(1L to null, 2L to 10L, 1L to 11L, 2L to 11L, 2L to null, 3L to null),
            tiles.map { it.key },
        )
    }

    @Test
    fun skipsCombinationsThatCantBeBooked() {
        val tiles = buildTiles(
            snapshot,
            favorites = keys(
                4L to 10L, // archived activity
                2L to 12L, // archived class
                3L to 11L, // meetings aren't booked per class
                1L to null, // General is already first
                99L to null, // deleted activity
            ),
            mostUsed = emptyList(),
        )

        assertEquals(keys(1L to null, 2L to null, 3L to null), tiles.map { it.key })
    }

    @Test
    fun stopsAtTheLimit() {
        val tiles = buildTiles(snapshot, favorites = keys(2L to 10L, 2L to 11L, 3L to null), mostUsed = emptyList(), limit = 3)

        assertEquals(keys(1L to null, 2L to 10L, 2L to 11L), tiles.map { it.key })
    }
}
