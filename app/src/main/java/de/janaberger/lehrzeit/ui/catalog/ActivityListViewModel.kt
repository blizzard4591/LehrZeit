// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.db.ActivityWithCount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActivityListUiState(
    val isLoading: Boolean = true,
    val active: List<ActivityWithCount> = emptyList(),
    val archived: List<ActivityWithCount> = emptyList(),
)

class ActivityListViewModel(private val catalog: CatalogRepository) : ViewModel() {
    val uiState: StateFlow<ActivityListUiState> = catalog.observeActivities()
        .map { all ->
            ActivityListUiState(
                isLoading = false,
                active = all.filter { !it.activity.archived },
                archived = all.filter { it.activity.archived },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityListUiState())

    fun saveOrder(activityIds: List<Long>) {
        viewModelScope.launch { catalog.reorderActivities(activityIds) }
    }
}
