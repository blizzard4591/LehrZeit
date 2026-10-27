// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.MAX_NAME_LENGTH
import de.janaberger.lehrzeit.data.normalizeName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WelcomeInputError { DUPLICATE, TAKEN }

data class WelcomeUiState(
    val input: String = "",
    val names: List<String> = emptyList(),
    val inputError: WelcomeInputError? = null,
    val isSaving: Boolean = false,
) {
    val canFinish: Boolean get() = !isSaving && (names.isNotEmpty() || normalizeName(input).isNotEmpty())
}

class WelcomeViewModel(
    private val catalog: CatalogRepository,
    private val preferences: AppPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value.take(MAX_NAME_LENGTH), inputError = null) }
    }

    fun addInput() {
        viewModelScope.launch { addPendingInput() }
    }

    fun remove(name: String) {
        _uiState.update { it.copy(names = it.names - name) }
    }

    /** Creates the classes, including a name still in the text field, and ends the welcome setup. */
    fun finish() {
        if (!_uiState.value.canFinish) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            if (!addPendingInput()) {
                _uiState.update { it.copy(isSaving = false) }
                return@launch
            }
            catalog.createInitialClasses(_uiState.value.names)
            preferences.setOnboardingCompleted()
        }
    }

    fun skip() {
        viewModelScope.launch { preferences.setOnboardingCompleted() }
    }

    /** Moves the text field's content into the list. Returns false if it was rejected. */
    private suspend fun addPendingInput(): Boolean {
        val name = normalizeName(_uiState.value.input)
        if (name.isEmpty()) return true
        val error = when {
            _uiState.value.names.any { it.equals(name, ignoreCase = true) } -> WelcomeInputError.DUPLICATE
            catalog.isClassNameTaken(name, exceptId = null) -> WelcomeInputError.TAKEN
            else -> null
        }
        _uiState.update {
            if (error == null) it.copy(input = "", names = it.names + name, inputError = null) else it.copy(inputError = error)
        }
        return error == null
    }
}
