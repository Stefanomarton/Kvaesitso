package de.mm20.launcher2.tasksapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DueOnTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 10, 19)

    private fun task(id: String, due: String?, allDay: Boolean = false, completed: Boolean = false, priority: Int = 0) =
        TasksAppTask(
            id, "p", null, null, id, null, null, due?.let { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() },
            allDay, "Europe/Rome", priority, completed, false, emptyList(),
        )

    @Test
    fun onlyOpenTodayTasksTimedFirstAllDayLast() {
        val tasks = listOf(
            task("allDayLow", "2026-10-18T22:00:00Z", allDay = true, priority = 1),
            task("overdue", "2026-10-18T08:00:00Z"),
            task("late", "2026-10-19T16:00:00Z"),
            task("done", "2026-10-19T09:00:00Z", completed = true),
            task("allDayHigh", "2026-10-18T22:00:00Z", allDay = true, priority = 3),
            task("early", "2026-10-19T06:00:00Z"),
            task("tomorrow", "2026-10-20T06:00:00Z"),
            task("noDate", null),
        )
        assertEquals(
            listOf("early", "late", "allDayHigh", "allDayLow"),
            tasks.dueOn(today, zone).map { it.id },
        )
    }
}
