package de.mm20.launcher2.calendar.providers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import de.mm20.launcher2.calendar.TickTickCalendarEventSerializer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.ticktick.TickTickRepository
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

    override fun launch(context: Context, options: Bundle?): Boolean {
        val app = context.packageManager.getLaunchIntentForPackage(TickTickPackage)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (app != null && context.tryStartActivity(app, options)) return true
        val web = Intent(Intent.ACTION_VIEW, "https://ticktick.com/webapp/#p/$projectId/tasks/$id".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return context.tryStartActivity(web, options)
    }

    override fun getSerializer(): SearchableSerializer {
        return TickTickCalendarEventSerializer()
    }

    companion object {
        const val Domain = "ticktick"
        private const val TickTickPackage = "com.ticktick.task"
    }
}
