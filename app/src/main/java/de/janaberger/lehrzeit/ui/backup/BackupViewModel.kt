// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.backup.Backup
import de.janaberger.lehrzeit.data.backup.BackupReadResult
import de.janaberger.lehrzeit.data.backup.BackupService
import de.janaberger.lehrzeit.tracking.TrackingController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupError { WRITE_FAILED, NOT_A_BACKUP, DAMAGED, TOO_NEW, TOO_OLD, RESTORE_FAILED }

data class BackupUiState(
    val lastBackupAt: Long? = null,
    val isWorking: Boolean = false,
    /** A checked backup waiting for the user to confirm that it replaces everything. */
    val pendingRestore: Backup? = null,
    val error: BackupError? = null,
)

sealed interface BackupEvent {
    data class Saved(val fileName: String) : BackupEvent
    data object Restored : BackupEvent
}

class BackupViewModel(
    private val backups: BackupService,
    preferences: AppPreferences,
    private val controller: TrackingController,
) : ViewModel() {
    private val state = MutableStateFlow(BackupUiState())
    private val eventChannel = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<BackupUiState> = combine(state, preferences.lastBackupAt) { state, last -> state.copy(lastBackupAt = last) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun suggestedFileName(): String = backups.suggestedFileName()

    fun createBackup(uri: Uri, fileName: String) = work(onFailure = BackupError.WRITE_FAILED) {
        backups.saveTo(uri)
        eventChannel.send(BackupEvent.Saved(fileName))
    }

    /** Reads and checks a picked file; a valid backup then waits for [confirmRestore]. */
    fun inspect(uri: Uri) = work(onFailure = BackupError.NOT_A_BACKUP) {
        when (val result = backups.read(uri)) {
            is BackupReadResult.Ok -> state.update { it.copy(pendingRestore = result.backup) }
            BackupReadResult.NotABackup -> state.update { it.copy(error = BackupError.NOT_A_BACKUP) }
            BackupReadResult.Damaged -> state.update { it.copy(error = BackupError.DAMAGED) }
            BackupReadResult.TooNew -> state.update { it.copy(error = BackupError.TOO_NEW) }
            BackupReadResult.TooOld -> state.update { it.copy(error = BackupError.TOO_OLD) }
        }
    }

    fun confirmRestore() {
        val backup = state.value.pendingRestore ?: return
        state.update { it.copy(pendingRestore = null) }
        work(onFailure = BackupError.RESTORE_FAILED) {
            backups.restore(backup)
            controller.refreshNotification()
            eventChannel.send(BackupEvent.Restored)
        }
    }

    fun cancelRestore() {
        state.update { it.copy(pendingRestore = null) }
    }

    fun dismissError() {
        state.update { it.copy(error = null) }
    }

    private fun work(onFailure: BackupError, block: suspend () -> Unit) {
        if (state.value.isWorking) return
        state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                state.update { it.copy(error = onFailure) }
            } finally {
                state.update { it.copy(isWorking = false) }
            }
        }
    }
}
