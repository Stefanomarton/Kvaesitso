package de.mm20.launcher2.ticktick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitsTest {
    private val monday = LocalDate.of(2026, 10, 12)

    @Test
    fun dueRules() {
        val mwf = "RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR"
        assertTrue(isDueOn(mwf, monday))
        assertFalse(isDueOn(mwf, monday.plusDays(1)))
        assertTrue(isDueOn("RRULE:FREQ=DAILY;INTERVAL=1", monday))
        assertTrue(isDueOn(null, monday))
        // "N times per week" has no BYDAY: due every day
        assertTrue(isDueOn("RRULE:FREQ=WEEKLY;TT_TIMES=3", monday))
    }

    @Test
    fun dailyStreak() {
        val done = (1..5).map { stampOf(monday.minusDays(it.toLong())) }.toSet()
        // today not done yet does not break the streak
        assertEquals(5, streak(done, monday, null))
        assertEquals(6, streak(done + stampOf(monday), monday, null))
        // gap breaks it
        assertEquals(2, streak(done - stampOf(monday.minusDays(3)), monday, null))
    }

    @Test
    fun weeklyStreakSkipsNonDueDays() {
        val mwf = "RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR"
        // Fri 9, Wed 7, Mon 5 done; today (Mon 12) done
        val done = setOf(20261009, 20261007, 20261005, 20261012)
        assertEquals(4, streak(done, monday, mwf))
    }
}
