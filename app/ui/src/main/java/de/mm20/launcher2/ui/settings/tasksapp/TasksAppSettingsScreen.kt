package de.mm20.launcher2.ui.settings.tasksapp

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.tasksapp.TasksAppRepository
import de.mm20.launcher2.tasksapp.api.TasksAccount
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.TextPreference
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data object TasksAppSettingsRoute : NavKey

@Composable
fun TasksAppSettingsScreen() {
    val repository: TasksAppRepository = koinInject()
    val scope = rememberCoroutineScope()
    val configured by repository.isConfigured.collectAsStateWithLifecycle(false)
    val syncState by repository.syncState.collectAsStateWithLifecycle()
    val projects by remember { repository.projects() }.collectAsStateWithLifecycle(emptyList())
    var defaultProject by remember { mutableStateOf(repository.defaultProjectId) }
    // Saved as soon as address, email and password are all filled in
    var draft by remember {
        mutableStateOf(repository.account.value ?: TasksAccount("https://tasks.marton.dev", "", "", "", ""))
    }
    fun edit(account: TasksAccount) {
        draft = account
        if (account.url.isNotBlank() && account.email.isNotBlank() && account.password.isNotBlank()) {
            repository.setAccount(account)
        }
    }
    val hidden = stringResource(R.string.tasksapp_hidden)

    PreferenceScreen(title = stringResource(R.string.tasksapp)) {
        item {
            PreferenceCategory {
                Banner(
                    text = stringResource(R.string.tasksapp_integration_description),
                    icon = R.drawable.info_24px,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                )
                TextPreference(
                    title = stringResource(R.string.tasksapp_url),
                    value = draft.url,
                    onValueChanged = { edit(draft.copy(url = it.trim())) },
                )
                TextPreference(
                    title = stringResource(R.string.tasksapp_email),
                    value = draft.email,
                    onValueChanged = { edit(draft.copy(email = it.trim())) },
                )
                TextPreference(
                    title = stringResource(R.string.tasksapp_password),
                    value = "",
                    summary = if (draft.password.isEmpty()) null else hidden,
                    onValueChanged = { edit(draft.copy(password = it)) },
                )
                TextPreference(
                    title = stringResource(R.string.tasksapp_access_id),
                    value = draft.accessId,
                    summary = stringResource(R.string.tasksapp_access_summary),
                    onValueChanged = { edit(draft.copy(accessId = it.trim())) },
                )
                TextPreference(
                    title = stringResource(R.string.tasksapp_access_secret),
                    value = "",
                    summary = if (draft.accessSecret.isEmpty()) null else hidden,
                    onValueChanged = { edit(draft.copy(accessSecret = it.trim())) },
                )
                if (configured) {
                    Preference(
                        title = stringResource(R.string.tasksapp_sign_out),
                        icon = R.drawable.logout_20px,
                        onClick = {
                            repository.setAccount(null)
                            draft = draft.copy(password = "", accessSecret = "")
                        },
                    )
                }
            }
        }
        if (configured) {
            item {
                PreferenceCategory {
                    Preference(
                        title = stringResource(R.string.tasksapp_sync_now),
                        icon = R.drawable.autorenew_24px,
                        enabled = !syncState.running,
                        summary = listOfNotNull(
                            when {
                                syncState.running -> stringResource(R.string.tasksapp_syncing)
                                syncState.lastSync == 0L -> stringResource(R.string.tasksapp_never_synced)
                                else -> stringResource(
                                    R.string.tasksapp_last_sync,
                                    DateUtils.getRelativeTimeSpanString(syncState.lastSync).toString()
                                )
                            },
                            syncState.error,
                        ).joinToString("\n"),
                        onClick = { scope.launch { repository.sync() } },
                    )
                    if (projects.isNotEmpty()) {
                        ListPreference(
                            title = stringResource(R.string.tasksapp_default_list),
                            icon = R.drawable.folder_24px,
                            items = projects.map { it.name to it.id },
                            value = defaultProject ?: projects.first().id,
                            onValueChanged = {
                                repository.defaultProjectId = it
                                defaultProject = it
                            },
                        )
                    }
                }
            }
        }
    }
}
