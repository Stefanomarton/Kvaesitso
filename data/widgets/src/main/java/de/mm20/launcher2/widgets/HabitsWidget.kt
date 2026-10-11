package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class HabitsWidgetConfig(
    val hideCompleted: Boolean = false,
    val showNotDue: Boolean = false,
)

data class HabitsWidget(
    override val id: UUID,
    val config: HabitsWidgetConfig = HabitsWidgetConfig(),
) : Widget() {

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    override fun getLabel(context: Context): String {
        return context.getString(R.string.widget_name_habits)
    }

    companion object {
        const val Type = "habits"
    }
}
