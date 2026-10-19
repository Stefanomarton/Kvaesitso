package de.mm20.launcher2.database.daos

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import de.mm20.launcher2.database.entities.TickTickProjectEntity
import de.mm20.launcher2.database.entities.TickTickTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TickTickDao {
    @Query("SELECT * FROM TickTickProject ORDER BY sortOrder")
    fun projects(): Flow<List<TickTickProjectEntity>>

    @Query("SELECT * FROM TickTickTask ORDER BY sortOrder")
    fun tasks(): Flow<List<TickTickTaskEntity>>

    @Query("SELECT * FROM TickTickTask WHERE id = :id")
    suspend fun getTask(id: String): TickTickTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TickTickTaskEntity)

    @Query("DELETE FROM TickTickTask WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<TickTickProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TickTickTaskEntity>)

    @Query("DELETE FROM TickTickProject")
    suspend fun clearProjects()

    @Query("DELETE FROM TickTickTask")
    suspend fun clearTasks()

    @Transaction
    suspend fun replaceTasks(projects: List<TickTickProjectEntity>, tasks: List<TickTickTaskEntity>) {
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
