// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.janaberger.lehrzeit.LehrZeitApplication
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.ui.backup.BackupViewModel
import de.janaberger.lehrzeit.ui.catalog.ActivityEditViewModel
import de.janaberger.lehrzeit.ui.catalog.ActivityListViewModel
import de.janaberger.lehrzeit.ui.catalog.ClassEditViewModel
import de.janaberger.lehrzeit.ui.catalog.ClassListViewModel
import de.janaberger.lehrzeit.ui.entry.EntryEditViewModel
import de.janaberger.lehrzeit.ui.export.ExportViewModel
import de.janaberger.lehrzeit.ui.settings.SettingsViewModel
import de.janaberger.lehrzeit.ui.stats.StatsViewModel
import de.janaberger.lehrzeit.ui.track.TrackViewModel
import de.janaberger.lehrzeit.ui.welcome.WelcomeViewModel
import de.janaberger.lehrzeit.ui.worklog.WorklogViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Creates every ViewModel with its dependencies from the [de.janaberger.lehrzeit.AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { AppViewModel(app().container.preferences) }
        initializer { WelcomeViewModel(app().container.catalog, app().container.preferences) }
        initializer { ActivityListViewModel(app().container.catalog) }
        initializer { ActivityEditViewModel(createSavedStateHandle(), app().container.catalog) }
        initializer { ClassListViewModel(app().container.catalog) }
        initializer { ClassEditViewModel(createSavedStateHandle(), app().container.catalog) }
        initializer {
            val container = app().container
            TrackViewModel(container.entries, container.catalog, container.preferences, container.trackingController)
        }
        initializer { SettingsViewModel(app().container.preferences, app().container.trackingController) }
        initializer { StatsViewModel(app().container.entries, app().container.catalog) }
        initializer { ExportViewModel(app().container.entries, app().container.exportWriter) }
        initializer {
            val container = app().container
            BackupViewModel(container.backups, container.preferences, container.trackingController)
        }
        initializer {
            val container = app().container
            WorklogViewModel(container.entries, container.catalog, container.trackingController)
        }
        initializer {
            val container = app().container
            EntryEditViewModel(createSavedStateHandle(), container.entries, container.catalog, container.trackingController)
        }
    }
}

private fun CreationExtras.app(): LehrZeitApplication = this[APPLICATION_KEY] as LehrZeitApplication

class AppViewModel(preferences: AppPreferences) : ViewModel() {
    /** Null until the stored value is known, so the welcome screen doesn't flash for returning users. */
    val onboardingCompleted: StateFlow<Boolean?> =
        preferences.onboardingCompleted.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
