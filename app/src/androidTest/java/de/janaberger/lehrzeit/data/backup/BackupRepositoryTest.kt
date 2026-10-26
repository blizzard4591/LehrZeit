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
        catalog = CatalogRepository(db, starterActivityNames = { listOf("Teaching", "Correcting") }, generalDisplayName = { "General" })
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
        catalog.createInitialAccounts(listOf("7b", "9a"))
        val account = catalog.activeRegularAccounts().first()
        val activity = catalog.activeActivities().first()
        entries.toggleFavorite(ComboKey(account.id, activity.id))
        entries.start(ComboKey(account.id, activity.id), now = 0)
        entries.start(ComboKey(account.id, null), now = 30 * 60_000L) // first entry finished, second running
        preferences.setShortEntries(ShortEntrySettings(enabled = false, minSeconds = 120))
        val backup = backups.create(now = 1, appVersion = "test")

        // Change everything, then restore.
        catalog.saveAccount(null, "Fachschaft", color = 0, activityIds = emptySet())
        entries.stop(now = 40 * 60_000L)
        entries.toggleFavorite(ComboKey(account.id, activity.id))
        preferences.setShortEntries(ShortEntrySettings())
        backups.restore(backup)

        assertEquals(backup, backups.create(now = 1, appVersion = "test"))
        assertEquals(ShortEntrySettings(enabled = false, minSeconds = 120), preferences.shortEntries.first())
        assertEquals(account.id, entries.getRunning()!!.entry.accountId)
        assertTrue(preferences.onboardingCompleted.first())
        // New rows still get fresh IDs after a restore.
        assertTrue(catalog.saveAccount(null, "Neu", color = 0, activityIds = emptySet()) is SaveResult.Saved)
    }

    @Test
    fun aBackupOfRealData_survivesTheFileFormat() = runTest {
        catalog.createInitialAccounts(listOf("7b"))
        entries.start(ComboKey(catalog.activeRegularAccounts().first().id, null), now = 0)
        val backup = backups.create(now = 1, appVersion = "test")

        val bytes = ByteArrayOutputStream().also { writeBackup(backup, it) }.toByteArray()

        assertEquals(BackupReadResult.Ok(backup), readBackup(ByteArrayInputStream(bytes)))
    }
}
