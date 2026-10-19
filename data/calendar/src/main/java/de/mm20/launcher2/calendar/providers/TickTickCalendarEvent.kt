package de.mm20.launcher2.calendar.providers

import android.content.Context
import android.os.Bundle
import de.mm20.launcher2.calendar.TickTickCalendarEventSerializer
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.ticktick.TickTickRepository
import de.mm20.launcher2.ticktick.TickTickTask
import de.mm20.launcher2.ticktick.localDateIn
import java.time.ZoneId
import org.koin.core.context.GlobalContext

data class TickTickCalendarEvent(
    val id: String,
    val projectId: String,
    override val label: String,
    override val color: Int?,
    override val startTime: Long?,
    override val endTime: Long,
    override val allDay: Boolean,
    override val isCompleted: Boolean?,
    override val description: String?,
    override val calendarName: String?,
    val priority: Int,
    val tags: List<String>,
    override val labelOverride: String? = null,
) : CalendarEvent {

    override val domain: String = Domain

    override val key: String = "$domain://$id"

    override val location: String? = null
    override val attendees: List<String> = emptyList()

    override val canToggleCompleted: Boolean = true

    override suspend fun setCompleted(completed: Boolean): Boolean {
        return GlobalContext.get().get<TickTickRepository>().setCompleted(id, completed).isSuccess
    }

    override fun overrideLabel(label: String): TickTickCalendarEvent {
        return this.copy(labelOverride = label)
    }

    /** Opens the Tasks app on the task's list. */
    override fun launch(context: Context, options: Bundle?): Boolean {
        return GlobalContext.get().get<TickTickRepository>().openApp(context, "list/$projectId", options)
    }

    override fun getSerializer(): SearchableSerializer {
        return TickTickCalendarEventSerializer()
    }

    companion object {
        const val Domain = "ticktick"
    }
}

fun TickTickTask.toCalendarEvent(): TickTickCalendarEvent? {
    val due = dueDate ?: return null
    val end = if (isAllDay) {
        localDateIn(due, timeZone).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
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
