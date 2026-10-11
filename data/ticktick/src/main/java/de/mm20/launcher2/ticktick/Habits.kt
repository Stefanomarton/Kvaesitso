package de.mm20.launcher2.ticktick

import java.time.DayOfWeek
import java.time.LocalDate

private val byDayCodes = mapOf(
    "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
    "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY,
    "SU" to DayOfWeek.SUNDAY,
)

/**
 * Only BYDAY is evaluated; everything else (daily, "N times per week", intervals) counts as due.
 * ponytail: INTERVAL>1 ignored, add when someone uses every-other-day habits.
 */
fun isDueOn(rrule: String?, date: LocalDate): Boolean {
    val byDay = rrule
        ?.removePrefix("RRULE:")
        ?.split(";")
        ?.firstOrNull { it.startsWith("BYDAY=") }
        ?.removePrefix("BYDAY=")
        ?.split(",")
        ?.mapNotNull { byDayCodes[it.takeLast(2)] }
        ?: return true
    return date.dayOfWeek in byDay
}

/**
 * Consecutive completed due days ending today (or yesterday, if today isn't done yet).
 */
fun streak(doneStamps: Set<Int>, today: LocalDate, rrule: String?): Int {
    var count = 0
    var day = today
    if (stampOf(today) !in doneStamps) day = day.minusDays(1)
    // ponytail: bounded by the check-in window that was synced
    repeat(366) {
        if (isDueOn(rrule, day)) {
            if (stampOf(day) in doneStamps) count++ else return count
        }
        day = day.minusDays(1)
    }
    return count
}
