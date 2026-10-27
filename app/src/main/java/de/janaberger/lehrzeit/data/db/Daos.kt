// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.janaberger.lehrzeit.data.ComboKey
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY isGeneral DESC, sortOrder, id")
    fun observeAll(): Flow<List<WorkActivity>>

    /** Non-archived activities except General. */
    @Query("SELECT * FROM activity WHERE archived = 0 AND isGeneral = 0 ORDER BY sortOrder, id")
    suspend fun getActiveRegular(): List<WorkActivity>

    @Query("SELECT * FROM activity WHERE id = :id")
    suspend fun get(id: Long): WorkActivity?

    @Query("SELECT * FROM activity WHERE isGeneral = 1 LIMIT 1")
    suspend fun getGeneral(): WorkActivity?

    @Query("SELECT COUNT(*) FROM activity WHERE isGeneral = 0")
    suspend fun countRegular(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM activity")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(activity: WorkActivity): Long

    @Update
    suspend fun update(activity: WorkActivity)

    @Delete
    suspend fun delete(activity: WorkActivity)

    @Query("UPDATE activity SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)
}

@Dao
interface ClassDao {
    @Query("SELECT * FROM school_class ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<SchoolClass>>

    @Query("SELECT * FROM school_class WHERE archived = 0 ORDER BY sortOrder, id")
    suspend fun getActive(): List<SchoolClass>

    @Query("SELECT * FROM school_class WHERE id = :id")
    suspend fun get(id: Long): SchoolClass?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM school_class")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(schoolClass: SchoolClass): Long

    @Update
    suspend fun update(schoolClass: SchoolClass)

    @Delete
    suspend fun delete(schoolClass: SchoolClass)

    @Query("UPDATE school_class SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)
}

@Dao
interface EntryDao {
    @Insert
    suspend fun insert(entry: Entry): Long

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("SELECT COUNT(*) FROM entry WHERE activityId = :activityId")
    suspend fun countForActivity(activityId: Long): Int

    @Query("SELECT COUNT(*) FROM entry WHERE classId = :classId")
    suspend fun countForClass(classId: Long): Int

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun get(id: Long): Entry?

    @Query("SELECT MIN(startUtc) FROM entry")
    fun observeEarliestStart(): Flow<Long?>

    /** Entries overlapping [fromUtc, toUtc), including a running one. */
    @Query("SELECT * FROM entry WHERE startUtc < :toUtc AND (endUtc IS NULL OR endUtc > :fromUtc) ORDER BY startUtc")
    suspend fun getOverlapping(fromUtc: Long, toUtc: Long): List<Entry>

    /** Finished entries that start in [fromUtc, toUtc), oldest first. */
    @Query("SELECT * FROM entry WHERE endUtc IS NOT NULL AND startUtc >= :fromUtc AND startUtc < :toUtc ORDER BY startUtc")
    fun observeFinishedStartingIn(fromUtc: Long, toUtc: Long): Flow<List<Entry>>

    @Query("SELECT * FROM entry WHERE endUtc IS NULL LIMIT 1")
    suspend fun getRunning(): Entry?

    @Transaction
    @Query("SELECT * FROM entry WHERE endUtc IS NULL LIMIT 1")
    suspend fun getRunningWithCombo(): EntryWithCombo?

    @Transaction
    @Query("SELECT * FROM entry WHERE endUtc IS NULL LIMIT 1")
    fun observeRunningWithCombo(): Flow<EntryWithCombo?>

    /** The finished entry that ended last at or before [utc], i.e. the one before a running entry. */
    @Query("SELECT * FROM entry WHERE endUtc IS NOT NULL AND endUtc <= :utc ORDER BY endUtc DESC LIMIT 1")
    suspend fun getLatestEndingAtOrBefore(utc: Long): Entry?

    /** The finished entry that ended last before the running one started; null while nothing runs. */
    @Query(
        """
        SELECT * FROM entry
        WHERE endUtc IS NOT NULL AND endUtc <= (SELECT startUtc FROM entry WHERE endUtc IS NULL)
        ORDER BY endUtc DESC LIMIT 1
        """,
    )
    fun observeBeforeRunning(): Flow<Entry?>

    @Query("UPDATE entry SET note = :note, updatedAt = :now WHERE id = :id")
    suspend fun setNote(id: Long, note: String, now: Long)

    /** Entries overlapping [fromUtc, toUtc), including a running one. */
    @Query("SELECT * FROM entry WHERE startUtc < :toUtc AND (endUtc IS NULL OR endUtc > :fromUtc) ORDER BY startUtc")
    fun observeOverlapping(fromUtc: Long, toUtc: Long): Flow<List<Entry>>

    /** Combinations used since [sinceUtc], most entries first, ties broken by most recent use. */
    @Query(
        """
        SELECT activityId, classId FROM entry
        WHERE startUtc >= :sinceUtc
        GROUP BY activityId, classId
        ORDER BY COUNT(*) DESC, MAX(startUtc) DESC
        """
    )
    fun observeMostUsed(sinceUtc: Long): Flow<List<ComboKey>>
}

/** Whole-table access for backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM activity")
    suspend fun activities(): List<WorkActivity>

    @Query("SELECT * FROM school_class")
    suspend fun classes(): List<SchoolClass>

    @Query("SELECT * FROM favorite")
    suspend fun favorites(): List<Favorite>

    @Query("SELECT * FROM entry")
    suspend fun entries(): List<Entry>

    @Query("DELETE FROM entry")
    suspend fun deleteEntries()

    @Query("DELETE FROM favorite")
    suspend fun deleteFavorites()

    @Query("DELETE FROM school_class")
    suspend fun deleteClasses()

    @Query("DELETE FROM activity")
    suspend fun deleteActivities()

    @Insert
    suspend fun insertActivities(activities: List<WorkActivity>)

    @Insert
    suspend fun insertClasses(classes: List<SchoolClass>)

    @Insert
    suspend fun insertFavorites(favorites: List<Favorite>)

    @Insert
    suspend fun insertEntries(entries: List<Entry>)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Favorite>>

    @Query("SELECT COUNT(*) FROM favorite WHERE activityId = :activityId AND classId IS :classId")
    suspend fun count(activityId: Long, classId: Long?): Int

    @Query("DELETE FROM favorite WHERE activityId = :activityId AND classId IS :classId")
    suspend fun delete(activityId: Long, classId: Long?)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM favorite")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(favorite: Favorite)
}
