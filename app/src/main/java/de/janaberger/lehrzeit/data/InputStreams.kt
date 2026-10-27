// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads up to [limit] + 1 bytes, so a caller can tell that the content was too large. */
fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() <= limit) {
        val read = read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
