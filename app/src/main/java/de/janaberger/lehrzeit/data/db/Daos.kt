// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Query("SELECT COUNT(*) FROM entry WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM entry WHERE activityId = :activityId")
    suspend fun countForActivity(activityId: Long): Int
}
