// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit

import android.content.Context
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import de.janaberger.lehrzeit.export.ExportWriter
import de.janaberger.lehrzeit.tracking.TrackingController
import de.janaberger.lehrzeit.tracking.TrackingNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: the app-wide singletons. */
class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = LehrZeitDatabase.build(context)

    val preferences = AppPreferences(context)

    val catalog = CatalogRepository(
        db = database,
        starterActivityNames = { context.resources.getStringArray(R.array.starter_activities).toList() },
        generalDisplayName = { context.getString(R.string.account_general) },
    )

    val entries = EntryRepository(database)

    val exportWriter = ExportWriter(context, entries, catalog)

    val trackingNotifier = TrackingNotifier(context)

    val trackingController = TrackingController(
        entries = entries,
        catalog = catalog,
        preferences = preferences,
        notifier = trackingNotifier,
        generalName = { context.getString(R.string.account_general) },
    )
}
