// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.db.AccountWithCount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountListUiState(
    val isLoading: Boolean = true,
    val general: AccountWithCount? = null,
    val active: List<AccountWithCount> = emptyList(),
    val archived: List<AccountWithCount> = emptyList(),
)

class AccountListViewModel(private val catalog: CatalogRepository) : ViewModel() {
    val uiState: StateFlow<AccountListUiState> = catalog.observeAccounts()
        .map { all ->
            AccountListUiState(
                isLoading = false,
                general = all.firstOrNull { it.account.isGeneral },
                active = all.filter { !it.account.isGeneral && !it.account.archived },
                archived = all.filter { it.account.archived },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountListUiState())

    fun saveOrder(accountIds: List<Long>) {
        viewModelScope.launch { catalog.reorderAccounts(accountIds) }
    }
}
