// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.backup

import androidx.room.withTransaction
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.data.ReminderSettings
import de.janaberger.lehrzeit.data.ShortEntrySettings
import de.janaberger.lehrzeit.data.db.Account
import de.janaberger.lehrzeit.data.db.AccountActivity
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.db.Favorite
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import de.janaberger.lehrzeit.data.db.WorkActivity
import kotlinx.coroutines.flow.first

class BackupRepository(
    private val db: LehrZeitDatabase,
    private val preferences: AppPreferences,
    private val entries: EntryRepository,
) {
    private val dao = db.backupDao()

    /** A consistent snapshot of all data and settings. */
    suspend fun create(now: Long, appVersion: String): Backup {
        val shortEntries = preferences.shortEntries.first()
        val reminder = preferences.reminder.first()
        return db.withTransaction {
            Backup(
                createdAtUtc = now,
                appVersion = appVersion,
                accounts = dao.accounts().map { BackupAccount(it.id, it.name, it.color, it.isGeneral, it.archived, it.sortOrder) },
                activities = dao.activities().map { BackupActivity(it.id, it.name, it.archived, it.sortOrder) },
                accountActivities = dao.links().map { BackupLink(it.accountId, it.activityId) },
                favorites = dao.favorites().map { BackupFavorite(it.id, it.accountId, it.activityId, it.sortOrder) },
                entries = dao.entries().map {
                    BackupEntry(it.id, it.accountId, it.activityId, it.startUtc, it.endUtc, it.note, it.createdAt, it.updatedAt)
                },
                settings = BackupSettings(shortEntries.enabled, shortEntries.minSeconds, reminder.enabled, reminder.hours),
            )
        }
    }

    /**
     * Replaces all data with [backup] in one transaction, so a failure leaves everything as it was.
     * Afterwards the welcome setup counts as done and a pause or pending undo is forgotten.
     * Callers must refresh the tracking notification.
     */
    suspend fun restore(backup: Backup) {
        db.withTransaction {
            // Children before parents, because of the foreign keys.
            dao.deleteEntries()
            dao.deleteFavorites()
            dao.deleteLinks()
            dao.deleteActivities()
            dao.deleteAccounts()
            dao.insertAccounts(backup.accounts.map { Account(it.id, it.name, it.color, it.isGeneral, it.archived, it.sortOrder) })
            dao.insertActivities(backup.activities.map { WorkActivity(it.id, it.name, it.archived, it.sortOrder) })
            dao.insertLinks(backup.accountActivities.map { AccountActivity(it.accountId, it.activityId) })
            dao.insertFavorites(backup.favorites.map { Favorite(it.id, it.accountId, it.activityId, it.sortOrder) })
            dao.insertEntries(
                backup.entries.map { Entry(it.id, it.accountId, it.activityId, it.startUtc, it.endUtc, it.note, it.createdAt, it.updatedAt) },
            )
        }
        preferences.setShortEntries(ShortEntrySettings(backup.settings.discardShortEntries, backup.settings.minEntrySeconds.coerceAtLeast(1)))
        preferences.setReminder(ReminderSettings(backup.settings.reminderEnabled, backup.settings.reminderHours.coerceAtLeast(1)))
        preferences.setPaused(null)
        // Restored entries may reuse the ID of the timer that ran before, so start the reminder afresh.
        preferences.setReminderState(null)
        preferences.setOnboardingCompleted()
        entries.forgetAllDeleted()
    }
}
