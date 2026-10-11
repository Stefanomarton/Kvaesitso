package de.mm20.launcher2.ticktick

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Personal API token and small integration settings, encrypted at rest.
 */
internal class TickTickSettings(private val context: Context) {

    private val prefs by lazy { create() }

    private fun create(catchErrors: Boolean = true): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context,
                "ticktick",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            if (!catchErrors) throw e
            File(context.filesDir, "../shared_prefs/ticktick.xml").delete()
            return create(false)
        }
    }

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?>
        get() {
            if (_token.value == null) _token.value = prefs.getString("token", null)
            return _token
        }

    fun setToken(value: String?) {
        prefs.edit { if (value == null) remove("token") else putString("token", value) }
        _token.value = value
    }

    var defaultProjectId: String?
        get() = prefs.getString("default_project", null)
        set(value) = prefs.edit { putString("default_project", value) }

    var lastSync: Long
        get() = prefs.getLong("last_sync", 0L)
        set(value) = prefs.edit { putLong("last_sync", value) }
}
