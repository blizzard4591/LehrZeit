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
import de.janaberger.lehrzeit.ui.navigation.ClassEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClassEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val isArchived: Boolean = false,
    val name: String = "",
    val entryCount: Int = 0,
    val nameError: NameError? = null,
    val isSaving: Boolean = false,
    /** Set once the class was saved or deleted; the screen then closes. */
    val isFinished: Boolean = false,
    private val savedName: String = "",
) {
    val hasChanges: Boolean get() = name != savedName
}

class ClassEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val classId: Long? = savedStateHandle.toRoute<ClassEditRoute>().id

    private val _uiState = MutableStateFlow(ClassEditUiState())
    val uiState: StateFlow<ClassEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (classId == null) {
            _uiState.value = ClassEditUiState(isLoading = false)
            return
        }
        val details = catalog.getClassDetails(classId)
        if (details == null) {
            _uiState.update { it.copy(isFinished = true) }
            return
        }
        val schoolClass = details.schoolClass
        _uiState.value = ClassEditUiState(
            isLoading = false,
            isNew = false,
            isArchived = schoolClass.archived,
            name = schoolClass.name,
            entryCount = details.entryCount,
            savedName = schoolClass.name,
        )
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name.take(MAX_NAME_LENGTH), nameError = null) }
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
            val result = catalog.saveClass(classId, state.name, archived)
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
        val id = classId ?: return
        viewModelScope.launch {
            when (catalog.deleteClass(id)) {
                DeleteResult.DELETED -> _uiState.update { it.copy(isFinished = true) }
                // An entry was added in the meantime; the button disables itself.
                DeleteResult.HAS_ENTRIES -> _uiState.update { it.copy(entryCount = maxOf(it.entryCount, 1)) }
            }
        }
    }
}
