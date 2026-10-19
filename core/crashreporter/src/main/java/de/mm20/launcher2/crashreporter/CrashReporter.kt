package de.mm20.launcher2.crashreporter

import android.util.Log
import kotlinx.coroutines.CancellationException

object CrashReporter {
    fun logException(e: Exception) {
        if (e is CancellationException) return
        Log.e("MM20", Log.getStackTraceString(e))
    }
}
