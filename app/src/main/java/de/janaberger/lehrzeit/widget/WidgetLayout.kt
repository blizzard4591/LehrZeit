// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.widget

/** The widget's measures, in dp. */
internal object WidgetDimens {
    const val PADDING = 6f
    const val STATUS_HEIGHT = 48f
    const val TILE_HEIGHT = 44f
    const val GAP = 4f
    const val MIN_TILE_WIDTH = 100f
    const val MAX_COLUMNS = 4
}

data class TileGrid(val columns: Int, val rows: Int) {
    val capacity: Int get() = columns * rows
}

/** How many tiles fit below the status bar of a widget of this size (in dp). */
fun tileGrid(widthDp: Float, heightDp: Float): TileGrid = with(WidgetDimens) {
    val columns = ((widthDp - 2 * PADDING + GAP) / (MIN_TILE_WIDTH + GAP)).toInt().coerceIn(1, MAX_COLUMNS)
    val rows = ((heightDp - 2 * PADDING - STATUS_HEIGHT) / (GAP + TILE_HEIGHT)).toInt().coerceAtLeast(0)
    TileGrid(columns, rows)
}
