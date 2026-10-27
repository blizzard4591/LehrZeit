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
            starterActivities = {
                listOf(StarterActivity("Teaching", perClass = true), StarterActivity("Correcting", perClass = true), StarterActivity("Meetings", perClass = false))
            },
            generalDisplayName = { "General" },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun activitiesByName() = catalog.observeActivities().first().associateBy { it.name }

    private suspend fun classNames() = catalog.observeClasses().first().map { it.name }

    private suspend fun classId(name: String) = catalog.observeClasses().first().single { it.name == name }.id

    private fun savedId(result: SaveResult): Long = (result as SaveResult.Saved).id

    private suspend fun addEntry(activityId: Long, classId: Long?) {
        db.entryDao().insert(
            Entry(activityId = activityId, classId = classId, startUtc = 0, endUtc = 1, createdAt = 0, updatedAt = 0),
        )
    }

    @Test
    fun seedIfNeeded_createsGeneralAndStarterActivitiesOnlyOnce() = runTest {
        catalog.seedIfNeeded()
        catalog.seedIfNeeded()

        val activities = catalog.observeActivities().first()
        assertEquals(listOf("General", "Teaching", "Correcting", "Meetings"), activities.map { it.name })
        assertTrue(activities.first().isGeneral)
        assertEquals(listOf(true, true, true, false), activities.map { it.perClass })
        assertEquals("each its own color", 4, activities.map { it.color }.toSet().size)
        assertTrue(classNames().isEmpty())
    }

    @Test
    fun createInitialClasses_skipsInvalidNames() = runTest {
        catalog.createInitialClasses(listOf("7b", " 9a  Mathe ", "7B", "general", "  "))

        assertEquals(listOf("7b", "9a Mathe"), classNames())
        assertEquals("seeded on the way", 4, catalog.observeActivities().first().size)
    }

    @Test
    fun saveActivity_storesPerClass() = runTest {
        catalog.seedIfNeeded()
        val id = savedId(catalog.saveActivity(null, "Mentoring", color = 1, perClass = false))
        assertFalse(catalog.getActivityDetails(id)!!.activity.perClass)

        catalog.saveActivity(id, "Mentoring", color = 1, perClass = true)
        assertTrue(catalog.getActivityDetails(id)!!.activity.perClass)
    }

    @Test
    fun saveActivity_rejectsEmptyAndTakenNamesIgnoringCase() = runTest {
        catalog.seedIfNeeded()
        catalog.saveActivity(null, "Äpfel schälen", color = 1, perClass = true)

        assertEquals(SaveResult.NameEmpty, catalog.saveActivity(null, "   ", color = 1, perClass = true))
        assertEquals(SaveResult.NameTaken, catalog.saveActivity(null, "äpfel  SCHÄLEN", color = 1, perClass = true))
        assertEquals(SaveResult.NameTaken, catalog.saveActivity(null, "GENERAL", color = 1, perClass = true))
    }

    @Test
    fun archivedNamesCanBeReused_butBlockRestoringTheDuplicate() = runTest {
        val oldId = savedId(catalog.saveClass(null, "7b"))
        catalog.saveClass(oldId, "7b", archived = true)

        assertTrue(catalog.saveClass(null, "7b") is SaveResult.Saved)
        assertEquals(SaveResult.NameTaken, catalog.saveClass(oldId, "7b", archived = false))
        assertTrue(catalog.saveClass(oldId, "7b 2025", archived = false) is SaveResult.Saved)
    }

    @Test
    fun restoringFromArchive_movesActivityToTheEnd() = runTest {
        catalog.seedIfNeeded()
        val teaching = activitiesByName().getValue("Teaching")
        catalog.saveActivity(teaching.id, "Teaching", color = 1, perClass = true, archived = true)
        catalog.saveActivity(teaching.id, "Teaching", color = 1, perClass = true, archived = false)

        assertEquals(listOf("General", "Correcting", "Meetings", "Teaching"), catalog.observeActivities().first().map { it.name })
    }

    @Test
    fun general_onlyItsColorAndPerClassCanChange() = runTest {
        catalog.seedIfNeeded()
        val general = activitiesByName().getValue("General")

        val result = catalog.saveActivity(general.id, "Renamed", color = 42, perClass = false, archived = true)

        assertEquals(SaveResult.Saved(general.id), result)
        val saved = catalog.getActivityDetails(general.id)!!.activity
        assertEquals("General", saved.name)
        assertEquals(42, saved.color)
        assertFalse(saved.perClass)
        assertFalse(saved.archived)
    }

    @Test(expected = IllegalArgumentException::class)
    fun general_cannotBeDeleted() = runTest {
        catalog.seedIfNeeded()
        catalog.deleteActivity(activitiesByName().getValue("General").id)
    }

    @Test
    fun deleteActivity_onlyWithoutEntries() = runTest {
        catalog.seedIfNeeded()
        val used = savedId(catalog.saveActivity(null, "Used", color = 1, perClass = true))
        val unused = savedId(catalog.saveActivity(null, "Unused", color = 1, perClass = true))
        addEntry(used, classId = null)

        assertEquals(DeleteResult.HAS_ENTRIES, catalog.deleteActivity(used))
        assertEquals(DeleteResult.DELETED, catalog.deleteActivity(unused))
        assertEquals(setOf("General", "Teaching", "Correcting", "Meetings", "Used"), activitiesByName().keys)
    }

    @Test
    fun deleteClass_onlyWithoutEntries() = runTest {
        catalog.createInitialClasses(listOf("7b", "8a"))
        addEntry(activitiesByName().getValue("General").id, classId("7b"))

        assertEquals(DeleteResult.HAS_ENTRIES, catalog.deleteClass(classId("7b")))
        assertEquals(DeleteResult.DELETED, catalog.deleteClass(classId("8a")))
        assertEquals(listOf("7b"), classNames())
    }

    @Test
    fun saveClass_rejectsEmptyTakenAndGeneralNames() = runTest {
        catalog.saveClass(null, "Q1")

        assertEquals(SaveResult.NameEmpty, catalog.saveClass(null, "  "))
        assertEquals(SaveResult.NameTaken, catalog.saveClass(null, "q1"))
        assertEquals(SaveResult.NameTaken, catalog.saveClass(null, "General"))
    }

    @Test
    fun reorder_persistsNewOrder() = runTest {
        catalog.createInitialClasses(listOf("7b", "8a", "9c"))
        val activities = activitiesByName()

        catalog.reorderActivities(listOf("Meetings", "Teaching", "Correcting").map { activities.getValue(it).id })
        catalog.reorderClasses(listOf(classId("9c"), classId("7b"), classId("8a")))

        assertEquals(listOf("General", "Meetings", "Teaching", "Correcting"), catalog.observeActivities().first().map { it.name })
        assertEquals(listOf("9c", "7b", "8a"), classNames())
    }
}
