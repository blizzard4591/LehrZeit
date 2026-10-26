// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val BACKUP_FORMAT = "de.janaberger.lehrzeit.backup"

/** Raise when the content changes; older versions must stay readable (migrate them in [readBackup]). */
const val BACKUP_VERSION = 1

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
    val accounts: List<BackupAccount>,
    val activities: List<BackupActivity>,
    val accountActivities: List<BackupLink>,
    val favorites: List<BackupFavorite>,
    val entries: List<BackupEntry>,
    val settings: BackupSettings,
)

@Serializable
data class BackupAccount(val id: Long, val name: String, val color: Int, val isGeneral: Boolean, val archived: Boolean, val sortOrder: Int)

@Serializable
data class BackupActivity(val id: Long, val name: String, val archived: Boolean, val sortOrder: Int)

@Serializable
data class BackupLink(val accountId: Long, val activityId: Long)

@Serializable
data class BackupFavorite(val id: Long, val accountId: Long, val activityId: Long? = null, val sortOrder: Int)

@Serializable
data class BackupEntry(
    val id: Long,
    val accountId: Long,
    val activityId: Long? = null,
    val startUtc: Long,
    val endUtc: Long? = null,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupSettings(val discardShortEntries: Boolean = true, val minEntrySeconds: Int = 60)

sealed interface BackupReadResult {
    data class Ok(val backup: Backup) : BackupReadResult
    data object NotABackup : BackupReadResult
    data object TooNew : BackupReadResult
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

    val backup = try {
        json.decodeFromJsonElement<Backup>(root)
    } catch (e: SerializationException) {
        return BackupReadResult.Damaged
    } catch (e: IllegalArgumentException) {
        return BackupReadResult.Damaged
    }
    return if (backup.isConsistent()) BackupReadResult.Ok(backup) else BackupReadResult.Damaged
}

/** The database rules a restore relies on: one General, unique IDs, no dangling links, at most one running entry. */
internal fun Backup.isConsistent(): Boolean {
    val accountIds = accounts.map { it.id }.toSet()
    val activityIds = activities.map { it.id }.toSet()
    fun knownActivity(id: Long?) = id == null || id in activityIds
    return accounts.count { it.isGeneral } == 1 &&
        accountIds.size == accounts.size &&
        activityIds.size == activities.size &&
        favorites.map { it.id }.toSet().size == favorites.size &&
        entries.map { it.id }.toSet().size == entries.size &&
        accountActivities.all { it.accountId in accountIds && it.activityId in activityIds } &&
        favorites.all { it.accountId in accountIds && knownActivity(it.activityId) } &&
        entries.all { it.accountId in accountIds && knownActivity(it.activityId) && (it.endUtc == null || it.endUtc > it.startUtc) } &&
        entries.count { it.endUtc == null } <= 1
}

/** Reads up to [limit] + 1 bytes, so a caller can tell that the content was too large. */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() <= limit) {
        val read = read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
