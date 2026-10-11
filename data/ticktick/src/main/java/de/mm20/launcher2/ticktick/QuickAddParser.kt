package de.mm20.launcher2.ticktick

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class QuickAdd(
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val tags: List<String>,
    /** TickTick priority: 0, 1, 3 or 5 */
    val priority: Int,
)

private val weekdays = DayOfWeek.entries.associateBy { it.name.lowercase() } +
        DayOfWeek.entries.associateBy { it.name.lowercase().take(3) }
private val time12 = Regex("""(\d{1,2})(?::(\d{2}))?(am|pm)""")
private val time24 = Regex("""(\d{1,2}):(\d{2})""")

/**
 * English-only parser for "buy milk tomorrow 5pm #home !3".
 * Understands today/tomorrow, [next] weekday, in N days/weeks, 5pm, 5:30pm, 17:00, #tag, !1-3.
 */
fun parseQuickAdd(text: String, today: LocalDate): QuickAdd {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val keep = BooleanArray(words.size) { true }
    var date: LocalDate? = null
    var time: LocalTime? = null
    val tags = mutableListOf<String>()
    var priority = 0

    fun consume(vararg idx: Int) {
        idx.forEach { keep[it] = false }
        // swallow a preceding preposition
        val prev = idx.min() - 1
        if (prev >= 0 && keep[prev] && words[prev].lowercase() in setOf("on", "at", "by")) keep[prev] = false
    }

    var i = 0
    while (i < words.size) {
        val w = words[i].lowercase()
        val next = words.getOrNull(i + 1)?.lowercase()
        when {
            w.length > 1 && w.startsWith("#") -> { tags += words[i].drop(1); keep[i] = false }
            w in setOf("!1", "!2", "!3") -> { priority = mapOf("!1" to 1, "!2" to 3, "!3" to 5)[w]!!; keep[i] = false }
            date == null && w == "today" -> { date = today; consume(i) }
            date == null && (w == "tomorrow" || w == "tmr") -> { date = today.plusDays(1); consume(i) }
            date == null && w == "next" && next in weekdays -> {
                date = today.with(TemporalAdjusters.next(weekdays[next]!!))
                consume(i, i + 1); i++
            }
            date == null && w in weekdays -> {
                date = today.with(TemporalAdjusters.nextOrSame(weekdays[w]!!)); consume(i)
            }
            date == null && w == "in" && next?.toLongOrNull() != null &&
                    words.getOrNull(i + 2)?.lowercase()?.trimEnd('s') in setOf("day", "week") -> {
                val n = next.toLong()
                date = if (words[i + 2].lowercase().startsWith("week")) today.plusWeeks(n) else today.plusDays(n)
                consume(i, i + 1, i + 2); i += 2
            }
            time == null && time12.matches(w) -> {
                val m = time12.matchEntire(w)!!
                val h = m.groupValues[1].toInt() % 12 + if (m.groupValues[3] == "pm") 12 else 0
                time = LocalTime.of(h, m.groupValues[2].ifEmpty { "0" }.toInt()); consume(i)
            }
            time == null && time24.matches(w) -> {
                val m = time24.matchEntire(w)!!
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                if (h < 24 && min < 60) { time = LocalTime.of(h, min); consume(i) }
            }
        }
        i++
    }
    if (time != null && date == null) date = today

    return QuickAdd(
        title = words.filterIndexed { idx, _ -> keep[idx] }.joinToString(" "),
        date = date,
        time = time,
        tags = tags,
        priority = priority,
    )
}
