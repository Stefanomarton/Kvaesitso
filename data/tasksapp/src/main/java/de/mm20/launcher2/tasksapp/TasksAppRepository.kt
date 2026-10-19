package de.mm20.launcher2.tasksapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.net.toUri
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.TasksAppProjectEntity
import de.mm20.launcher2.database.entities.TasksAppTaskEntity
import de.mm20.launcher2.tasksapp.api.TaskDto
import de.mm20.launcher2.tasksapp.api.TasksAccount
import de.mm20.launcher2.tasksapp.api.TasksAppApi
import de.mm20.launcher2.tasksapp.api.TasksAppUnauthorizedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

/**
 * The self-hosted Tasks app behind a local Room cache.
 * Stored identifiers keep their old "ticktick" values (widget type, calendar provider and search keys,
 * quick-add action key, Room table names, sync work name): devices already have data saved under them.
 */
class TasksAppRepository internal constructor(
    private val context: Context,
    database: AppDatabase,
) {
    private val settings = TasksAppSettings(context)
    private val api = TasksAppApi(
        account = { settings.account.value },
        loadToken = { settings.token },
        saveToken = { settings.token = it },
    )
    private val dao = database.tasksAppDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _syncState = MutableStateFlow(TasksAppSyncState(lastSync = settings.lastSync))
    val syncState: StateFlow<TasksAppSyncState> = _syncState

    val account: StateFlow<TasksAccount?> get() = settings.account
    val isConfigured: Flow<Boolean> = settings.account.map { it != null }
    val isConfiguredNow: Boolean
        get() = settings.account.value != null

    init {
        // Re-applies the current work request (network constraint) to the job already scheduled on the device.
        schedulePeriodicSync()
    }

    var defaultProjectId: String?
        get() = settings.defaultProjectId
        set(value) {
            settings.defaultProjectId = value
        }

    fun setAccount(account: TasksAccount?) {
        settings.setAccount(account)
        if (account == null) {
            WorkManager.getInstance(context).cancelUniqueWork(WorkName)
            scope.launch { dao.clearAll() }
            _syncState.value = TasksAppSyncState()
            settings.lastSync = 0L
        } else {
            schedulePeriodicSync()
            requestSync()
        }
    }

    /**
     * Opens the Tasks Android app (it.marton.tasks, no deep links: its start screen). Without it, the web app at
     * [fragment] (e.g. "list/<id>"): the PWA installed from Chrome (a WebAPK) when there is one, else the browser.
     */
    fun openApp(context: Context, fragment: String = "today", options: Bundle? = null): Boolean {
        context.packageManager.getLaunchIntentForPackage(AndroidApp)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { if (context.tryStartActivity(it, options)) return true }
        val url = settings.account.value?.url?.trimEnd('/') ?: return false
        val intent = Intent(Intent.ACTION_VIEW, "$url/#$fragment".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .firstOrNull { it.activityInfo.packageName.startsWith("org.chromium.webapk") }
            ?.let { intent.setPackage(it.activityInfo.packageName) }
        return context.tryStartActivity(intent, options)
    }

    fun projects(): Flow<List<TasksAppProject>> = dao.projects().map { list ->
        list.map { TasksAppProject(it.id, it.name, it.color) }
    }

    fun tasks(): Flow<List<TasksAppTask>> = combine(dao.tasks(), dao.projects()) { tasks, projects ->
        val byId = projects.associateBy { it.id }
        tasks.map { it.toModel(byId[it.projectId]) }
    }

    suspend fun getTask(id: String): TasksAppTask? {
        val entity = dao.getTask(id) ?: return null
        return entity.toModel(null)
    }

    /**
     * Fire-and-forget sync, used after writes and when the launcher resumes.
     */
    fun requestSync() {
        scope.launch { sync() }
    }

    /**
     * Called when task UI becomes visible; skips if a sync happened recently.
     */
    fun requestSyncIfStale() {
        if (System.currentTimeMillis() - settings.lastSync > 2 * 60 * 1000L) requestSync()
    }

    suspend fun sync() {
        if (!isConfiguredNow) return
        syncMutex.withLock {
            _syncState.update { it.copy(running = true) }
            val result = runCatching {
                val lists = api.lists()
                val tasks = api.openTasks()
                dao.replaceTasks(
                    lists.mapIndexed { i, l ->
                        TasksAppProjectEntity(l.id, l.name, parseColor(l.color), i.toLong())
                    } + TasksAppProjectEntity(InboxId, "Inbox", null, Long.MIN_VALUE),
                    tasks.mapIndexed { i, t -> t.toEntity(i.toLong()) },
                )
            }
            if (result.isSuccess) settings.lastSync = System.currentTimeMillis()
            _syncState.value = TasksAppSyncState(
                running = false,
                lastSync = settings.lastSync,
                error = result.exceptionOrNull()?.toMessage(),
            )
        }
    }

    /**
     * Optimistic write: apply locally, call the API, roll back on failure, then resync.
     */
    private suspend fun <T> write(apply: suspend () -> Unit, rollback: suspend () -> Unit, call: suspend () -> T): Result<T> {
        apply()
        return try {
            Result.success(call()).also { requestSync() }
        } catch (e: Exception) {
            Log.e("Tasks", "Write failed", e)
            rollback()
            if (e is TasksAppUnauthorizedException) _syncState.update { it.copy(error = e.toMessage()) }
            Result.failure(e)
        }
    }

    /** Completing a recurring task moves it to its next date on the server; the resync brings it back. */
    suspend fun setCompleted(taskId: String, completed: Boolean): Result<Unit> {
        val task = dao.getTask(taskId) ?: return Result.failure(NoSuchElementException(taskId))
        return write(
            apply = { dao.upsertTask(task.copy(completed = completed)) },
            rollback = { dao.upsertTask(task) },
        ) {
            if (completed) api.completeTask(task.id) else api.reopenTask(task.id)
        }
    }

    /** [text] in the app's Italian quick-add syntax ("Chiamare domani alle 9 #lavoro +tel !2"), parsed by the server. */
    suspend fun quickAdd(text: String): Result<Unit> = runCatching {
        val created = api.quickAdd(text, settings.defaultProjectId?.takeIf { it != InboxId })
        dao.upsertTask(created.toEntity(0L))
        requestSync()
    }.onFailure { Log.e("Tasks", "Create failed", it) }

    suspend fun updateTask(taskId: String, edit: TaskEdit): Result<Unit> {
        val old = dao.getTask(taskId) ?: return Result.failure(NoSuchElementException(taskId))
        val updated = old.copy(
            title = edit.title,
            content = edit.content,
            projectId = edit.projectId,
            dueDate = edit.dueDate,
            startDate = edit.dueDate,
            isAllDay = edit.isAllDay,
            priority = edit.priority,
        )
        val (date, time) = dueFields(edit.dueDate, edit.isAllDay)
        return write(
            apply = { dao.upsertTask(updated) },
            rollback = { dao.upsertTask(old) },
        ) {
            api.updateTask(buildJsonObject {
                put("id", old.id)
                put("title", edit.title)
                put("notes", edit.content ?: "")
                put("list", edit.projectId)
                put("priority", edit.priority)
                // a date can't be cleared while the task repeats; dropping the date drops the repeat too
                if (date.isEmpty()) {
                    put("clear", buildJsonArray { listOf("due_date", "due_time", "repeat").forEach { add(JsonPrimitive(it)) } })
                } else {
                    put("due_date", date)
                    put("due_time", time)
                }
            })
        }.map { }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        val task = dao.getTask(taskId) ?: return Result.failure(NoSuchElementException(taskId))
        return write(
            apply = { dao.deleteTask(taskId) },
            rollback = { dao.upsertTask(task) },
        ) { api.deleteTask(task.id) }
    }

    fun schedulePeriodicSync() {
        if (!isConfiguredNow) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<TasksAppSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build(),
        )
    }

    private fun TasksAppTaskEntity.toModel(project: TasksAppProjectEntity?) = TasksAppTask(
        id = id,
        projectId = projectId,
        projectName = project?.name ?: if (projectId == InboxId) "Inbox" else null,
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

    private fun TaskDto.toEntity(sortOrder: Long): TasksAppTaskEntity {
        val due = dueMillis(due_date, due_time)
        return TasksAppTaskEntity(
            id = id,
            projectId = list,
            title = title,
            content = notes.takeIf { it.isNotBlank() },
            startDate = due,
            dueDate = due,
            isAllDay = due_time.isEmpty(),
            timeZone = null,
            priority = priority,
            completed = status == "done",
            repeatFlag = repeat.takeIf { it.isNotEmpty() },
            tags = tags.joinToString(","),
            sortOrder = sortOrder,
        )
    }

    private fun Throwable.toMessage(): String = when (this) {
        is TasksAppUnauthorizedException -> "Email or password rejected"
        else -> message ?: javaClass.simpleName
    }.also { if (this is Exception && this !is TasksAppUnauthorizedException) CrashReporter.logException(this) }

    companion object {
        /** Tasks has no Inbox list: a task without a list is in the Inbox */
        const val InboxId = ""
        private const val WorkName = "TickTickSync"
        private const val AndroidApp = "it.marton.tasks"

        internal fun parseColor(hex: String?): Int? {
            val clean = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
            return clean.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        }
    }
}
