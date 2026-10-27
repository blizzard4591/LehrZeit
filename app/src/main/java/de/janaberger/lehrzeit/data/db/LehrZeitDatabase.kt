// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WorkActivity::class, SchoolClass::class, Favorite::class, Entry::class, Lesson::class],
    version = 2,
    autoMigrations = [
        // 2 adds the lessons of the timetable link.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class LehrZeitDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun classDao(): ClassDao
    abstract fun entryDao(): EntryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun backupDao(): BackupDao
    abstract fun lessonDao(): LessonDao

    companion object {
        fun build(context: Context): LehrZeitDatabase =
            Room.databaseBuilder(context, LehrZeitDatabase::class.java, "lehrzeit.db").build()
    }
}
