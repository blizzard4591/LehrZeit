// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.backup

import androidx.room.withTransaction
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.data.ReminderSettings
import de.janaberger.lehrzeit.data.ShortEntrySettings
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import de.janaberger.lehrzeit.data.db.SchoolClass
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
                activities = dao.activities().map {
                    BackupActivity(it.id, it.name, it.color, it.isGeneral, it.perClass, it.archived, it.sortOrder)
                },
                classes = dao.classes().map { BackupClass(it.id, it.name, it.archived, it.sortOrder) },
                entries = dao.entries().map {
                    BackupEntry(it.id, it.activityId, it.classId, it.startUtc, it.endUtc, it.note, it.createdAt, it.updatedAt)
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
            dao.deleteClasses()
            dao.deleteActivities()
            dao.insertActivities(
                backup.activities.map { WorkActivity(it.id, it.name, it.color, it.isGeneral, it.perClass, it.archived, it.sortOrder) },
            )
            dao.insertClasses(backup.classes.map { SchoolClass(it.id, it.name, it.archived, it.sortOrder) })
            dao.insertEntries(
                backup.entries.map { Entry(it.id, it.activityId, it.classId, it.startUtc, it.endUtc, it.note, it.createdAt, it.updatedAt) },
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
