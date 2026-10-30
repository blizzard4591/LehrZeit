// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LehrZeitDatabase::class.java)

    @Test
    fun from1To2_keepsTheData_andAddsTheLessonTable() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO activity (id, name, color, isGeneral, perClass, archived, sortOrder) VALUES (1, 'General', 0, 1, 1, 0, 0)")
            db.execSQL("INSERT INTO entry (id, activityId, classId, startUtc, endUtc, note, createdAt, updatedAt) VALUES (1, 1, NULL, 0, 60000, 'kept', 0, 0)")
        }

        // Also checks that the migrated schema matches 2.json.
        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT note FROM entry").use { cursor ->
                cursor.moveToFirst()
                assertEquals("kept", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM lesson").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun from2To3_keepsTheData_andDropsThePinnedTiles() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL("INSERT INTO activity (id, name, color, isGeneral, perClass, archived, sortOrder) VALUES (1, 'General', 0, 1, 1, 0, 0)")
            db.execSQL("INSERT INTO school_class (id, name, archived, sortOrder) VALUES (1, '7b', 0, 0)")
            db.execSQL("INSERT INTO favorite (id, activityId, classId, sortOrder) VALUES (1, 1, 1, 0)")
            db.execSQL("INSERT INTO entry (id, activityId, classId, startUtc, endUtc, note, createdAt, updatedAt) VALUES (1, 1, 1, 0, 60000, 'kept', 0, 0)")
        }

        // Also checks that the migrated schema matches 3.json.
        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT note, classId FROM entry").use { cursor ->
                cursor.moveToFirst()
                assertEquals("kept", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM sqlite_master WHERE name = 'favorite'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
