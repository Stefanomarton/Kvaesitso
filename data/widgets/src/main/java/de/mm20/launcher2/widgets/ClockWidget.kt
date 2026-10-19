package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import java.util.UUID

/**
 * The clock as a regular, reorderable widget. Its look is configured globally (ClockWidgetSettings).
 * While no clock widget exists on the home screen, the clock stays pinned above the widget column.
 */
data class ClockWidget(
    override val id: UUID,
) : Widget() {

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = null,
        )
    }

    override fun getLabel(context: Context): String {
        return context.getString(R.string.preference_screen_clockwidget)
    }

    companion object {
        const val Type = "clock"
    }
}
