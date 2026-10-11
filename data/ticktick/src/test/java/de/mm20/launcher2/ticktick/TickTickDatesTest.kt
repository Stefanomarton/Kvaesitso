package de.mm20.launcher2.ticktick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TickTickDatesTest {
    @Test
    fun parsesBothFormats() {
        assertEquals(1573614000000L, parseTickTickDate("2019-11-13T03:00:00+0000"))
        assertEquals(1573614000123L, parseTickTickDate("2019-11-13T03:00:00.123+0000"))
        assertNull(parseTickTickDate(null))
        assertNull(parseTickTickDate("garbage"))
    }

    @Test
    fun formatRoundTrips() {
        assertEquals("2019-11-13T03:00:00+0000", formatTickTickDate(1573614000000L))
    }

    @Test
    fun allDayDateUsesTaskZone() {
        // All-day task due 2019-11-14 in Los Angeles is stored as 08:00 UTC
        val millis = parseTickTickDate("2019-11-14T08:00:00+0000")!!
        assertEquals(LocalDate.of(2019, 11, 14), localDateIn(millis, "America/Los_Angeles"))
        assertEquals(LocalDate.of(2019, 11, 14), localDateIn(millis, null, ZoneId.of("Europe/Rome")))
    }

    @Test
    fun stamps() {
        assertEquals(20260407, stampOf(LocalDate.of(2026, 4, 7)))
        assertEquals(LocalDate.of(2026, 4, 7), dateOfStamp(20260407))
    }
}
