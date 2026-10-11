package de.mm20.launcher2.ticktick

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val parser = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]Z")
private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

fun parseTickTickDate(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value, parser).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

fun formatTickTickDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).format(formatter)

/**
 * TickTick stores all-day dates as local midnight of the task's time zone.
 */
fun localDateIn(millis: Long, timeZone: String?, fallback: ZoneId = ZoneId.systemDefault()): LocalDate {
    val zone = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

fun stampOf(date: LocalDate): Int = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth

fun dateOfStamp(stamp: Int): LocalDate = LocalDate.of(stamp / 10000, stamp / 100 % 100, stamp % 100)
