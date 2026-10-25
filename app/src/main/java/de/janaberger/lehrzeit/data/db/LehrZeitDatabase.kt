// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Account::class, WorkActivity::class, AccountActivity::class, Favorite::class, Entry::class],
    version = 1,
)
abstract class LehrZeitDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun activityDao(): ActivityDao
    abstract fun accountActivityDao(): AccountActivityDao
    abstract fun entryDao(): EntryDao

    companion object {
        fun build(context: Context): LehrZeitDatabase =
            Room.databaseBuilder(context, LehrZeitDatabase::class.java, "lehrzeit.db").build()
    }
}
