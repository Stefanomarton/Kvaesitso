package de.mm20.launcher2.ticktick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class TickTickDatesTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun serverFieldsRoundTrip() {
        val timed = dueMillis("2026-10-19", "09:30", rome)!!
        assertEquals(1792395000000L, timed)
        assertEquals("2026-10-19" to "09:30", dueFields(timed, false, rome))
        val allDay = dueMillis("2026-10-19", "", rome)!!
        assertEquals("2026-10-19" to "", dueFields(allDay, true, rome))
        assertEquals("" to "", dueFields(null, true, rome))
    }

    @Test
    fun noOrBadDate() {
        assertNull(dueMillis("", "", rome))
        assertNull(dueMillis("garbage", "09:00", rome))
    }
}
