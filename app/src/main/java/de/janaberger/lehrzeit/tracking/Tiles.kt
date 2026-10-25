// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.ComboKey

const val HOME_TILE_COUNT = 8

/**
 * The home-screen tiles, in this order: General, pinned [favorites], [mostUsed] combinations,
 * and, while there is little history, the remaining accounts on their own.
 * Combinations that can't be booked (archived, or no longer offered) are skipped.
 */
fun buildTiles(
    snapshot: CatalogSnapshot,
    favorites: List<ComboKey>,
    mostUsed: List<ComboKey>,
    limit: Int = HOME_TILE_COUNT,
): List<Combo> {
    val keys = LinkedHashSet<ComboKey>()
    snapshot.general?.let { keys += ComboKey(it.id, null) }
    val generalId = snapshot.general?.id
    val accountsAlone = snapshot.activeAccounts.filter { !it.isGeneral }.map { ComboKey(it.id, null) }
    for (key in favorites + mostUsed + accountsAlone) {
        if (keys.size >= limit) break
        if (key.accountId != generalId && snapshot.isBookable(key)) keys += key
    }
    return keys.mapNotNull(snapshot::combo)
}
