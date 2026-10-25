// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import de.janaberger.lehrzeit.data.db.Account
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.db.WorkActivity

/** What time is booked on: an account plus an optional activity. Also used as a Room result row. */
data class ComboKey(val accountId: Long, val activityId: Long?)

val Entry.comboKey: ComboKey get() = ComboKey(accountId, activityId)

data class Combo(val account: Account, val activity: WorkActivity?) {
    val key: ComboKey get() = ComboKey(account.id, activity?.id)

    /** E.g. "7b › Correcting", or just "7b". [generalName] is General's localized name. */
    fun label(generalName: String): String =
        listOfNotNull(if (account.isGeneral) generalName else account.name, activity?.name).joinToString(" › ")
}

/** All accounts, activities and their links at one point in time. */
data class CatalogSnapshot(
    /** General first, then by sort order. Includes archived accounts. */
    val accounts: List<Account>,
    /** By sort order. Includes archived activities. */
    val activities: List<WorkActivity>,
    /** Which activity each account offers; [ComboKey.activityId] is never null here. */
    val links: Set<ComboKey>,
) {
    private val accountsById = accounts.associateBy { it.id }
    private val activitiesById = activities.associateBy { it.id }

    val general: Account? get() = accounts.firstOrNull { it.isGeneral }

    /** Non-archived accounts, General first. */
    val activeAccounts: List<Account> get() = accounts.filter { !it.archived }

    fun combo(key: ComboKey): Combo? {
        val account = accountsById[key.accountId] ?: return null
        val activity = key.activityId?.let { activitiesById[it] ?: return null }
        return Combo(account, activity)
    }

    /** Non-archived activities the account offers, in catalog order. */
    fun activeActivitiesFor(accountId: Long): List<WorkActivity> =
        activities.filter { !it.archived && ComboKey(accountId, it.id) in links }

    /** True if time can be booked on [key] right now: nothing archived and the account offers the activity. */
    fun isBookable(key: ComboKey): Boolean {
        val account = accountsById[key.accountId] ?: return false
        if (account.archived) return false
        val activityId = key.activityId ?: return true
        val activity = activitiesById[activityId] ?: return false
        return !account.isGeneral && !activity.archived && key in links
    }
}
