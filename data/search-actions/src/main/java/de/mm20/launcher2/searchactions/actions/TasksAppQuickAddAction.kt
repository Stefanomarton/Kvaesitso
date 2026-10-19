package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.widget.Toast
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.tasksapp.TasksAppRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/** Adds [text] to Tasks; the server parses it like the app's quick-add bar. */
data class TasksAppQuickAddAction(
    override val label: String,
    val text: String,
) : SearchAction {
    override val icon: SearchActionIcon = SearchActionIcon.Task
    override val iconColor: Int = 0
    override val customIcon: String? = null

    override fun start(context: Context) {
        val repository = GlobalContext.get().get<TasksAppRepository>()
        MainScope().launch {
            val result = repository.quickAdd(text)
            Toast.makeText(
                context,
                if (result.isSuccess) R.string.tasksapp_task_added else R.string.tasksapp_write_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
