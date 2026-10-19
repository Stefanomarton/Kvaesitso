package de.mm20.launcher2.database.daos

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import de.mm20.launcher2.database.entities.TasksAppProjectEntity
import de.mm20.launcher2.database.entities.TasksAppTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TasksAppDao {
    @Query("SELECT * FROM TickTickProject ORDER BY sortOrder")
    fun projects(): Flow<List<TasksAppProjectEntity>>

    @Query("SELECT * FROM TickTickTask ORDER BY sortOrder")
    fun tasks(): Flow<List<TasksAppTaskEntity>>

    @Query("SELECT * FROM TickTickTask WHERE id = :id")
    suspend fun getTask(id: String): TasksAppTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TasksAppTaskEntity)

    @Query("DELETE FROM TickTickTask WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<TasksAppProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TasksAppTaskEntity>)

    @Query("DELETE FROM TickTickProject")
    suspend fun clearProjects()

    @Query("DELETE FROM TickTickTask")
    suspend fun clearTasks()

    @Transaction
    suspend fun replaceTasks(projects: List<TasksAppProjectEntity>, tasks: List<TasksAppTaskEntity>) {
        clearProjects()
        clearTasks()
        insertProjects(projects)
        insertTasks(tasks)
    }

    @Transaction
    suspend fun clearAll() {
        clearProjects()
        clearTasks()
    }
}
