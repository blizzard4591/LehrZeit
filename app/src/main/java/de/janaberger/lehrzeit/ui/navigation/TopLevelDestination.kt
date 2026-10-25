// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.janaberger.lehrzeit.R
import kotlinx.serialization.Serializable

@Serializable data object TrackRoute
@Serializable data object WorklogRoute
@Serializable data object StatsRoute
@Serializable data object MoreRoute

/** The four tabs of the bottom navigation bar, in display order. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    TRACK(TrackRoute, R.string.nav_track, R.drawable.ic_nav_track),
    WORKLOG(WorklogRoute, R.string.nav_worklog, R.drawable.ic_nav_worklog),
    STATS(StatsRoute, R.string.nav_stats, R.drawable.ic_nav_stats),
    MORE(MoreRoute, R.string.nav_more, R.drawable.ic_nav_more),
}
