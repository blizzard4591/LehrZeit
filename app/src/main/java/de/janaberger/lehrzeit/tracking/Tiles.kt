// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.ComboKey

const val HOME_TILE_COUNT = 8

/**
 * The home-screen tiles, in this order: General, pinned [favorites], [mostUsed] combinations,
 * and, while there is little history, the remaining activities on their own.
 * Combinations that can't be booked (archived, or a class under an activity that isn't per class) are skipped.
 */
fun buildTiles(
    snapshot: CatalogSnapshot,
    favorites: List<ComboKey>,
    mostUsed: List<ComboKey>,
    limit: Int = HOME_TILE_COUNT,
): List<Combo> {
    val keys = LinkedHashSet<ComboKey>()
    snapshot.general?.let { keys += ComboKey(it.id, null) }
    val activitiesAlone = snapshot.activeActivities.filter { !it.isGeneral }.map { ComboKey(it.id, null) }
    for (key in favorites + mostUsed + activitiesAlone) {
        if (keys.size >= limit) break
        if (snapshot.isBookable(key)) keys += key
    }
    return keys.mapNotNull(snapshot::combo)
}
