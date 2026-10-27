// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import androidx.room.withTransaction
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import de.janaberger.lehrzeit.data.db.SchoolClass
import de.janaberger.lehrzeit.data.db.WorkActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

const val MAX_NAME_LENGTH = 40

/** Stored name of General; the UI always shows the localized name instead. */
const val GENERAL_STORED_NAME = "General"

/** Trims and collapses inner whitespace, so " 7b  Mathe " and "7b Mathe" count as the same name. */
fun normalizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data object NameEmpty : SaveResult
    data object NameTaken : SaveResult
}

enum class DeleteResult { DELETED, HAS_ENTRIES }

data class ActivityDetails(val activity: WorkActivity, val entryCount: Int)

data class ClassDetails(val schoolClass: SchoolClass, val entryCount: Int)

/** An activity of the starter catalog, added on first launch. */
data class StarterActivity(val name: String, val perClass: Boolean)

/**
 * The shared activity catalog and the classes.
 *
 * Rules enforced here:
 * - Names are unique among non-archived items, ignoring case. Archived items don't block a name,
 *   so next school year's "7b" can coexist with last year's archived one.
 * - Neither an activity nor a class can be called like General.
 * - General can't be renamed, archived or deleted.
 * - Items with entries can't be deleted, only archived.
 */
