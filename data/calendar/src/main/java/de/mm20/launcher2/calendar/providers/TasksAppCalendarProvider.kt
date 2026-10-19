package de.mm20.launcher2.calendar.providers

import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.calendar.CalendarListType
import de.mm20.launcher2.tasksapp.TasksAppProject
import de.mm20.launcher2.tasksapp.TasksAppTask

/**
 * Filters a snapshot of the local Tasks cache; never touches the network.
 * ponytail: tasks without a due date are not shown, CalendarEvent needs an end time.
 */
internal class TasksAppCalendarProvider(
    private val tasks: List<TasksAppTask>,
    private val projects: List<TasksAppProject>,
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
            val event = task.toCalendarEvent() ?: return@mapNotNull null
            // Overdue open tasks are always included so search can find them.
            if (event.endTime > to) return@mapNotNull null
            if (event.endTime < from && task.completed) return@mapNotNull null
            event
        }
    }

    override suspend fun getCalendarLists(): List<CalendarList> {
        return projects.map {
            CalendarList(
                id = "$namespace:${it.id}",
                name = it.name,
                owner = "Tasks",
                color = it.color ?: 0,
                types = listOf(CalendarListType.Tasks),
                providerId = namespace,
            )
        }
    }
}
