// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.db.WorkActivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActivityListUiState(
    val isLoading: Boolean = true,
    val general: WorkActivity? = null,
    val active: List<WorkActivity> = emptyList(),
    val archived: List<WorkActivity> = emptyList(),
)

class ActivityListViewModel(private val catalog: CatalogRepository) : ViewModel() {
    val uiState: StateFlow<ActivityListUiState> = catalog.observeActivities()
        .map { all ->
            ActivityListUiState(
                isLoading = false,
                general = all.firstOrNull { it.isGeneral },
                active = all.filter { !it.isGeneral && !it.archived },
                archived = all.filter { it.archived },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityListUiState())

    fun saveOrder(activityIds: List<Long>) {
        viewModelScope.launch { catalog.reorderActivities(activityIds) }
    }
}
