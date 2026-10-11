package de.mm20.launcher2.calendar.providers

import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.calendar.CalendarListType
import de.mm20.launcher2.ticktick.TickTickProject
import de.mm20.launcher2.ticktick.TickTickTask
import de.mm20.launcher2.ticktick.localDateIn
import java.time.LocalDate
import java.time.ZoneId

/**
 * Filters a snapshot of the local TickTick cache; never touches the network.
 * ponytail: tasks without a due date are not shown, CalendarEvent needs an end time.
 */
internal class TickTickCalendarProvider(
    private val tasks: List<TickTickTask>,
    private val projects: List<TickTickProject>,
) : CalendarProvider {

    override val namespace: String = "ticktick"

    override suspend fun search(
        query: String?,
        from: Long,
        to: Long,
        excludedCalendars: List<String>,
        excludeAllDayEvents: Boolean,
        allowNetwork: Boolean
    ): List<CalendarEvent> {
        return tasks.mapNotNull { task ->
            if (task.projectId in excludedCalendars) return@mapNotNull null
            if (query != null && !task.title.contains(query, ignoreCase = true)) return@mapNotNull null
            if (excludeAllDayEvents && task.isAllDay) return@mapNotNull null
            val event = task.toEvent() ?: return@mapNotNull null
            // Overdue open tasks are always included; the widget pins them to today.
            if (event.endTime > to) return@mapNotNull null
            if (event.endTime < from && (task.completed || query != null)) return@mapNotNull null
            event
        }
    }

    override suspend fun getCalendarLists(): List<CalendarList> {
        return projects.map {
            CalendarList(
                id = "$namespace:${it.id}",
                name = it.name,
                owner = "TickTick",
                color = it.color ?: 0,
                types = listOf(CalendarListType.Tasks),
                providerId = namespace,
            )
        }
    }

    private fun TickTickTask.toEvent(): TickTickCalendarEvent? {
        val due = dueDate ?: return null
        val end = if (isAllDay) {
            localDateIn(due, timeZone).endOfDay()
        } else due
        return TickTickCalendarEvent(
            id = id,
            projectId = projectId,
            label = title,
            color = projectColor,
            startTime = null,
            endTime = end,
            allDay = isAllDay,
            isCompleted = completed,
            description = content,
            calendarName = projectName,
            priority = priority,
            tags = tags,
        )
    }

    private fun LocalDate.endOfDay(): Long =
        plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
}
