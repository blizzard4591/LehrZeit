// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneId

/** Emits today's bounds, and again right after each midnight. */
fun currentDayFlow(): Flow<DayBounds> = flow {
    while (true) {
        val zone = ZoneId.systemDefault()
        val bounds = dayBounds(LocalDate.now(zone), zone)
        emit(bounds)
        delay((bounds.endUtc - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
    }
}
