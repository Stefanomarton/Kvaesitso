package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.actions.TasksAppQuickAddAction
import de.mm20.launcher2.tasksapp.TasksAppRepository
import org.koin.core.context.GlobalContext

class TasksAppQuickAddActionBuilder(
    override val label: String,
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(R.string.tasksapp_quick_add))

    override val key: String
        get() = "ticktick"

    override val icon: SearchActionIcon = SearchActionIcon.Task

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (!GlobalContext.get().get<TasksAppRepository>().isConfiguredNow) return null
        val text = classifiedQuery.text.trim()
        if (text.isEmpty()) return null
        return TasksAppQuickAddAction(label, text)
    }
}
