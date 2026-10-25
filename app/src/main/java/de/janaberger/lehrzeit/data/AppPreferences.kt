// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** "Paused" means nothing runs, but [key] is remembered so Resume can start it again. */
data class PausedState(val key: ComboKey, val pausedAt: Long)

class AppPreferences(private val context: Context) {
    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val pausedAccountKey = longPreferencesKey("paused_account")
    private val pausedActivityKey = longPreferencesKey("paused_activity")
    private val pausedAtKey = longPreferencesKey("paused_at")

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[onboardingDoneKey] ?: false }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[onboardingDoneKey] = true }
    }

    val paused: Flow<PausedState?> = context.dataStore.data.map { prefs ->
        val accountId = prefs[pausedAccountKey] ?: return@map null
        PausedState(ComboKey(accountId, prefs[pausedActivityKey]), prefs[pausedAtKey] ?: 0L)
    }

    suspend fun getPaused(): PausedState? = paused.first()

    suspend fun setPaused(state: PausedState?) {
        context.dataStore.edit { prefs ->
            prefs.remove(pausedAccountKey)
            prefs.remove(pausedActivityKey)
            prefs.remove(pausedAtKey)
            if (state != null) {
                prefs[pausedAccountKey] = state.key.accountId
                state.key.activityId?.let { prefs[pausedActivityKey] = it }
                prefs[pausedAtKey] = state.pausedAt
            }
        }
    }
}
