package de.mm20.launcher2.ticktick

import java.time.LocalDate
import java.time.ZoneId

data class TickTickProject(
    val id: String,
    val name: String,
    val color: Int?,
)

data class TickTickTask(
    val id: String,
    val projectId: String,
    val projectName: String?,
    val projectColor: Int?,
    val title: String,
    val content: String?,
    val startDate: Long?,
    val dueDate: Long?,
    val isAllDay: Boolean,
    val timeZone: String?,
    /** 0 none … 3 high */
    val priority: Int,
    val completed: Boolean,
    val isRecurring: Boolean,
    val tags: List<String>,
)

data class TickTickSyncState(
    val running: Boolean = false,
    val lastSync: Long = 0L,
    val error: String? = null,
)

data class TaskEdit(
    val title: String,
    val content: String?,
    val projectId: String,
    val dueDate: Long?,
    val isAllDay: Boolean,
    val priority: Int,
)

/**
 * Open tasks due on [day]: timed ones by time, then all-day ones by priority.
 */
fun List<TickTickTask>.dueOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<TickTickTask> =
    filter { task ->
        val due = task.dueDate ?: return@filter false
        !task.completed && localDateIn(due, if (task.isAllDay) task.timeZone else null, zone) == day
    }.sortedWith(compareBy({ it.isAllDay }, { if (it.isAllDay) 0L else it.dueDate }, { -it.priority }))