class CatalogRepository(
    private val db: LehrZeitDatabase,
    private val starterActivities: () -> List<StarterActivity>,
    private val generalDisplayName: () -> String,
) {
    private val activities = db.activityDao()
    private val classes = db.classDao()
    private val entries = db.entryDao()

    /** General first, then all other activities (active and archived) in their sort order. */
    fun observeActivities(): Flow<List<WorkActivity>> = activities.observeAll()

    /** All classes (active and archived) in their sort order. */
    fun observeClasses(): Flow<List<SchoolClass>> = classes.observeAll()

    fun observeSnapshot(): Flow<CatalogSnapshot> = combine(activities.observeAll(), classes.observeAll(), ::CatalogSnapshot)

    suspend fun getCombo(key: ComboKey): Combo? {
        val activity = activities.get(key.activityId) ?: return null
        val schoolClass = key.classId?.let { classes.get(it) ?: return null }
        return Combo(activity, schoolClass)
    }

    suspend fun activeClasses(): List<SchoolClass> = classes.getActive()

    /** Non-archived activities except General. */
    suspend fun activeRegularActivities(): List<WorkActivity> = activities.getActiveRegular()

    /** Creates General and the starter activities on first launch. Safe to call repeatedly. */
    suspend fun seedIfNeeded() {
        db.withTransaction {
            if (activities.getGeneral() != null) return@withTransaction
            activities.insert(WorkActivity(name = GENERAL_STORED_NAME, color = ActivityColors.GENERAL, isGeneral = true))
            starterActivities().forEachIndexed { index, starter ->
                activities.insert(
                    WorkActivity(
                        name = normalizeName(starter.name),
                        color = ActivityColors.forIndex(index),
                        perClass = starter.perClass,
                        sortOrder = index,
                    ),
                )
            }
        }
    }

    /** Welcome setup: creates one class per name. Empty and already-taken names are skipped. */
    suspend fun createInitialClasses(names: List<String>) {
        db.withTransaction {
            seedIfNeeded()
            for (raw in names) {
                val name = normalizeName(raw)
                if (name.isEmpty() || isClassNameTaken(name, exceptId = null)) continue
                classes.insert(SchoolClass(name = name, sortOrder = classes.maxSortOrder() + 1))
            }
        }
    }

    suspend fun nextActivityColor(): Int = ActivityColors.forIndex(activities.countRegular())

    suspend fun isActivityNameTaken(name: String, exceptId: Long?): Boolean {
        val normalized = normalizeName(name)
        if (normalized.equals(generalDisplayName(), ignoreCase = true)) return true
        return activities.getActiveRegular().any { it.id != exceptId && it.name.equals(normalized, ignoreCase = true) }
    }

    suspend fun getActivityDetails(id: Long): ActivityDetails? {
        val activity = activities.get(id) ?: return null
        return ActivityDetails(activity, entryCount = entries.countForActivity(id))
    }

    /** Creates ([id] == null) or updates an activity. For General only the color and [perClass] change. */
    suspend fun saveActivity(
        id: Long?,
        name: String,
        color: Int,
        perClass: Boolean,
        archived: Boolean = false,
    ): SaveResult = db.withTransaction {
        val existing = id?.let { activities.get(it) }
        if (existing != null && existing.isGeneral) {
            activities.update(existing.copy(color = color, perClass = perClass))
            return@withTransaction SaveResult.Saved(existing.id)
        }
        val normalized = normalizeName(name)
        if (normalized.isEmpty()) return@withTransaction SaveResult.NameEmpty
        if (!archived && isActivityNameTaken(normalized, exceptId = id)) return@withTransaction SaveResult.NameTaken

        val activityId = if (existing == null) {
            activities.insert(
                WorkActivity(
                    name = normalized,
                    color = color,
                    perClass = perClass,
                    archived = archived,
                    sortOrder = activities.maxSortOrder() + 1,
                ),
            )
        } else {
            // Coming back from the archive puts the activity at the end of the list.
            val sortOrder = if (existing.archived && !archived) activities.maxSortOrder() + 1 else existing.sortOrder
            activities.update(
                existing.copy(name = normalized, color = color, perClass = perClass, archived = archived, sortOrder = sortOrder),
            )
            existing.id
        }
        SaveResult.Saved(activityId)
    }

    suspend fun deleteActivity(id: Long): DeleteResult = db.withTransaction {
        val activity = activities.get(id) ?: return@withTransaction DeleteResult.DELETED
        require(!activity.isGeneral) { "General can't be deleted" }
        if (entries.countForActivity(id) > 0) return@withTransaction DeleteResult.HAS_ENTRIES
        activities.delete(activity)
        DeleteResult.DELETED
    }

    /** Stores the order of the active regular activities as shown in the list. */
    suspend fun reorderActivities(idsInOrder: List<Long>) {
        db.withTransaction {
            idsInOrder.forEachIndexed { index, id -> activities.setSortOrder(id, index) }
        }
    }

    suspend fun isClassNameTaken(name: String, exceptId: Long?): Boolean {
        val normalized = normalizeName(name)
        if (normalized.equals(generalDisplayName(), ignoreCase = true)) return true
        return classes.getActive().any { it.id != exceptId && it.name.equals(normalized, ignoreCase = true) }
    }

    suspend fun getClassDetails(id: Long): ClassDetails? {
        val schoolClass = classes.get(id) ?: return null
        return ClassDetails(schoolClass, entryCount = entries.countForClass(id))
    }

    /** Creates ([id] == null) or updates a class. */
    suspend fun saveClass(id: Long?, name: String, archived: Boolean = false): SaveResult = db.withTransaction {
        val existing = id?.let { classes.get(it) }
        val normalized = normalizeName(name)
        if (normalized.isEmpty()) return@withTransaction SaveResult.NameEmpty
        if (!archived && isClassNameTaken(normalized, exceptId = id)) return@withTransaction SaveResult.NameTaken

        val classId = if (existing == null) {
            classes.insert(SchoolClass(name = normalized, archived = archived, sortOrder = classes.maxSortOrder() + 1))
        } else {
            val sortOrder = if (existing.archived && !archived) classes.maxSortOrder() + 1 else existing.sortOrder
            classes.update(existing.copy(name = normalized, archived = archived, sortOrder = sortOrder))
            existing.id
        }
        SaveResult.Saved(classId)
    }

    suspend fun deleteClass(id: Long): DeleteResult = db.withTransaction {
        val schoolClass = classes.get(id) ?: return@withTransaction DeleteResult.DELETED
        if (entries.countForClass(id) > 0) return@withTransaction DeleteResult.HAS_ENTRIES
        classes.delete(schoolClass)
        DeleteResult.DELETED
    }

    /** Stores the order of the active classes as shown in the list. */
    suspend fun reorderClasses(idsInOrder: List<Long>) {
        db.withTransaction {
            idsInOrder.forEachIndexed { index, id -> classes.setSortOrder(id, index) }
        }
    }
}
