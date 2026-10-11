package de.mm20.launcher2.ticktick

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.TickTickCheckinEntity
import de.mm20.launcher2.database.entities.TickTickHabitEntity
import de.mm20.launcher2.database.entities.TickTickProjectEntity
import de.mm20.launcher2.database.entities.TickTickTaskEntity
import de.mm20.launcher2.ticktick.api.CheckinRequest
import de.mm20.launcher2.ticktick.api.TaskDto
import de.mm20.launcher2.ticktick.api.TickTickApi
import de.mm20.launcher2.ticktick.api.TickTickUnauthorizedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class TickTickRepository internal constructor(
    private val context: Context,
    database: AppDatabase,
) {
    private val settings = TickTickSettings(context)
    private val api = TickTickApi { settings.token.value }
    private val dao = database.tickTickDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _syncState = MutableStateFlow(TickTickSyncState(lastSync = settings.lastSync))
    val syncState: StateFlow<TickTickSyncState> = _syncState

    val isConfigured: Flow<Boolean> = settings.token.map { !it.isNullOrBlank() }

    var defaultProjectId: String?
        get() = settings.defaultProjectId
        set(value) {
            settings.defaultProjectId = value
        }

    fun setToken(token: String?) {
        settings.setToken(token?.trim()?.takeIf { it.isNotEmpty() })
        if (token.isNullOrBlank()) {
            WorkManager.getInstance(context).cancelUniqueWork(WorkName)
            scope.launch { dao.clearAll() }
            _syncState.value = TickTickSyncState()
            settings.lastSync = 0L
        } else {
            schedulePeriodicSync()
            requestSync()
        }
    }

    fun projects(): Flow<List<TickTickProject>> = dao.projects().map { list ->
        list.map { TickTickProject(it.id, it.name, it.color) }
    }

    fun tasks(): Flow<List<TickTickTask>> = combine(dao.tasks(), dao.projects()) { tasks, projects ->
        val byId = projects.associateBy { it.id }
        tasks.map { it.toModel(byId[it.projectId]) }
    }

    suspend fun getTask(id: String): TickTickTask? {
        val entity = dao.getTask(id) ?: return null
        return entity.toModel(null)
    }

    fun habitsToday(): Flow<List<HabitToday>> {
        val today = LocalDate.now()
        return combine(dao.habits(), dao.checkins(stampOf(today.minusDays(CheckinWindowDays)))) { habits, checkins ->
            val byHabit = checkins.groupBy { it.habitId }
            habits.map { habit ->
                val entries = byHabit[habit.id].orEmpty()
                val done = entries.filter { it.isDone() }.map { it.stamp }.toSet()
                val todayEntry = entries.firstOrNull { it.stamp == stampOf(today) }
                HabitToday(
                    id = habit.id,
                    name = habit.name,
                    color = habit.color,
                    isBoolean = habit.type != "Real",
                    goal = habit.goal,
                    step = habit.step,
                    unit = habit.unit,
                    valueToday = todayEntry?.value ?: 0.0,
                    doneToday = stampOf(today) in done,
                    dueToday = isDueOn(habit.repeatRule, today),
                    streak = streak(done, today, habit.repeatRule),
                    lastWeek = (6 downTo 0).map {
                        val day = today.minusDays(it.toLong())
                        HabitDay(stampOf(day), isDueOn(habit.repeatRule, day), stampOf(day) in done)
                    },
                )
            }
        }
    }

    /**
     * Fire-and-forget sync, used after writes and when the launcher resumes.
     */
    fun requestSync() {
        scope.launch { sync() }
    }

    /**
     * Called when TickTick UI becomes visible; skips if a sync happened recently.
     */
    fun requestSyncIfStale() {
        if (System.currentTimeMillis() - settings.lastSync > 2 * 60 * 1000L) requestSync()
    }

    suspend fun sync() {
        if (settings.token.value.isNullOrBlank()) return
        syncMutex.withLock {
            _syncState.update { it.copy(running = true) }
            val tasks = scope.async { runCatching { syncTasks() } }
            val habits = scope.async { runCatching { syncHabits() } }
            val tasksResult = tasks.await()
            val habitsResult = habits.await()
            val now = System.currentTimeMillis()
            if (tasksResult.isSuccess || habitsResult.isSuccess) settings.lastSync = now
            _syncState.value = TickTickSyncState(
                running = false,
                lastSync = settings.lastSync,
                tasksError = tasksResult.exceptionOrNull()?.toMessage(),
                habitsError = habitsResult.exceptionOrNull()?.toMessage(),
            )
        }
    }

    private suspend fun syncTasks() {
        val projects = api.projects().filter { it.closed != true && it.kind != "NOTE" }
        val projectIds = listOf("inbox") + projects.map { it.id }
        val tasks = projectIds.flatMap { api.projectData(it).tasks }
        // The inbox's real id is "inbox<userId>"; learn it from its tasks.
        val inboxId = tasks.firstOrNull { it.projectId.startsWith(InboxId) }?.projectId ?: InboxId
        val projectEntities = projects.mapIndexed { i, p ->
            TickTickProjectEntity(p.id, p.name, parseColor(p.color), p.sortOrder ?: i.toLong())
        } + TickTickProjectEntity(inboxId, "Inbox", null, Long.MIN_VALUE)
        dao.replaceTasks(projectEntities, tasks.mapNotNull { it.toEntity() })
    }

    private suspend fun syncHabits() {
        val habits = api.habits().filter { it.status != 1 }
        val today = LocalDate.now()
        val checkins = api.checkins(
            habits.map { it.id },
            stampOf(today.minusDays(CheckinWindowDays)),
            stampOf(today),
        )
        dao.replaceHabits(
            habits.mapIndexed { i, h ->
                TickTickHabitEntity(
                    id = h.id,
                    name = h.name,
                    iconRes = h.iconRes,
                    color = parseColor(h.color),
                    sortOrder = h.sortOrder ?: i.toLong(),
                    type = h.type ?: "Boolean",
                    goal = h.goal ?: 1.0,
                    step = h.step?.takeIf { it > 0 } ?: 1.0,
                    unit = h.unit,
                    repeatRule = h.repeatRule,
                )
            },
            checkins.flatMap { c ->
                c.checkins.map {
                    TickTickCheckinEntity(c.habitId, it.stamp, it.value ?: 0.0, it.goal ?: 1.0, it.status ?: 0)
                }
            },
        )
    }

    /**
     * Optimistic write: apply locally, call the API, roll back on failure, then resync.
     */
    private suspend fun <T> write(apply: suspend () -> Unit, rollback: suspend () -> Unit, call: suspend () -> T): Result<T> {
        apply()
        return try {
            Result.success(call()).also { requestSync() }
        } catch (e: Exception) {
            Log.e("TickTick", "Write failed", e)
            rollback()
            if (e is TickTickUnauthorizedException) {
                _syncState.update { it.copy(tasksError = e.toMessage(), habitsError = e.toMessage()) }
            }
            Result.failure(e)
        }
    }

    suspend fun setCompleted(taskId: String, completed: Boolean): Result<Unit> {
        val task = dao.getTask(taskId) ?: return Result.failure(NoSuchElementException(taskId))
        return write(
            apply = { dao.upsertTask(task.copy(completed = completed)) },
            rollback = { dao.upsertTask(task) },
        ) {
            if (completed) {
                api.completeTask(task.projectId, task.id)
            } else {
                // No dedicated endpoint; setting status back to 0 reopens the task.
                api.updateTask(task.id, buildJsonObject {
                    put("id", JsonPrimitive(task.id))
                    put("projectId", JsonPrimitive(task.projectId))
                    put("status", JsonPrimitive(0))
                })
            }
            Unit
        }
    }

    suspend fun createTask(
        title: String,
        projectId: String?,
        dueDate: Long?,
        isAllDay: Boolean,
        tags: List<String> = emptyList(),
        priority: Int = 0,
    ): Result<Unit> = runCatching {
        val created = api.createTask(
            TaskDto(
                projectId = projectId ?: settings.defaultProjectId ?: InboxId,
                title = title,
                isAllDay = dueDate?.let { isAllDay },
                startDate = dueDate?.let { formatTickTickDate(it) },
                dueDate = dueDate?.let { formatTickTickDate(it) },
                timeZone = ZoneId.systemDefault().id,
                tags = tags.takeIf { it.isNotEmpty() },
                priority = priority,
            )
        )
        created.toEntity()?.let { dao.upsertTask(it) }
        requestSync()
    }.onFailure { Log.e("TickTick", "Create failed", it) }

    suspend fun updateTask(taskId: String, edit: TaskEdit): Result<Unit> {
        val old = dao.getTask(taskId) ?: return Result.failure(NoSuchElementException(taskId))
        val updated = old.copy(
            title = edit.title,
            content = edit.content,
            projectId = edit.projectId,
            dueDate = edit.dueDate,
            startDate = edit.dueDate,
            isAllDay = edit.isAllDay,
            timeZone = ZoneId.systemDefault().id,
            priority = edit.priority,
        )
        return write(
            apply = { dao.upsertTask(updated) },
            rollback = { dao.upsertTask(old) },
        ) {
            if (edit.projectId != old.projectId) {
                // Moving lists is a separate concept in the API; update in old project, then move.
                api.moveTask(old.id, old.projectId, edit.projectId)
            }
            api.updateTask(old.id, buildJsonObject {
                put("id", JsonPrimitive(old.id))
                put("projectId", JsonPrimitive(edit.projectId))
                put("title", JsonPrimitive(edit.title))
                put("content", JsonPrimitive(edit.content ?: ""))
                put("priority", JsonPrimitive(edit.priority))
                put("isAllDay", JsonPrimitive(edit.isAllDay))
                put("timeZone", JsonPrimitive(ZoneId.systemDefault().id))
                val date = edit.dueDate?.let { JsonPrimitive(formatTickTickDate(it)) } ?: JsonNull
                put("startDate", date)
                put("dueDate", date)
            })
            Unit
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        val task = dao.getTask(taskId) ?: return Result.failure(NoSuchElementException(taskId))
        return write(
            apply = { dao.deleteTask(taskId) },
            rollback = { dao.upsertTask(task) },
        ) { api.deleteTask(task.projectId, task.id) }
    }

    /**
     * Boolean habits toggle, count habits add one step.
     */
    suspend fun checkIn(habit: HabitToday): Result<Unit> {
        val value = when {
            habit.isBoolean && habit.doneToday -> 0.0
            habit.isBoolean -> 1.0
            else -> habit.valueToday + habit.step
        }
        return setHabitValue(habit.id, value, habit.goal)
    }

    suspend fun setHabitValue(habitId: String, value: Double, goal: Double): Result<Unit> {
        val stamp = stampOf(LocalDate.now())
        val old = dao.getCheckin(habitId, stamp)
        val status = if (value >= goal) StatusDone else StatusUnchecked
        val new = TickTickCheckinEntity(habitId, stamp, value, goal, status)
        return write(
            apply = { dao.upsertCheckin(new) },
            rollback = { if (old != null) dao.upsertCheckin(old) else dao.deleteCheckin(habitId, stamp) },
        ) { api.checkin(habitId, CheckinRequest(stamp, value, goal, status)) }
    }

    fun schedulePeriodicSync() {
        if (settings.token.value.isNullOrBlank()) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WorkName,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TickTickSyncWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    private fun TickTickTaskEntity.toModel(project: TickTickProjectEntity?) = TickTickTask(
        id = id,
        projectId = projectId,
        projectName = project?.name ?: if (projectId.startsWith(InboxId)) "Inbox" else null,
        projectColor = project?.color,
        title = title,
        content = content,
        startDate = startDate,
        dueDate = dueDate,
        isAllDay = isAllDay,
        timeZone = timeZone,
        priority = priority,
        completed = completed,
        isRecurring = !repeatFlag.isNullOrBlank(),
        tags = tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    )

    private fun TaskDto.toEntity(): TickTickTaskEntity? {
        return TickTickTaskEntity(
            id = id ?: return null,
            projectId = projectId,
            title = title,
            content = content?.takeIf { it.isNotBlank() } ?: desc?.takeIf { it.isNotBlank() },
            startDate = parseTickTickDate(startDate),
            dueDate = parseTickTickDate(dueDate),
            isAllDay = isAllDay == true,
            timeZone = timeZone,
            priority = priority ?: 0,
            completed = status == 2,
            repeatFlag = repeatFlag,
            tags = tags?.joinToString(","),
            sortOrder = sortOrder ?: 0L,
        )
    }

    private fun TickTickCheckinEntity.isDone() = status == StatusDone || (goal > 0 && value >= goal)

    private fun Throwable.toMessage(): String = when (this) {
        is TickTickUnauthorizedException -> "Token invalid or expired"
        else -> message ?: javaClass.simpleName
    }.also { if (this is Exception && this !is TickTickUnauthorizedException) CrashReporter.logException(this) }

    companion object {
        const val InboxId = "inbox"
        private const val WorkName = "TickTickSync"
        private const val CheckinWindowDays = 120L
        private const val StatusUnchecked = 0
        private const val StatusDone = 2

        internal fun parseColor(hex: String?): Int? {
            val clean = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
            return clean.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        }
    }
}
