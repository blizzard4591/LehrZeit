// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.backup

import android.content.Context
import android.net.Uri
import de.janaberger.lehrzeit.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Backup files in places the user picks through the system file dialogs. */
class BackupService(
    private val context: Context,
    private val repository: BackupRepository,
    private val preferences: AppPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun suggestedFileName(): String = "LehrZeit-Backup_${LocalDate.now()}.zip"

    suspend fun saveTo(uri: Uri) {
        val createdAt = now()
        val backup = repository.create(createdAt, appVersion())
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't open $uri")
            stream.use { writeBackup(backup, it) }
        }
        preferences.setLastBackupAt(createdAt)
    }

    suspend fun read(uri: Uri): BackupReadResult = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use(::readBackup) ?: BackupReadResult.NotABackup
    }

    suspend fun restore(backup: Backup) = repository.restore(backup)

    private fun appVersion(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}
