package de.mm20.launcher2.tasksapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TasksAppSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {

    private val repository: TasksAppRepository by inject()

    override suspend fun doWork(): Result {
        repository.sync()
        return Result.success()
    }
}
