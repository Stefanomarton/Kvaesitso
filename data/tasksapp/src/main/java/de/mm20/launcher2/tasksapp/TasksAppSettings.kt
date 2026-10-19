package de.mm20.launcher2.tasksapp

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import de.mm20.launcher2.tasksapp.api.TasksAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Tasks server account, login token and small integration settings, encrypted at rest.
 */
internal class TasksAppSettings(private val context: Context) {

    private val prefs by lazy { create() }

    private fun create(catchErrors: Boolean = true): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context,
                "tasks",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            if (!catchErrors) throw e
            File(context.filesDir, "../shared_prefs/tasks.xml").delete()
            return create(false)
        }
    }

    private val _account = MutableStateFlow<TasksAccount?>(null)
    val account: StateFlow<TasksAccount?>
        get() {
            if (_account.value == null) _account.value = prefs.getString("url", null)?.let {
                TasksAccount(
                    url = it,
                    email = prefs.getString("email", "")!!,
                    password = prefs.getString("password", "")!!,
                    accessId = prefs.getString("access_id", "")!!,
                    accessSecret = prefs.getString("access_secret", "")!!,
                )
            }
            return _account
        }

    fun setAccount(value: TasksAccount?) {
        prefs.edit {
            remove("token")
            if (value == null) {
                listOf("url", "email", "password", "access_id", "access_secret").forEach { remove(it) }
            } else {
                putString("url", value.url)
                putString("email", value.email)
                putString("password", value.password)
                putString("access_id", value.accessId)
                putString("access_secret", value.accessSecret)
            }
        }
        _account.value = value
    }

    /** PocketBase auth token; renewed by logging in again when it expires */
    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit { putString("token", value) }

    var defaultProjectId: String?
        get() = prefs.getString("default_project", null)
        set(value) = prefs.edit { putString("default_project", value) }

    var lastSync: Long
        get() = prefs.getLong("last_sync", 0L)
        set(value) = prefs.edit { putLong("last_sync", value) }
}
