// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.DeleteResult
import de.janaberger.lehrzeit.data.MAX_NAME_LENGTH
import de.janaberger.lehrzeit.data.SaveResult
import de.janaberger.lehrzeit.ui.components.NameError
import de.janaberger.lehrzeit.ui.navigation.ActivityEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val isGeneral: Boolean = false,
    val isArchived: Boolean = false,
    val name: String = "",
    val color: Int = 0,
    /** Whether a class can be chosen under the activity. */
    val perClass: Boolean = true,
    val entryCount: Int = 0,
    val nameError: NameError? = null,
    val isSaving: Boolean = false,
    /** Set once the activity was saved or deleted; the screen then closes. */
    val isFinished: Boolean = false,
    private val savedName: String = "",
    private val savedColor: Int = 0,
    private val savedPerClass: Boolean = true,
) {
    val hasChanges: Boolean get() = name != savedName || color != savedColor || perClass != savedPerClass
}

class ActivityEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val activityId: Long? = savedStateHandle.toRoute<ActivityEditRoute>().id

    private val _uiState = MutableStateFlow(ActivityEditUiState())
    val uiState: StateFlow<ActivityEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (activityId == null) {
            val color = catalog.nextActivityColor()
            _uiState.value = ActivityEditUiState(isLoading = false, color = color, savedColor = color)
            return
        }
        val details = catalog.getActivityDetails(activityId)
        if (details == null) {
            _uiState.update { it.copy(isFinished = true) }
            return
        }
        val activity = details.activity
        _uiState.value = ActivityEditUiState(
            isLoading = false,
            isNew = false,
            isGeneral = activity.isGeneral,
            isArchived = activity.archived,
            name = activity.name,
            color = activity.color,
            perClass = activity.perClass,
            entryCount = details.entryCount,
            savedName = activity.name,
            savedColor = activity.color,
            savedPerClass = activity.perClass,
        )
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name.take(MAX_NAME_LENGTH), nameError = null) }
    }

    fun onColorChange(color: Int) {
        _uiState.update { it.copy(color = color) }
    }

    fun onPerClassChange(perClass: Boolean) {
        _uiState.update { it.copy(perClass = perClass) }
    }

    fun save() = persist(archived = _uiState.value.isArchived)

    /** Archiving and restoring also save the other changes on the screen. */
    fun archive() = persist(archived = true)

    fun unarchive() = persist(archived = false)

    private fun persist(archived: Boolean) {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = catalog.saveActivity(activityId, state.name, state.color, state.perClass, archived)
            _uiState.update {
                when (result) {
                    is SaveResult.Saved -> it.copy(isSaving = false, isFinished = true)
                    SaveResult.NameEmpty -> it.copy(isSaving = false, nameError = NameError.EMPTY)
                    SaveResult.NameTaken -> it.copy(isSaving = false, nameError = NameError.TAKEN)
                }
            }
        }
    }

    fun delete() {
        val id = activityId ?: return
        viewModelScope.launch {
            when (catalog.deleteActivity(id)) {
                DeleteResult.DELETED -> _uiState.update { it.copy(isFinished = true) }
                // An entry was added in the meantime; the button disables itself.
                DeleteResult.HAS_ENTRIES -> _uiState.update { it.copy(entryCount = maxOf(it.entryCount, 1)) }
            }
        }
    }
}
