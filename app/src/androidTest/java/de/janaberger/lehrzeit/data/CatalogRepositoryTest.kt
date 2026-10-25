// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogRepositoryTest {
    private lateinit var db: LehrZeitDatabase
    private lateinit var catalog: CatalogRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LehrZeitDatabase::class.java)
            .build()
        catalog = CatalogRepository(
            db = db,
            starterActivityNames = { listOf("Teaching", "Correcting", "Exams") },
            generalDisplayName = { "General" },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun accountsByName() = catalog.observeAccounts().first().associateBy { it.account.name }

    private suspend fun activityIds(vararg names: String): Set<Long> {
        val byName = catalog.observeActivities().first().associateBy { it.activity.name }
        return names.map { byName.getValue(it).activity.id }.toSet()
    }

    private fun savedId(result: SaveResult): Long = (result as SaveResult.Saved).id

    private suspend fun addEntry(accountId: Long, activityId: Long?) {
        db.entryDao().insert(
            Entry(accountId = accountId, activityId = activityId, startUtc = 0, endUtc = 1, createdAt = 0, updatedAt = 0),
        )
    }

    @Test
    fun seedIfNeeded_createsGeneralAndStarterActivitiesOnlyOnce() = runTest {
        catalog.seedIfNeeded()
        catalog.seedIfNeeded()

        val accounts = catalog.observeAccounts().first()
        assertEquals(1, accounts.size)
        assertTrue(accounts.single().account.isGeneral)
        assertEquals(listOf("Teaching", "Correcting", "Exams"), catalog.observeActivities().first().map { it.activity.name })
    }

    @Test
    fun createInitialAccounts_enablesAllActivitiesAndSkipsInvalidNames() = runTest {
        catalog.createInitialAccounts(listOf("7b", " 9a  Mathe ", "7B", "general", "  "))

        val accounts = catalog.observeAccounts().first()
        assertEquals(listOf("General", "7b", "9a Mathe"), accounts.map { it.account.name })
        assertEquals(listOf(0, 3, 3), accounts.map { it.activityCount })
    }

    @Test
    fun saveAccount_newAccountGetsOnlyTheCheckedActivities() = runTest {
        catalog.seedIfNeeded()
        val id = savedId(catalog.saveAccount(null, "7b", color = 1, activityIds = activityIds("Exams")))

        val details = catalog.getAccountDetails(id)!!
        assertEquals(activityIds("Exams"), details.activityIds)
    }

    @Test
    fun saveAccount_rejectsEmptyAndTakenNamesIgnoringCase() = runTest {
        catalog.seedIfNeeded()
        catalog.saveAccount(null, "Äpfel 7b", color = 1, activityIds = emptySet())

        assertEquals(SaveResult.NameEmpty, catalog.saveAccount(null, "   ", color = 1, activityIds = emptySet()))
        assertEquals(SaveResult.NameTaken, catalog.saveAccount(null, "äpfel  7B", color = 1, activityIds = emptySet()))
        assertEquals(SaveResult.NameTaken, catalog.saveAccount(null, "GENERAL", color = 1, activityIds = emptySet()))
    }

    @Test
    fun archivedNamesCanBeReused_butBlockRestoringTheDuplicate() = runTest {
        catalog.seedIfNeeded()
        val oldId = savedId(catalog.saveAccount(null, "7b", color = 1, activityIds = emptySet()))
        catalog.saveAccount(oldId, "7b", color = 1, activityIds = emptySet(), archived = true)

        assertTrue(catalog.saveAccount(null, "7b", color = 2, activityIds = emptySet()) is SaveResult.Saved)
        assertEquals(
            SaveResult.NameTaken,
            catalog.saveAccount(oldId, "7b", color = 1, activityIds = emptySet(), archived = false),
        )
        assertTrue(catalog.saveAccount(oldId, "7b 2025", color = 1, activityIds = emptySet(), archived = false) is SaveResult.Saved)
    }

    @Test
    fun restoringFromArchive_movesAccountToTheEnd() = runTest {
        catalog.seedIfNeeded()
        val first = savedId(catalog.saveAccount(null, "A", color = 1, activityIds = emptySet()))
        catalog.saveAccount(null, "B", color = 1, activityIds = emptySet())
        catalog.saveAccount(first, "A", color = 1, activityIds = emptySet(), archived = true)
        catalog.saveAccount(first, "A", color = 1, activityIds = emptySet(), archived = false)

        assertEquals(listOf("General", "B", "A"), catalog.observeAccounts().first().map { it.account.name })
    }

    @Test
    fun saveAccount_keepsLinksToArchivedActivities() = runTest {
        catalog.seedIfNeeded()
        val all = activityIds("Teaching", "Correcting", "Exams")
        val accountId = savedId(catalog.saveAccount(null, "7b", color = 1, activityIds = all))
        val examsId = activityIds("Exams").single()
        catalog.saveActivity(examsId, "Exams", accountIds = setOf(accountId), archived = true)

        // The account editor only shows active activities, so it saves without Exams.
        catalog.saveAccount(accountId, "7b", color = 1, activityIds = activityIds("Teaching"))

        assertEquals(activityIds("Teaching", "Exams"), catalog.getAccountDetails(accountId)!!.activityIds)
    }

    @Test
    fun general_onlyItsColorCanChange() = runTest {
        catalog.seedIfNeeded()
        val general = accountsByName().getValue("General").account

        val result = catalog.saveAccount(general.id, "Renamed", color = 42, activityIds = activityIds("Exams"), archived = true)

        assertEquals(SaveResult.Saved(general.id), result)
        val details = catalog.getAccountDetails(general.id)!!
        assertEquals("General", details.account.name)
        assertEquals(42, details.account.color)
        assertFalse(details.account.archived)
        assertTrue(details.activityIds.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun general_cannotBeDeleted() = runTest {
        catalog.seedIfNeeded()
        catalog.deleteAccount(accountsByName().getValue("General").account.id)
    }

    @Test
    fun deleteAccount_onlyWithoutEntries() = runTest {
        catalog.seedIfNeeded()
        val used = savedId(catalog.saveAccount(null, "Used", color = 1, activityIds = activityIds("Exams")))
        val unused = savedId(catalog.saveAccount(null, "Unused", color = 1, activityIds = activityIds("Exams")))
        addEntry(used, activityId = null)

        assertEquals(DeleteResult.HAS_ENTRIES, catalog.deleteAccount(used))
        assertEquals(DeleteResult.DELETED, catalog.deleteAccount(unused))
        assertEquals(setOf("General", "Used"), accountsByName().keys)
        // The deleted account's links are gone, so Exams is offered by one account only.
        assertEquals(1, catalog.observeActivities().first().single { it.activity.name == "Exams" }.accountCount)
    }

    @Test
    fun deleteActivity_onlyWithoutEntries() = runTest {
        catalog.seedIfNeeded()
        val general = accountsByName().getValue("General").account
        val examsId = activityIds("Exams").single()
        val teachingId = activityIds("Teaching").single()
        addEntry(general.id, examsId)

        assertEquals(DeleteResult.HAS_ENTRIES, catalog.deleteActivity(examsId))
        assertEquals(DeleteResult.DELETED, catalog.deleteActivity(teachingId))
        assertEquals(listOf("Correcting", "Exams"), catalog.observeActivities().first().map { it.activity.name })
    }

    @Test
    fun saveActivity_linksCheckedAccountsAndRejectsDuplicateNames() = runTest {
        catalog.seedIfNeeded()
        val a = savedId(catalog.saveAccount(null, "7b", color = 1, activityIds = emptySet()))
        catalog.saveAccount(null, "8c", color = 1, activityIds = emptySet())

        val id = savedId(catalog.saveActivity(null, "Class trip", accountIds = setOf(a)))

        assertEquals(setOf(a), catalog.getActivityDetails(id)!!.accountIds)
        assertEquals(SaveResult.NameTaken, catalog.saveActivity(null, "class TRIP", accountIds = emptySet()))
    }

    @Test
    fun reorder_persistsNewOrder() = runTest {
        catalog.seedIfNeeded()
        val a = savedId(catalog.saveAccount(null, "A", color = 1, activityIds = emptySet()))
        val b = savedId(catalog.saveAccount(null, "B", color = 1, activityIds = emptySet()))
        val c = savedId(catalog.saveAccount(null, "C", color = 1, activityIds = emptySet()))

        catalog.reorderAccounts(listOf(c, a, b))
        catalog.reorderActivities(activityIds("Exams", "Teaching", "Correcting").toList())

        assertEquals(listOf("General", "C", "A", "B"), catalog.observeAccounts().first().map { it.account.name })
        assertEquals(listOf("Exams", "Teaching", "Correcting"), catalog.observeActivities().first().map { it.activity.name })
    }
}
