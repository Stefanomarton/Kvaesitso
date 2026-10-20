package de.mm20.launcher2.tasksapp

import java.time.LocalDate
import java.time.ZoneId

data class TasksAppProject(
    val id: String,
    val name: String,
    val color: Int?,
)

data class TasksAppTask(
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
    val parent: String = "",
    /** a block: a stretch of time for a kind of work, its tasks (subtasks) inside */
    val isBlock: Boolean = false,
    val durationMin: Int = 0,
)

data class TasksAppSyncState(
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
fun List<TasksAppTask>.dueOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<TasksAppTask> =
    filter { task ->
        val due = task.dueDate ?: return@filter false
        !task.completed && localDateIn(due, if (task.isAllDay) task.timeZone else null, zone) == day
    }.sortedWith(compareBy({ it.isAllDay }, { if (it.isAllDay) 0L else it.dueDate }, { -it.priority }))

/** Oggi as a day, as the Tasks app shows it: the late tasks, today's without a time, the timed ones in order. A subtask
    sits under its task when that is here; a block holds its tasks whatever their date. */
data class TasksDay(val late: List<TasksAppTask>, val anytime: List<TasksAppTask>, val timed: List<TasksAppTask>)

fun List<TasksAppTask>.day(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): TasksDay {
    val open = filter { !it.completed }
    val dayOf = { t: TasksAppTask -> t.dueDate?.let { localDateIn(it, if (t.isAllDay) t.timeZone else null, zone) } }
    val late = open.filter { dayOf(it)?.isBefore(today) == true }
    val todays = open.filter { dayOf(it) == today }
    val here = (late + todays).map { it.id }.toSet()
    val blocks = open.filter { it.isBlock }.map { it.id }.toSet()
    val top = { t: TasksAppTask -> t.parent.isEmpty() || (t.parent !in here && t.parent !in blocks) }
    return TasksDay(
        late.filter(top).sortedBy { it.dueDate },
        todays.filter { top(it) && it.isAllDay }.sortedByDescending { it.priority },
        todays.filter { top(it) && !it.isAllDay }.sortedBy { it.dueDate },
    )
}

/** The open tasks inside task [id]. */
fun List<TasksAppTask>.inside(id: String) = filter { !it.completed && it.parent == id }
