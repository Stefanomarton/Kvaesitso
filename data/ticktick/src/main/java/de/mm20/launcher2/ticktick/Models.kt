package de.mm20.launcher2.ticktick

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
    /** 0, 1, 3 or 5 */
    val priority: Int,
    val completed: Boolean,
    val isRecurring: Boolean,
    val tags: List<String>,
)

data class HabitDay(
    val stamp: Int,
    val due: Boolean,
    val done: Boolean,
)

data class HabitToday(
    val id: String,
    val name: String,
    val color: Int?,
    val isBoolean: Boolean,
    val goal: Double,
    val step: Double,
    val unit: String?,
    val valueToday: Double,
    val doneToday: Boolean,
    val dueToday: Boolean,
    val streak: Int,
    /** Oldest first, ends with today */
    val lastWeek: List<HabitDay>,
)

data class TickTickSyncState(
    val running: Boolean = false,
    val lastSync: Long = 0L,
    val tasksError: String? = null,
    val habitsError: String? = null,
)

data class TaskEdit(
    val title: String,
    val content: String?,
    val projectId: String,
    val dueDate: Long?,
    val isAllDay: Boolean,
    val priority: Int,
)
