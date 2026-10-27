// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.db.SchoolClass
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClassListUiState(
    val isLoading: Boolean = true,
    val active: List<SchoolClass> = emptyList(),
    val archived: List<SchoolClass> = emptyList(),
)

class ClassListViewModel(private val catalog: CatalogRepository) : ViewModel() {
    val uiState: StateFlow<ClassListUiState> = catalog.observeClasses()
        .map { all ->
            ClassListUiState(
                isLoading = false,
                active = all.filter { !it.archived },
                archived = all.filter { it.archived },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClassListUiState())

    fun saveOrder(classIds: List<Long>) {
        viewModelScope.launch { catalog.reorderClasses(classIds) }
    }
}
