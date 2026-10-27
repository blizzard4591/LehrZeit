// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import java.net.URI
import java.net.URISyntaxException

/**
 * The https URL of a timetable link as typed or pasted, or null if it isn't one. Whitespace is
 * dropped, since a link can't contain any, e.g. line breaks from a link wrapped in an email.
 * Calendar subscription links often start with webcal://, which is fetched over https.
 */
fun normalizeTimetableUrl(input: String): String? {
    val compact = input.filterNot { it.isWhitespace() }
    val url = if (compact.startsWith("webcal://", ignoreCase = true)) "https://" + compact.substring("webcal://".length) else compact
    val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        return null
    }
    return url.takeIf { uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() }
}

/** The part of a timetable link that is safe to show, e.g. "schule.webuntis.com"; the rest may contain a token. */
fun timetableHost(url: String): String = try {
    URI(url).host.orEmpty()
} catch (e: URISyntaxException) {
    ""
}
