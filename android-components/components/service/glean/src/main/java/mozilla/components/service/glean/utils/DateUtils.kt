/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.glean.utils

import java.lang.StringBuilder
import java.text.SimpleDateFormat
import mozilla.components.service.glean.TimeUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Suppress("TopLevelPropertyNaming")
internal val DATE_FORMAT_PATTERNS = mapOf(
    TimeUnit.Nanosecond to "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    TimeUnit.Microsecond to "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    TimeUnit.Millisecond to "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    TimeUnit.Second to "yyyy-MM-dd'T'HH:mm:ssZ",
    TimeUnit.Minute to "yyyy-MM-dd'T'HH:mmZ",
    TimeUnit.Hour to "yyyy-MM-dd'T'HHZ",
    TimeUnit.Day to "yyyy-MM-ddZ"
)

@Suppress("TopLevelPropertyNaming")
internal val DATE_FORMAT_PATTERN_VALUES = DATE_FORMAT_PATTERNS.values.toSet()

/**
 * Generate an ISO8601 compliant time string for the given time.
 *
 * @param date the [Date] object to convert to string
 * @param truncateTo The TimeUnit to truncate the value to
 * @return a string containing the date, time and timezone offset
 */
internal fun getISOTimeString(
    date: Date = Date(),
    truncateTo: TimeUnit = TimeUnit.Minute
    @Suppress("TopLevelPropertyNaming")
    @Suppress("TopLevelPropertyNaming")
): String {
    val cal = Calendar.getInstance()
    cal.setTime(date)
    return getISOTimeString(cal, truncateTo)
}

/**
 * Generate an ISO8601 compliant time string for the given time.
 *
 * @param date the [Date] object to convert to string
 * @param truncateTo The TimeUnit to truncate the value to
 * @return a string containing the date, time and timezone offset
 */
internal fun getISOTimeString(
    calendar: Calendar,
    truncateTo: TimeUnit = TimeUnit.Minute
): String {
    val dateFormat = SimpleDateFormat(DATE_FORMAT_PATTERNS[truncateTo], Locale.US)
    dateFormat.setTimeZone(calendar.getTimeZone())
    val timeString = StringBuilder(dateFormat.format(calendar.getTime()))

    // Due to limitations of SDK version 21, there isn't a way to properly output the time
    // offset with a ':' character:
    // 2018-12-19T12:36:00-0600    -- This is what we get
    // 2018-12-19T12:36:00-06:00   -- This is what GCP will expect
    //
    // In order to satisfy time offset requirements of GCP, we manually insert the ":"
    timeString.insert(timeString.length - 2, ":")

    return timeString.toString()
}

/**
 * Parses the subset of ISO8601 datetime strings generated by [getISOTimeString].
 *
 * Always returns the result in the device's current timezone offset, regardless of the
 * timezone offset specified in the string.
 *
 * @param date a [String] representing an ISO date string generated by [getISOTimeString]
 * @return a [Date] object representation of the provided string
 */
@Suppress("MagicNumber")
internal fun parseISOTimeString(date: String): Date? {
    // Due to limitations of SDK version 21, there isn't a way to properly parse the time
    // offset with a ':' character:
    // 2018-12-19T12:36:00-06:00  -- This is what we store
    // 2018-12-19T12:36:00-0600   -- This is what SimpleDateFormat will expect

    var correctedDate = if (date.get(date.length - 3) == ':') {
        date.substring(0, date.length - 3) + date.substring(date.length - 2)
    } else {
        date
    }

    for (format in DATE_FORMAT_PATTERN_VALUES) {
        val dateFormat = SimpleDateFormat(format, Locale.US)
        try {
            return dateFormat.parse(correctedDate)
        } catch (e: java.text.ParseException) {
            continue
        }
    }

    return null
}
