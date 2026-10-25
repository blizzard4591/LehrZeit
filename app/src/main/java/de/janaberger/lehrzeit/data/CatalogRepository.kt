// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import androidx.room.withTransaction
import de.janaberger.lehrzeit.data.db.Account
import de.janaberger.lehrzeit.data.db.AccountActivity
import de.janaberger.lehrzeit.data.db.AccountWithCount
import de.janaberger.lehrzeit.data.db.ActivityWithCount
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
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

data class AccountDetails(val account: Account, val activityIds: Set<Long>, val entryCount: Int)

data class ActivityDetails(val activity: WorkActivity, val accountIds: Set<Long>, val entryCount: Int)

/**
 * Accounts, the shared activity catalog and which activities each account offers.
 *
 * Rules enforced here:
 * - Names are unique among non-archived items, ignoring case. Archived items don't block a name,
 *   so next school year's "7b" can coexist with last year's archived one.
 * - General can't be renamed, archived or deleted, and offers no activities.
 * - Items with entries can't be deleted, only archived.
 */
class CatalogRepository(
    private val db: LehrZeitDatabase,
    private val starterActivityNames: () -> List<String>,
    private val generalDisplayName: () -> String,
) {
    private val accounts = db.accountDao()
    private val activities = db.activityDao()
    private val links = db.accountActivityDao()
    private val entries = db.entryDao()

    /** General first, then all other accounts (active and archived) in their sort order. */
    fun observeAccounts(): Flow<List<AccountWithCount>> = accounts.observeAllWithActivityCount()

    fun observeActivities(): Flow<List<ActivityWithCount>> = activities.observeAllWithAccountCount()

    fun observeSnapshot(): Flow<CatalogSnapshot> = combine(
        accounts.observeAll(),
        activities.observeAll(),
        links.observeAll(),
    ) { accounts, activities, links ->
        CatalogSnapshot(accounts, activities, links.mapTo(HashSet()) { ComboKey(it.accountId, it.activityId) })
    }

    suspend fun getCombo(key: ComboKey): Combo? {
        val account = accounts.get(key.accountId) ?: return null
        val activity = key.activityId?.let { activities.get(it) ?: return null }
        return Combo(account, activity)
    }

    suspend fun activeActivities(): List<WorkActivity> = activities.getActive()

    /** Non-archived accounts except General, i.e. those that can offer activities. */
    suspend fun activeRegularAccounts(): List<Account> = accounts.getActiveRegular()

    /** Creates General and the starter activities on first launch. Safe to call repeatedly. */
    suspend fun seedIfNeeded() {
        db.withTransaction {
            if (accounts.getGeneral() != null) return@withTransaction
            accounts.insert(Account(name = GENERAL_STORED_NAME, color = AccountColors.GENERAL, isGeneral = true))
            starterActivityNames().forEachIndexed { index, name ->
                activities.insert(WorkActivity(name = normalizeName(name), sortOrder = index))
            }
        }
    }

    /**
     * Welcome setup: creates one account per name, each offering every active activity.
     * Empty and already-taken names are skipped.
     */
    suspend fun createInitialAccounts(names: List<String>) {
        db.withTransaction {
            seedIfNeeded()
            val activityIds = activities.getActive().map { it.id }
            for (raw in names) {
                val name = normalizeName(raw)
                if (name.isEmpty() || isAccountNameTaken(name, exceptId = null)) continue
                val id = accounts.insert(
                    Account(
                        name = name,
                        color = nextAccountColor(),
                        sortOrder = accounts.maxSortOrder() + 1,
                    ),
                )
                links.insertAll(activityIds.map { AccountActivity(id, it) })
            }
        }
    }

    suspend fun nextAccountColor(): Int = AccountColors.forIndex(accounts.countRegular())

    suspend fun isAccountNameTaken(name: String, exceptId: Long?): Boolean {
        val normalized = normalizeName(name)
        if (normalized.equals(generalDisplayName(), ignoreCase = true)) return true
        return accounts.getActiveRegular().any { it.id != exceptId && it.name.equals(normalized, ignoreCase = true) }
    }

    suspend fun getAccountDetails(id: Long): AccountDetails? {
        val account = accounts.get(id) ?: return null
        return AccountDetails(
            account = account,
            activityIds = links.activityIdsFor(id).toSet(),
            entryCount = entries.countForAccount(id),
        )
    }

    /**
     * Creates ([id] == null) or updates an account. For General only the color changes.
     * [activityIds] decides the links to *active* activities; links to archived ones are kept,
     * so restoring an activity from the archive restores its accounts too.
     */
    suspend fun saveAccount(
        id: Long?,
        name: String,
        color: Int,
        activityIds: Set<Long>,
        archived: Boolean = false,
    ): SaveResult = db.withTransaction {
        val existing = id?.let { accounts.get(it) }
        if (existing != null && existing.isGeneral) {
            accounts.update(existing.copy(color = color))
            return@withTransaction SaveResult.Saved(existing.id)
        }
        val normalized = normalizeName(name)
        if (normalized.isEmpty()) return@withTransaction SaveResult.NameEmpty
        if (!archived && isAccountNameTaken(normalized, exceptId = id)) return@withTransaction SaveResult.NameTaken

        val accountId = if (existing == null) {
            accounts.insert(
                Account(name = normalized, color = color, archived = archived, sortOrder = accounts.maxSortOrder() + 1),
            )
        } else {
            // Coming back from the archive puts the account at the end of the list.
            val sortOrder = if (existing.archived && !archived) accounts.maxSortOrder() + 1 else existing.sortOrder
            accounts.update(existing.copy(name = normalized, color = color, archived = archived, sortOrder = sortOrder))
            existing.id
        }

        val activeIds = activities.getActive().map { it.id }.toSet()
        val current = links.activityIdsFor(accountId).toSet()
        links.deleteAll((current - activityIds).filter { it in activeIds }.map { AccountActivity(accountId, it) })
        links.insertAll((activityIds - current).filter { it in activeIds }.map { AccountActivity(accountId, it) })
        SaveResult.Saved(accountId)
    }

    suspend fun deleteAccount(id: Long): DeleteResult = db.withTransaction {
        val account = accounts.get(id) ?: return@withTransaction DeleteResult.DELETED
        require(!account.isGeneral) { "General can't be deleted" }
        if (entries.countForAccount(id) > 0) return@withTransaction DeleteResult.HAS_ENTRIES
        accounts.delete(account)
        DeleteResult.DELETED
    }

    /** Stores the order of the active regular accounts as shown in the list. */
    suspend fun reorderAccounts(idsInOrder: List<Long>) {
        db.withTransaction {
            idsInOrder.forEachIndexed { index, id -> accounts.setSortOrder(id, index) }
        }
    }

    suspend fun isActivityNameTaken(name: String, exceptId: Long?): Boolean {
        val normalized = normalizeName(name)
        return activities.getActive().any { it.id != exceptId && it.name.equals(normalized, ignoreCase = true) }
    }

    suspend fun getActivityDetails(id: Long): ActivityDetails? {
        val activity = activities.get(id) ?: return null
        return ActivityDetails(
            activity = activity,
            accountIds = links.accountIdsFor(id).toSet(),
            entryCount = entries.countForActivity(id),
        )
    }

    /**
     * Creates ([id] == null) or updates an activity. [accountIds] decides the links to active
     * regular accounts; links to archived accounts are kept.
     */
    suspend fun saveActivity(
        id: Long?,
        name: String,
        accountIds: Set<Long>,
        archived: Boolean = false,
    ): SaveResult = db.withTransaction {
        val existing = id?.let { activities.get(it) }
        val normalized = normalizeName(name)
        if (normalized.isEmpty()) return@withTransaction SaveResult.NameEmpty
        if (!archived && isActivityNameTaken(normalized, exceptId = id)) return@withTransaction SaveResult.NameTaken

        val activityId = if (existing == null) {
            activities.insert(
                WorkActivity(name = normalized, archived = archived, sortOrder = activities.maxSortOrder() + 1),
            )
        } else {
            val sortOrder = if (existing.archived && !archived) activities.maxSortOrder() + 1 else existing.sortOrder
            activities.update(existing.copy(name = normalized, archived = archived, sortOrder = sortOrder))
            existing.id
        }

        val activeIds = accounts.getActiveRegular().map { it.id }.toSet()
        val current = links.accountIdsFor(activityId).toSet()
        links.deleteAll((current - accountIds).filter { it in activeIds }.map { AccountActivity(it, activityId) })
        links.insertAll((accountIds - current).filter { it in activeIds }.map { AccountActivity(it, activityId) })
        SaveResult.Saved(activityId)
    }

    suspend fun deleteActivity(id: Long): DeleteResult = db.withTransaction {
        val activity = activities.get(id) ?: return@withTransaction DeleteResult.DELETED
        if (entries.countForActivity(id) > 0) return@withTransaction DeleteResult.HAS_ENTRIES
        activities.delete(activity)
        DeleteResult.DELETED
    }

    /** Stores the order of the active activities as shown in the list. */
    suspend fun reorderActivities(idsInOrder: List<Long>) {
        db.withTransaction {
            idsInOrder.forEachIndexed { index, id -> activities.setSortOrder(id, index) }
        }
    }
}
