// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.db.SchoolClass
import de.janaberger.lehrzeit.data.db.WorkActivity

/** What time is booked on: an activity plus an optional class. */
data class ComboKey(val activityId: Long, val classId: Long?)

val Entry.comboKey: ComboKey get() = ComboKey(activityId, classId)

data class Combo(val activity: WorkActivity, val schoolClass: SchoolClass?) {
    val key: ComboKey get() = ComboKey(activity.id, schoolClass?.id)

    /** E.g. "Correcting › 7b", or just "Correcting". [generalName] is General's localized name. */
    fun label(generalName: String): String =
        listOfNotNull(if (activity.isGeneral) generalName else activity.name, schoolClass?.name).joinToString(" › ")
}

/** All activities and classes at one point in time. */
data class CatalogSnapshot(
    /** General first, then by sort order. Includes archived activities. */
    val activities: List<WorkActivity>,
    /** By sort order. Includes archived classes. */
    val classes: List<SchoolClass>,
) {
    private val activitiesById = activities.associateBy { it.id }
    private val classesById = classes.associateBy { it.id }

    val general: WorkActivity? get() = activities.firstOrNull { it.isGeneral }

    /** Non-archived activities, General first. */
    val activeActivities: List<WorkActivity> get() = activities.filter { !it.archived }

    fun combo(key: ComboKey): Combo? {
        val activity = activitiesById[key.activityId] ?: return null
        val schoolClass = key.classId?.let { classesById[it] ?: return null }
        return Combo(activity, schoolClass)
    }

    /** Non-archived classes. */
    val activeClasses: List<SchoolClass> get() = classes.filter { !it.archived }

    /**
     * The classes that can be chosen under the activity: all non-archived ones if it's booked per class, else none.
     * Tapping an activity with classes asks for one; without, it is booked on its own straight away.
     */
    fun activeClassesFor(activityId: Long): List<SchoolClass> =
        if (activitiesById[activityId]?.perClass == true) activeClasses else emptyList()
}
