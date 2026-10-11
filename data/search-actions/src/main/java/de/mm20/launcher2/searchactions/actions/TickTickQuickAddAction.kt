package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.widget.Toast
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.ticktick.QuickAdd
import de.mm20.launcher2.ticktick.TickTickRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.time.ZoneId

data class TickTickQuickAddAction(
    override val label: String,
    val task: QuickAdd,
) : SearchAction {
    override val icon: SearchActionIcon = SearchActionIcon.Task
    override val iconColor: Int = 0
    override val customIcon: String? = null

    override fun start(context: Context) {
        val repository = GlobalContext.get().get<TickTickRepository>()
        val due = task.date?.let { d ->
            (task.time?.let { d.atTime(it) } ?: d.atStartOfDay())
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        MainScope().launch {
            val result = repository.createTask(
                title = task.title,
                projectId = null,
                dueDate = due,
                isAllDay = task.time == null,
                tags = task.tags,
                priority = task.priority,
            )
            Toast.makeText(
                context,
                if (result.isSuccess) R.string.ticktick_task_added else R.string.ticktick_write_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
