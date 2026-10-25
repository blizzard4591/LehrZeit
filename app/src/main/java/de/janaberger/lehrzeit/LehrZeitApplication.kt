// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit

import android.app.Application
import kotlinx.coroutines.launch

class LehrZeitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.trackingNotifier.createChannel()
        container.applicationScope.launch {
            // Seeds General and the starter activities in the phone's language at first launch.
            container.catalog.seedIfNeeded()
            // The notification may be gone if the app was force-stopped while tracking.
            container.trackingController.refreshNotification()
        }
    }
}
