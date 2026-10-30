// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.backup

import de.janaberger.lehrzeit.data.readAtMost
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val BACKUP_FORMAT = "de.janaberger.lehrzeit.backup"

/**
 * Raise when the content changes; older versions must stay readable (migrate them in [readBackup]).
 * Version 1 came from test builds before 0.1.0, which booked on account › activity; it can't be restored.
 * Version 2 also had pinned tiles ("favorites"); they are skipped like any unknown field.
 */
const val BACKUP_VERSION = 3

private const val BACKUP_ENTRY = "backup.json"

/** Far more than years of entries; protects against huge or malicious files. */
private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024

/**
 * Everything the app stores, independent of the database schema: a zip with one JSON file.
 * IDs are kept so links between the parts stay intact.
 */
@Serializable
data class Backup(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    val createdAtUtc: Long,
    val appVersion: String,
    val activities: List<BackupActivity>,
    val classes: List<BackupClass>,
    val entries: List<BackupEntry>,
    val settings: BackupSettings,
)

@Serializable
data class BackupActivity(
    val id: Long,
    val name: String,
    val color: Int,
    val isGeneral: Boolean,
    val perClass: Boolean = true,
    val archived: Boolean,
    val sortOrder: Int,
)

@Serializable
data class BackupClass(val id: Long, val name: String, val archived: Boolean, val sortOrder: Int)

@Serializable
data class BackupEntry(
    val id: Long,
    val activityId: Long,
    val classId: Long? = null,
    val startUtc: Long,
    val endUtc: Long? = null,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/** New settings get a default, so older backups without them stay readable. */
@Serializable
data class BackupSettings(
    val discardShortEntries: Boolean = true,
    val minEntrySeconds: Int = 60,
    val reminderEnabled: Boolean = true,
    val reminderHours: Int = 3,
)

sealed interface BackupReadResult {
    data class Ok(val backup: Backup) : BackupReadResult
    data object NotABackup : BackupReadResult
    data object TooNew : BackupReadResult
    data object TooOld : BackupReadResult
    data object Damaged : BackupReadResult
}

private val json = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun writeBackup(backup: Backup, out: OutputStream) {
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry(BACKUP_ENTRY))
        zip.write(json.encodeToString(Backup.serializer(), backup).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}

fun readBackup(input: InputStream): BackupReadResult {
    val text = try {
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == BACKUP_ENTRY }?.let { zip.readAtMost(MAX_BACKUP_BYTES) }
        }
    } catch (e: IOException) {
        null
    } ?: return BackupReadResult.NotABackup
    if (text.size > MAX_BACKUP_BYTES) return BackupReadResult.Damaged

    val root = try {
        json.parseToJsonElement(text.decodeToString()).jsonObject
    } catch (e: SerializationException) {
        return BackupReadResult.NotABackup
    } catch (e: IllegalArgumentException) {
        return BackupReadResult.NotABackup
    }
    if (root["format"]?.jsonPrimitive?.content != BACKUP_FORMAT) return BackupReadResult.NotABackup
    val version = root["version"]?.jsonPrimitive?.intOrNull ?: return BackupReadResult.Damaged
    if (version > BACKUP_VERSION) return BackupReadResult.TooNew
    if (version < 2) return BackupReadResult.TooOld

    val backup = try {
        json.decodeFromJsonElement<Backup>(root)
    } catch (e: SerializationException) {
        return BackupReadResult.Damaged
    } catch (e: IllegalArgumentException) {
        return BackupReadResult.Damaged
    }
    return if (backup.isConsistent()) BackupReadResult.Ok(backup) else BackupReadResult.Damaged
}

/** The database rules a restore relies on: one General, unique IDs, no dangling references, at most one running entry. */
internal fun Backup.isConsistent(): Boolean {
    val activityIds = activities.map { it.id }.toSet()
    val classIds = classes.map { it.id }.toSet()
    fun knownClass(id: Long?) = id == null || id in classIds
    return activities.count { it.isGeneral } == 1 &&
        activityIds.size == activities.size &&
        classIds.size == classes.size &&
        entries.map { it.id }.toSet().size == entries.size &&
        entries.all { it.activityId in activityIds && knownClass(it.classId) && (it.endUtc == null || it.endUtc > it.startUtc) } &&
        entries.count { it.endUtc == null } <= 1
}
