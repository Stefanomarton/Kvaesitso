package de.mm20.launcher2.tasksapp

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Tasks stores local calendar text ("2026-10-19", "09:30"); the launcher keeps epoch millis in this device's zone.
 * An all-day task is local midnight.
 */
fun dueMillis(date: String, time: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    val at = runCatching { LocalTime.parse(time) }.getOrNull() ?: LocalTime.MIDNIGHT
    return day.atTime(at).atZone(zone).toInstant().toEpochMilli()
}

/** due_date and due_time for the server; "" for no date / all day. */
fun dueFields(millis: Long?, isAllDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
    val at = millis?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: return "" to ""
    return at.toLocalDate().toString() to if (isAllDay) "" else at.toLocalTime().withSecond(0).withNano(0).toString()
}

fun localDateIn(millis: Long, timeZone: String?, fallback: ZoneId = ZoneId.systemDefault()): LocalDate {
    val zone = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
