package de.mm20.launcher2.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "TickTickProject")
data class TickTickProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int?,
    val sortOrder: Long,
)

@Entity(tableName = "TickTickTask")
data class TickTickTaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val content: String?,
    val startDate: Long?,
    val dueDate: Long?,
    val isAllDay: Boolean,
    val timeZone: String?,
    val priority: Int,
    val completed: Boolean,
    val repeatFlag: String?,
    /** Comma-separated */
    val tags: String?,
    val sortOrder: Long,
)
