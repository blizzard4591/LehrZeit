// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.data.SaveResult
import de.janaberger.lehrzeit.data.ShortEntrySettings
import de.janaberger.lehrzeit.data.StarterActivity
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private lateinit var db: LehrZeitDatabase
    private lateinit var catalog: CatalogRepository
    private lateinit var entries: EntryRepository
    private lateinit var preferences: AppPreferences
    private lateinit var backups: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LehrZeitDatabase::class.java).build()
        catalog = CatalogRepository(
            db,
            starterActivities = { listOf(StarterActivity("Teaching", perClass = true), StarterActivity("Meetings", perClass = false)) },
            generalDisplayName = { "General" },
        )
        entries = EntryRepository(db)
        preferences = AppPreferences(context)
        backups = BackupRepository(db, preferences, entries)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun backupThenRestore_bringsBackExactlyTheSameDataAndSettings() = runTest {
        catalog.createInitialClasses(listOf("7b", "9a"))
        val activity = catalog.activeRegularActivities().first()
        val schoolClass = catalog.activeClasses().first()
        entries.start(ComboKey(activity.id, schoolClass.id), now = 0)
        entries.start(ComboKey(activity.id, null), now = 30 * 60_000L) // first entry finished, second running
        preferences.setShortEntries(ShortEntrySettings(enabled = false, minSeconds = 120))
        val backup = backups.create(now = 1, appVersion = "test")

        // Change everything, then restore.
        catalog.saveActivity(null, "Fachschaft", color = 0, perClass = false)
        entries.stop(now = 40 * 60_000L)
        preferences.setShortEntries(ShortEntrySettings())
        backups.restore(backup)

        assertEquals(backup, backups.create(now = 1, appVersion = "test"))
        assertEquals(ShortEntrySettings(enabled = false, minSeconds = 120), preferences.shortEntries.first())
        assertEquals(activity.id, entries.getRunning()!!.entry.activityId)
        assertTrue(preferences.onboardingCompleted.first())
        // New rows still get fresh IDs after a restore.
        assertTrue(catalog.saveActivity(null, "Neu", color = 0, perClass = true) is SaveResult.Saved)
    }

    @Test
    fun aBackupOfRealData_survivesTheFileFormat() = runTest {
        catalog.createInitialClasses(listOf("7b"))
        entries.start(ComboKey(catalog.activeRegularActivities().first().id, null), now = 0)
        val backup = backups.create(now = 1, appVersion = "test")

        val bytes = ByteArrayOutputStream().also { writeBackup(backup, it) }.toByteArray()

        assertEquals(BackupReadResult.Ok(backup), readBackup(ByteArrayInputStream(bytes)))
    }
}
