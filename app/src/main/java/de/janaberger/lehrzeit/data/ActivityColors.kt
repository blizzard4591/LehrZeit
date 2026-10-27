// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

/** Colors an activity can have (ARGB). Chosen to stay recognizable in light and dark mode. */
object ActivityColors {
    val palette: List<Int> = listOf(
        0xFF546E7A, // blue grey, General's default
        0xFFD32F2F,
        0xFFF57C00,
        0xFFFBC02D,
        0xFF388E3C,
        0xFF00897B,
        0xFF0288D1,
        0xFF3949AB,
        0xFF8E24AA,
        0xFFD81B60,
        0xFF6D4C41,
    ).map { it.toInt() }

    val GENERAL: Int = palette[0]

    /** Default color for the [index]-th regular activity; cycles through the palette, skipping General's. */
    fun forIndex(index: Int): Int = palette[1 + index.mod(palette.size - 1)]
}
