// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.janaberger.lehrzeit.data.ComboKey
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query(
        """
        SELECT account.*, (
            SELECT COUNT(*) FROM account_activity AS link
            JOIN activity ON activity.id = link.activityId
            WHERE link.accountId = account.id AND activity.archived = 0
        ) AS activityCount
        FROM account
        ORDER BY isGeneral DESC, sortOrder, id
        """
    )
    fun observeAllWithActivityCount(): Flow<List<AccountWithCount>>

    @Query("SELECT * FROM account ORDER BY isGeneral DESC, sortOrder, id")
    fun observeAll(): Flow<List<Account>>

    /** Non-archived accounts except General. */
    @Query("SELECT * FROM account WHERE archived = 0 AND isGeneral = 0 ORDER BY sortOrder, id")
    suspend fun getActiveRegular(): List<Account>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun get(id: Long): Account?

    @Query("SELECT * FROM account WHERE isGeneral = 1 LIMIT 1")
    suspend fun getGeneral(): Account?

    @Query("SELECT COUNT(*) FROM account WHERE isGeneral = 0")
    suspend fun countRegular(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM account")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("UPDATE account SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)
}

@Dao
interface ActivityDao {
    @Query(
        """
        SELECT activity.*, (
            SELECT COUNT(*) FROM account_activity AS link
            JOIN account ON account.id = link.accountId
            WHERE link.activityId = activity.id AND account.archived = 0
        ) AS accountCount
        FROM activity
        ORDER BY sortOrder, id
        """
    )
    fun observeAllWithAccountCount(): Flow<List<ActivityWithCount>>

    @Query("SELECT * FROM activity ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<WorkActivity>>

    @Query("SELECT * FROM activity WHERE archived = 0 ORDER BY sortOrder, id")
    suspend fun getActive(): List<WorkActivity>

    @Query("SELECT * FROM activity WHERE id = :id")
    suspend fun get(id: Long): WorkActivity?

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
interface AccountActivityDao {
    @Query("SELECT * FROM account_activity")
    fun observeAll(): Flow<List<AccountActivity>>

    @Query("SELECT activityId FROM account_activity WHERE accountId = :accountId")
    suspend fun activityIdsFor(accountId: Long): List<Long>

    @Query("SELECT accountId FROM account_activity WHERE activityId = :activityId")
    suspend fun accountIdsFor(activityId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<AccountActivity>)

    @Delete
    suspend fun deleteAll(links: List<AccountActivity>)
}

@Dao
interface EntryDao {
    @Insert
    suspend fun insert(entry: Entry): Long

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("SELECT COUNT(*) FROM entry WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM entry WHERE activityId = :activityId")
    suspend fun countForActivity(activityId: Long): Int

    @Query("SELECT * FROM entry WHERE endUtc IS NULL LIMIT 1")
    suspend fun getRunning(): Entry?

    @Transaction
    @Query("SELECT * FROM entry WHERE endUtc IS NULL LIMIT 1")
    suspend fun getRunningWithCombo(): EntryWithCombo?

    @Transaction
    @Query("SELECT * FROM entry WHERE endUtc IS NULL LIMIT 1")
    fun observeRunningWithCombo(): Flow<EntryWithCombo?>

    /** End of the latest finished entry that ended at or before [utc], i.e. the one before a running entry. */
    @Query("SELECT MAX(endUtc) FROM entry WHERE endUtc IS NOT NULL AND endUtc <= :utc")
    suspend fun latestEndAtOrBefore(utc: Long): Long?

    /** Entries overlapping [fromUtc, toUtc), including a running one. */
    @Query("SELECT * FROM entry WHERE startUtc < :toUtc AND (endUtc IS NULL OR endUtc > :fromUtc) ORDER BY startUtc")
    fun observeOverlapping(fromUtc: Long, toUtc: Long): Flow<List<Entry>>

    /** Combinations used since [sinceUtc], most entries first, ties broken by most recent use. */
    @Query(
        """
        SELECT accountId, activityId FROM entry
        WHERE startUtc >= :sinceUtc
        GROUP BY accountId, activityId
        ORDER BY COUNT(*) DESC, MAX(startUtc) DESC
        """
    )
    fun observeMostUsed(sinceUtc: Long): Flow<List<ComboKey>>
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Favorite>>

    @Query("SELECT COUNT(*) FROM favorite WHERE accountId = :accountId AND activityId IS :activityId")
    suspend fun count(accountId: Long, activityId: Long?): Int

    @Query("DELETE FROM favorite WHERE accountId = :accountId AND activityId IS :activityId")
    suspend fun delete(accountId: Long, activityId: Long?)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM favorite")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(favorite: Favorite)
}
