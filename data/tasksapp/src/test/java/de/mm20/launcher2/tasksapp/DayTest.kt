package de.mm20.launcher2.tasksapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayTest {
    private val zone = ZoneId.of("Europe/Rome")

    private fun task(id: String, due: String?, allDay: Boolean = false, parent: String = "", block: Boolean = false) =
        TasksAppTask(id, "p", null, null, id, null, null, due?.let { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() },
            allDay, null, 0, false, false, emptyList(), parent = parent, isBlock = block, durationMin = if (block) 90 else 0)

    @Test
    fun lateAnytimeThenTimedWithBlocksHoldingTheirTasks() {
        val tasks = listOf(
            task("eve", "2026-10-19T19:00:00Z"), task("blk", "2026-10-19T15:00:00Z", block = true),
            task("late", "2026-10-18T08:00:00Z"), task("free", "2026-10-18T22:00:00Z", allDay = true),
            task("in", null, parent = "blk"), task("morning", "2026-10-19T05:45:00Z"), task("tomorrow", "2026-10-20T06:00:00Z"),
        )
        val d = tasks.day(LocalDate.of(2026, 10, 19), zone)
        assertEquals(listOf("late"), d.late.map { it.id })
        assertEquals(listOf("free"), d.anytime.map { it.id })
        assertEquals(listOf("morning", "blk", "eve"), d.timed.map { it.id })
        assertEquals(listOf("in"), tasks.inside("blk").map { it.id })
    }
}
