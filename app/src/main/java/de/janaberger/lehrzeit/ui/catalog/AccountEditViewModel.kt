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
import de.janaberger.lehrzeit.data.db.WorkActivity
import de.janaberger.lehrzeit.ui.components.NameError
import de.janaberger.lehrzeit.ui.navigation.AccountEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val isGeneral: Boolean = false,
    val isArchived: Boolean = false,
    val name: String = "",
    val color: Int = 0,
    /** The active activities the account can offer. */
    val activities: List<WorkActivity> = emptyList(),
    val checked: Set<Long> = emptySet(),
    val entryCount: Int = 0,
    val nameError: NameError? = null,
    val isSaving: Boolean = false,
    /** Set once the account was saved or deleted; the screen then closes. */
    val isFinished: Boolean = false,
    private val savedName: String = "",
    private val savedColor: Int = 0,
    private val savedChecked: Set<Long> = emptySet(),
) {
    val hasChanges: Boolean get() = name != savedName || color != savedColor || checked != savedChecked
}

class AccountEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val accountId: Long? = savedStateHandle.toRoute<AccountEditRoute>().id

    private val _uiState = MutableStateFlow(AccountEditUiState())
    val uiState: StateFlow<AccountEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val activities = catalog.activeActivities()
        if (accountId == null) {
            // New accounts start with no activities ticked.
            val color = catalog.nextAccountColor()
            _uiState.value = AccountEditUiState(isLoading = false, color = color, activities = activities, savedColor = color)
            return
        }
        val details = catalog.getAccountDetails(accountId)
        if (details == null) {
            _uiState.update { it.copy(isFinished = true) }
            return
        }
        val account = details.account
        val checked = details.activityIds intersect activities.map { it.id }.toSet()
        _uiState.value = AccountEditUiState(
            isLoading = false,
            isNew = false,
            isGeneral = account.isGeneral,
            isArchived = account.archived,
            name = account.name,
            color = account.color,
            activities = activities,
            checked = checked,
            entryCount = details.entryCount,
            savedName = account.name,
            savedColor = account.color,
            savedChecked = checked,
        )
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name.take(MAX_NAME_LENGTH), nameError = null) }
    }

    fun onColorChange(color: Int) {
        _uiState.update { it.copy(color = color) }
    }

    fun onToggleActivity(activityId: Long) {
        _uiState.update {
            it.copy(checked = if (activityId in it.checked) it.checked - activityId else it.checked + activityId)
        }
    }

    fun onSelectAll(selectAll: Boolean) {
        _uiState.update { state -> state.copy(checked = if (selectAll) state.activities.map { it.id }.toSet() else emptySet()) }
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
            val result = catalog.saveAccount(accountId, state.name, state.color, state.checked, archived)
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
        val id = accountId ?: return
        viewModelScope.launch {
            when (catalog.deleteAccount(id)) {
                DeleteResult.DELETED -> _uiState.update { it.copy(isFinished = true) }
                // An entry was added in the meantime; the button disables itself.
                DeleteResult.HAS_ENTRIES -> _uiState.update { it.copy(entryCount = maxOf(it.entryCount, 1)) }
            }
        }
    }
}
