// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.db.Account
import de.janaberger.lehrzeit.data.db.WorkActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class TilesTest {
    private val general = Account(id = 1, name = "General", color = 0, isGeneral = true)
    private val a7b = Account(id = 2, name = "7b", color = 0, sortOrder = 1)
    private val a9a = Account(id = 3, name = "9a", color = 0, sortOrder = 2)
    private val old = Account(id = 4, name = "old", color = 0, archived = true, sortOrder = 3)
    private val correcting = WorkActivity(id = 10, name = "Correcting")
    private val exams = WorkActivity(id = 11, name = "Exams")
    private val trips = WorkActivity(id = 12, name = "Trips", archived = true)

    private val snapshot = CatalogSnapshot(
        accounts = listOf(general, a7b, a9a, old),
        activities = listOf(correcting, exams, trips),
        links = setOf(ComboKey(2, 10), ComboKey(2, 11), ComboKey(2, 12), ComboKey(3, 10), ComboKey(4, 10)),
    )

    private fun keys(vararg pairs: Pair<Long, Long?>) = pairs.map { ComboKey(it.first, it.second) }

    @Test
    fun generalFirst_thenFavorites_thenMostUsed_thenAccountsAlone() {
        val tiles = buildTiles(snapshot, favorites = keys(3L to 10L), mostUsed = keys(2L to 11L, 3L to 10L))

        assertEquals(
            keys(1L to null, 3L to 10L, 2L to 11L, 2L to null, 3L to null),
            tiles.map { it.key },
        )
    }

    @Test
    fun skipsCombinationsThatCantBeBooked() {
        val tiles = buildTiles(
            snapshot,
            favorites = keys(
                4L to 10L, // archived account
                2L to 12L, // archived activity
                3L to 11L, // 9a doesn't offer Exams
                1L to null, // General is already first
                99L to null, // deleted account
            ),
            mostUsed = emptyList(),
        )

        assertEquals(keys(1L to null, 2L to null, 3L to null), tiles.map { it.key })
    }

    @Test
    fun stopsAtTheLimit() {
        val tiles = buildTiles(snapshot, favorites = keys(2L to 10L, 2L to 11L, 3L to 10L), mostUsed = emptyList(), limit = 3)

        assertEquals(keys(1L to null, 2L to 10L, 2L to 11L), tiles.map { it.key })
    }
}
