// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupFormatTest {
    private val backup = Backup(
        createdAtUtc = 1_000,
        appVersion = "0.1.0",
        accounts = listOf(
            BackupAccount(1, "General", color = -1, isGeneral = true, archived = false, sortOrder = 0),
            BackupAccount(2, "7b", color = -2, isGeneral = false, archived = false, sortOrder = 1),
        ),
        activities = listOf(BackupActivity(10, "Korrekturen", archived = false, sortOrder = 0)),
        accountActivities = listOf(BackupLink(2, 10)),
        favorites = listOf(BackupFavorite(5, accountId = 2, activityId = 10, sortOrder = 0)),
        entries = listOf(
            BackupEntry(100, accountId = 2, activityId = 10, startUtc = 0, endUtc = 60_000, note = "Klausur „2“", createdAt = 0, updatedAt = 0),
            BackupEntry(101, accountId = 1, startUtc = 120_000, createdAt = 0, updatedAt = 0),
        ),
        settings = BackupSettings(discardShortEntries = false, minEntrySeconds = 120),
    )

    private fun roundTrip(backup: Backup) =
        readBackup(ByteArrayInputStream(ByteArrayOutputStream().also { writeBackup(backup, it) }.toByteArray()))

    private fun zipWith(name: String, content: String): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        return ByteArrayInputStream(bytes)
    }

    @Test
    fun writeThenRead_returnsTheSameBackup() {
        assertEquals(BackupReadResult.Ok(backup), roundTrip(backup))
    }

    @Test
    fun otherFiles_areNotBackups() {
        assertEquals(BackupReadResult.NotABackup, readBackup(ByteArrayInputStream("Datum;Beginn\n".toByteArray())))
        assertEquals(BackupReadResult.NotABackup, readBackup(zipWith("content.xml", "<x/>")))
        assertEquals(BackupReadResult.NotABackup, readBackup(zipWith("backup.json", "not json")))
        assertEquals(BackupReadResult.NotABackup, readBackup(zipWith("backup.json", """{"format":"something.else","version":1}""")))
    }

    @Test
    fun newerVersions_areRejected_andUnknownFieldsIgnored() {
        assertEquals(
            BackupReadResult.TooNew,
            readBackup(zipWith("backup.json", """{"format":"$BACKUP_FORMAT","version":${BACKUP_VERSION + 1}}""")),
        )
        // A field added by a later version of the same format is skipped.
        val written = ByteArrayOutputStream().also { writeBackup(backup, it) }.toByteArray()
        val jsonText = ZipInputStream(ByteArrayInputStream(written)).use { it.nextEntry; it.readBytes().decodeToString() }
        val withExtraField = jsonText.replaceFirst("\"version\": 1,", "\"version\": 1, \"futureField\": {\"a\": 1},")
        assertEquals(BackupReadResult.Ok(backup), readBackup(zipWith("backup.json", withExtraField)))
    }

    @Test
    fun inconsistentContent_isDamaged() {
        val damaged = listOf(
            backup.copy(accounts = backup.accounts.map { it.copy(isGeneral = false) }), // no General
            backup.copy(accountActivities = listOf(BackupLink(2, 99))), // unknown activity
            backup.copy(entries = backup.entries + BackupEntry(102, accountId = 99, startUtc = 0, endUtc = 1, createdAt = 0, updatedAt = 0)),
            backup.copy(entries = backup.entries + BackupEntry(102, accountId = 2, startUtc = 200_000, createdAt = 0, updatedAt = 0)), // two running
            backup.copy(entries = listOf(BackupEntry(103, accountId = 2, startUtc = 10, endUtc = 10, createdAt = 0, updatedAt = 0))),
        )
        damaged.forEach { assertEquals(BackupReadResult.Damaged, roundTrip(it)) }
        assertEquals(BackupReadResult.Damaged, readBackup(zipWith("backup.json", """{"format":"$BACKUP_FORMAT","version":1,"accounts":"x"}""")))
    }
}
