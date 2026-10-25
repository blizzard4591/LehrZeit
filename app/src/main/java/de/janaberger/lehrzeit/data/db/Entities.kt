// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Top level of what time is booked on: a class, an area, or the permanent General account. */
@Entity(tableName = "account")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Ignored for General, whose display name comes from string resources. */
    val name: String,
    /** ARGB. */
    val color: Int,
    val isGeneral: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

/**
 * An entry in the shared activity catalog, e.g. "Correcting".
 * Named WorkActivity to keep it apart from android.app.Activity.
 */
@Entity(tableName = "activity")
data class WorkActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

/** An activity that an account offers. */
@Entity(
    tableName = "account_activity",
    primaryKeys = ["accountId", "activityId"],
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkActivity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("activityId")],
)
data class AccountActivity(
    val accountId: Long,
    val activityId: Long,
)

/** A pinned home-screen tile: an account plus an optional activity. */
@Entity(
    tableName = "favorite",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkActivity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("activityId")],
)
data class Favorite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val activityId: Long?,
    val sortOrder: Int = 0,
)

/**
 * One booked time span. Times are UTC epoch milliseconds; [endUtc] is null while the entry is running.
 * Accounts and activities with entries cannot be deleted (RESTRICT), only archived.
 */
@Entity(
    tableName = "entry",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = WorkActivity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("accountId"), Index("activityId"), Index("startUtc")],
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val activityId: Long?,
    val startUtc: Long,
    val endUtc: Long?,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

data class AccountWithCount(
    @Embedded val account: Account,
    /** Number of non-archived activities the account offers. */
    val activityCount: Int,
)

data class ActivityWithCount(
    @Embedded val activity: WorkActivity,
    /** Number of non-archived accounts that offer the activity. */
    val accountCount: Int,
)
