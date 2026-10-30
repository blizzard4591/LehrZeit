// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Top level of what time is booked on, from one shared catalog, e.g. "Correcting", or the permanent General. */
@Entity(tableName = "activity")
data class WorkActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Ignored for General, whose display name comes from string resources. */
    val name: String,
    /** ARGB. */
    val color: Int,
    val isGeneral: Boolean = false,
    /** Whether a class can be chosen under this activity. */
    val perClass: Boolean = true,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

/**
 * The optional second level: a class, course or AG, e.g. "7b".
 * Named SchoolClass to keep it apart from Kotlin's class keyword.
 */
@Entity(tableName = "school_class")
data class SchoolClass(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

/**
 * One booked time span. Times are UTC epoch milliseconds; [endUtc] is null while the entry is running.
 * Activities and classes with entries cannot be deleted (RESTRICT), only archived.
 */
@Entity(
    tableName = "entry",
    foreignKeys = [
        ForeignKey(
            entity = WorkActivity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SchoolClass::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("activityId"), Index("classId"), Index("startUtc")],
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val classId: Long?,
    val startUtc: Long,
    val endUtc: Long?,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

data class EntryWithCombo(
    @Embedded val entry: Entry,
    @Relation(parentColumn = "activityId", entityColumn = "id") val activity: WorkActivity,
    @Relation(parentColumn = "classId", entityColumn = "id") val schoolClass: SchoolClass?,
)

/** A lesson from the timetable link, kept locally. [uid] comes from the iCalendar file. */
@Entity(tableName = "lesson", indices = [Index("startUtc")])
data class Lesson(
    @PrimaryKey val uid: String,
    val startUtc: Long,
    val endUtc: Long,
    /** In WebUntis the subject, e.g. "BI" or "S1 G2". */
    val summary: String,
    /** In WebUntis the class and the teacher's abbreviation, e.g. "08.2 XYZ". */
    val description: String,
    /** The room. */
    val location: String,
)

data class LessonSummary(val count: Int, val firstStart: Long?, val lastStart: Long?)
