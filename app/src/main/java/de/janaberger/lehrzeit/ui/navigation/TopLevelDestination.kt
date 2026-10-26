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

/** Edits an entry, or creates one if [id] is null. Reached from the worklog and the Track screen. */
@Serializable data class EntryEditRoute(val id: Long? = null)

/** The More tab is a nested graph so that its sub-screens keep the tab selected. */
@Serializable data object MoreGraph
@Serializable data object MoreRoute
@Serializable data object AccountsRoute
@Serializable data class AccountEditRoute(val id: Long? = null)
@Serializable data object ActivitiesRoute
@Serializable data class ActivityEditRoute(val id: Long? = null)
@Serializable data object SettingsRoute
@Serializable data object ExportRoute
@Serializable data object BackupRoute

/** The four tabs of the bottom navigation bar, in display order. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    /** Where tapping the already selected tab returns to. */
    val rootRoute: Any = route,
) {
    TRACK(TrackRoute, R.string.nav_track, R.drawable.ic_nav_track),
    WORKLOG(WorklogRoute, R.string.nav_worklog, R.drawable.ic_nav_worklog),
    STATS(StatsRoute, R.string.nav_stats, R.drawable.ic_nav_stats),
    MORE(MoreGraph, R.string.nav_more, R.drawable.ic_nav_more, rootRoute = MoreRoute),
}
