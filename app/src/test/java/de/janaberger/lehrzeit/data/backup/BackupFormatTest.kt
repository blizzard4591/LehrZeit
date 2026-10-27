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
        activities = listOf(
            BackupActivity(1, "General", color = -1, isGeneral = true, archived = false, sortOrder = 0),
            BackupActivity(2, "Korrekturen", color = -2, isGeneral = false, perClass = true, archived = false, sortOrder = 1),
        ),
        classes = listOf(BackupClass(10, "7b", archived = false, sortOrder = 0)),
        favorites = listOf(BackupFavorite(5, activityId = 2, classId = 10, sortOrder = 0)),
        entries = listOf(
            BackupEntry(100, activityId = 2, classId = 10, startUtc = 0, endUtc = 60_000, note = "Klausur „2“", createdAt = 0, updatedAt = 0),
            BackupEntry(101, activityId = 1, startUtc = 120_000, createdAt = 0, updatedAt = 0),
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
        val version = "\"version\": $BACKUP_VERSION,"
        val withExtraField = jsonText.replaceFirst(version, "$version \"futureField\": {\"a\": 1},")
        assertEquals(BackupReadResult.Ok(backup), readBackup(zipWith("backup.json", withExtraField)))
    }

    @Test
    fun backupsFromTheTestBuilds_areTooOld() {
        // Version 1 stored account › activity, before 0.1.0.
        assertEquals(BackupReadResult.TooOld, readBackup(zipWith("backup.json", """{"format":"$BACKUP_FORMAT","version":1,"accounts":[]}""")))
    }

    @Test
    fun inconsistentContent_isDamaged() {
        val damaged = listOf(
            backup.copy(activities = backup.activities.map { it.copy(isGeneral = false) }), // no General
            backup.copy(favorites = listOf(BackupFavorite(5, activityId = 2, classId = 99, sortOrder = 0))), // unknown class
            backup.copy(entries = backup.entries + BackupEntry(102, activityId = 99, startUtc = 0, endUtc = 1, createdAt = 0, updatedAt = 0)),
            backup.copy(entries = backup.entries + BackupEntry(102, activityId = 2, startUtc = 200_000, createdAt = 0, updatedAt = 0)), // two running
            backup.copy(entries = listOf(BackupEntry(103, activityId = 2, startUtc = 10, endUtc = 10, createdAt = 0, updatedAt = 0))),
        )
        damaged.forEach { assertEquals(BackupReadResult.Damaged, roundTrip(it)) }
        assertEquals(BackupReadResult.Damaged, readBackup(zipWith("backup.json", """{"format":"$BACKUP_FORMAT","version":$BACKUP_VERSION,"activities":"x"}""")))
    }
}
