package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.actions.TickTickQuickAddAction
import de.mm20.launcher2.ticktick.TickTickRepository
import de.mm20.launcher2.ticktick.parseQuickAdd
import org.koin.core.context.GlobalContext
import java.time.LocalDate

class TickTickQuickAddActionBuilder(
    override val label: String,
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(R.string.ticktick_quick_add))

    override val key: String
        get() = "ticktick"

    override val icon: SearchActionIcon = SearchActionIcon.Task

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (!GlobalContext.get().get<TickTickRepository>().isConfiguredNow) return null
        val parsed = parseQuickAdd(classifiedQuery.text, LocalDate.now())
        if (parsed.title.isBlank()) return null
        return TickTickQuickAddAction(label, parsed)
    }
}
