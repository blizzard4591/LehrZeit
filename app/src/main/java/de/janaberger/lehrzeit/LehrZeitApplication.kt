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
        // Seeds General and the starter activities in the phone's language at first launch.
        container.applicationScope.launch { container.catalog.seedIfNeeded() }
    }
}
