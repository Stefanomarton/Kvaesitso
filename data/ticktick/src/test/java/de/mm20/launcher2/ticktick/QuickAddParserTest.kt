package de.mm20.launcher2.ticktick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickAddParserTest {
    // A Wednesday
    private val today = LocalDate.of(2026, 10, 14)

    @Test
    fun tomorrowWith12hTime() {
        val r = parseQuickAdd("buy milk tomorrow 5pm", today)
        assertEquals("buy milk", r.title)
        assertEquals(today.plusDays(1), r.date)
        assertEquals(LocalTime.of(17, 0), r.time)
    }

    @Test
    fun weekdayTagAndPriority() {
        val r = parseQuickAdd("call mom friday #family !3", today)
        assertEquals("call mom", r.title)
        assertEquals(LocalDate.of(2026, 10, 16), r.date)
        assertNull(r.time)
        assertEquals(listOf("family"), r.tags)
        assertEquals(5, r.priority)
    }

    @Test
    fun sameWeekdayIsToday_nextWeekdayIsNextWeek() {
        assertEquals(today, parseQuickAdd("x wednesday", today).date)
        assertEquals(today.plusDays(7), parseQuickAdd("x next wed", today).date)
    }

    @Test
    fun inNDaysWith24hTimeAndPrepositions() {
        val r = parseQuickAdd("report in 3 days at 17:30", today)
        assertEquals("report", r.title)
        assertEquals(today.plusDays(3), r.date)
        assertEquals(LocalTime.of(17, 30), r.time)
    }

    @Test
    fun timeOnlyMeansToday() {
        val r = parseQuickAdd("stand-up 9:15am", today)
        assertEquals(today, r.date)
        assertEquals(LocalTime.of(9, 15), r.time)
    }

    @Test
    fun plainTextHasNoDate() {
        val r = parseQuickAdd("just some text", today)
        assertEquals("just some text", r.title)
        assertNull(r.date)
        assertNull(r.time)
        assertEquals(0, r.priority)
    }

    @Test
    fun priorityLevels() {
        assertEquals(1, parseQuickAdd("a !1", today).priority)
        assertEquals(3, parseQuickAdd("a !2", today).priority)
    }

    @Test
    fun wordsInsideTitleAreKept() {
        // "on" not followed by a date stays in the title
        assertEquals("turn on lights", parseQuickAdd("turn on lights", today).title)
    }
}
