package de.mm20.launcher2.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import de.mm20.launcher2.calendar.providers.AndroidCalendarEvent
import de.mm20.launcher2.calendar.providers.AndroidCalendarProvider
import de.mm20.launcher2.calendar.providers.TasksCalendarEvent
import de.mm20.launcher2.calendar.providers.TasksCalendarProvider
import de.mm20.launcher2.calendar.providers.TasksAppCalendarEvent
import de.mm20.launcher2.calendar.providers.TasksAppCalendarProvider
import de.mm20.launcher2.tasksapp.TasksAppRepository
import kotlinx.coroutines.flow.first
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.asUpdateResult
import de.mm20.launcher2.serialization.Json
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.json.JSONObject

class AndroidCalendarEventSerializer: SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as AndroidCalendarEvent
        val json = JSONObject()
        json.put("id", searchable.id)
        return json.toString()
    }

    override val typePrefix: String
        get() = AndroidCalendarEvent.Domain
}

class AndroidCalendarEventDeserializer(val context: Context): SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val json = JSONObject(serialized)
        val id = json.getLong("id")
        return AndroidCalendarProvider(context).get(id)
    }
}

class TasksCalendarEventSerializer: SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as TasksCalendarEvent
        val json = JSONObject()
        json.put("id", searchable.id)
        return json.toString()
    }

    override val typePrefix: String
        get() = TasksCalendarEvent.Domain
}

class TasksCalendarEventDeserializer(val context: Context): SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        if (ContextCompat.checkSelfPermission(context, "org.tasks.permission.READ_TASKS") != PackageManager.PERMISSION_GRANTED) return null
        val json = JSONObject(serialized)
        val id = json.getLong("id")
        return TasksCalendarProvider(context).get(id)
    }
}



@Serializable
internal data class SerializedCalendarEvent(
    val id: String? = null,
    val authority: String? = null,
    val color: Int? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val allDay: Boolean? = null,
    val description: String? = null,
    val calendarName: String? = null,
    val location: String? = null,
    val attendees: List<String>? = null,
    val uri: String? = null,
    val completed: Boolean? = null,
    val label: String? = null,
    val timestamp: Long = 0L,
    val strategy: StorageStrategy = StorageStrategy.StoreCopy,
)

class TasksAppCalendarEventSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as TasksAppCalendarEvent
        return JSONObject().put("id", searchable.id).toString()
    }

    override val typePrefix: String
        get() = TasksAppCalendarEvent.Domain
}

class TasksAppCalendarEventDeserializer(
    private val repository: TasksAppRepository,
) : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        val id = JSONObject(serialized).getString("id")
        val task = repository.tasks().first().find { it.id == id } ?: return null
        return TasksAppCalendarProvider(listOf(task), emptyList())
            .search(null, Long.MIN_VALUE, Long.MAX_VALUE, emptyList(), false, false)
            .firstOrNull()
    }
}
