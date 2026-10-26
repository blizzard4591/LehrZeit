// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** "Paused" means nothing runs, but [key] is remembered so Resume can start it again. */
data class PausedState(val key: ComboKey, val pausedAt: Long)

/** Entries shorter than [minSeconds] are discarded when a timer stops, e.g. after tapping the wrong tile. */
data class ShortEntrySettings(val enabled: Boolean = true, val minSeconds: Int = 60) {
    val minMillis: Long get() = if (enabled) minSeconds * 1_000L else 0L

    companion object {
        val CHOICES_SECONDS = listOf(30, 60, 120, 180, 300, 600)
    }
}

/** A reminder asks whether a timer is still right after it has been running for [hours]. */
data class ReminderSettings(val enabled: Boolean = true, val hours: Int = 3) {
    companion object {
        val CHOICES_HOURS = listOf(1, 2, 3, 4, 5, 6, 8, 10, 12)
    }
}

/**
 * The reminder bookkeeping for the running entry [entryId]: the hours count from [anchorUtc]
 * (the entry's start while null, otherwise the last "Keep going"), and [fired] is set once it was shown.
 */
data class ReminderState(val entryId: Long, val anchorUtc: Long? = null, val fired: Boolean = false)

class AppPreferences(private val context: Context) {
    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val pausedAccountKey = longPreferencesKey("paused_account")
    private val pausedActivityKey = longPreferencesKey("paused_activity")
    private val pausedAtKey = longPreferencesKey("paused_at")
    private val discardShortKey = booleanPreferencesKey("discard_short_entries")
    private val minEntrySecondsKey = intPreferencesKey("min_entry_seconds")
    private val lastBackupAtKey = longPreferencesKey("last_backup_at")
    private val reminderEnabledKey = booleanPreferencesKey("reminder_enabled")
    private val reminderHoursKey = intPreferencesKey("reminder_hours")
    private val reminderEntryKey = longPreferencesKey("reminder_entry")
    private val reminderAnchorKey = longPreferencesKey("reminder_anchor")
    private val reminderFiredKey = booleanPreferencesKey("reminder_fired")

    val reminder: Flow<ReminderSettings> = context.dataStore.data.map { prefs ->
        val defaults = ReminderSettings()
        ReminderSettings(
            enabled = prefs[reminderEnabledKey] ?: defaults.enabled,
            hours = prefs[reminderHoursKey] ?: defaults.hours,
        )
    }

    suspend fun setReminder(settings: ReminderSettings) {
        context.dataStore.edit {
            it[reminderEnabledKey] = settings.enabled
            it[reminderHoursKey] = settings.hours
        }
    }

    suspend fun getReminderState(): ReminderState? = context.dataStore.data.first().let { prefs ->
        val entryId = prefs[reminderEntryKey] ?: return@let null
        ReminderState(entryId, prefs[reminderAnchorKey], prefs[reminderFiredKey] ?: false)
    }

    suspend fun setReminderState(state: ReminderState?) {
        context.dataStore.edit { prefs ->
            prefs.remove(reminderEntryKey)
            prefs.remove(reminderAnchorKey)
            prefs.remove(reminderFiredKey)
            if (state != null) {
                prefs[reminderEntryKey] = state.entryId
                state.anchorUtc?.let { prefs[reminderAnchorKey] = it }
                prefs[reminderFiredKey] = state.fired
            }
        }
    }

    /** When the last backup file was written, UTC millis. */
    val lastBackupAt: Flow<Long?> = context.dataStore.data.map { it[lastBackupAtKey] }

    suspend fun setLastBackupAt(utc: Long) {
        context.dataStore.edit { it[lastBackupAtKey] = utc }
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[onboardingDoneKey] ?: false }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[onboardingDoneKey] = true }
    }

    val paused: Flow<PausedState?> = context.dataStore.data.map { prefs ->
        val accountId = prefs[pausedAccountKey] ?: return@map null
        PausedState(ComboKey(accountId, prefs[pausedActivityKey]), prefs[pausedAtKey] ?: 0L)
    }

    suspend fun getPaused(): PausedState? = paused.first()

    val shortEntries: Flow<ShortEntrySettings> = context.dataStore.data.map { prefs ->
        val defaults = ShortEntrySettings()
        ShortEntrySettings(
            enabled = prefs[discardShortKey] ?: defaults.enabled,
            minSeconds = prefs[minEntrySecondsKey] ?: defaults.minSeconds,
        )
    }

    suspend fun setShortEntries(settings: ShortEntrySettings) {
        context.dataStore.edit {
            it[discardShortKey] = settings.enabled
            it[minEntrySecondsKey] = settings.minSeconds
        }
    }

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
