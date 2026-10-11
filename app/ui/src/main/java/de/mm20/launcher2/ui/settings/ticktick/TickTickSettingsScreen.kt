package de.mm20.launcher2.ui.settings.ticktick

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
import de.mm20.launcher2.ticktick.TickTickRepository
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
data object TickTickSettingsRoute : NavKey

@Composable
fun TickTickSettingsScreen() {
    val repository: TickTickRepository = koinInject()
    val scope = rememberCoroutineScope()
    val configured by repository.isConfigured.collectAsStateWithLifecycle(false)
    val syncState by repository.syncState.collectAsStateWithLifecycle()
    val projects by remember { repository.projects() }.collectAsStateWithLifecycle(emptyList())
    var defaultProject by remember { mutableStateOf(repository.defaultProjectId) }

    PreferenceScreen(title = stringResource(R.string.ticktick)) {
        item {
            PreferenceCategory {
                Banner(
                    text = stringResource(R.string.ticktick_integration_description),
                    icon = R.drawable.info_24px,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                )
                TextPreference(
                    title = stringResource(R.string.ticktick_token),
                    value = "",
                    summary = stringResource(
                        if (configured) R.string.ticktick_token_set else R.string.ticktick_token_summary
                    ),
                    onValueChanged = { repository.setToken(it) },
                )
                if (configured) {
                    Preference(
                        title = stringResource(R.string.ticktick_sign_out),
                        icon = R.drawable.logout_20px,
                        onClick = { repository.setToken(null) },
                    )
                }
            }
        }
        if (configured) {
            item {
                PreferenceCategory {
                    Preference(
                        title = stringResource(R.string.ticktick_sync_now),
                        icon = R.drawable.autorenew_24px,
                        enabled = !syncState.running,
                        summary = listOfNotNull(
                            when {
                                syncState.running -> stringResource(R.string.ticktick_syncing)
                                syncState.lastSync == 0L -> stringResource(R.string.ticktick_never_synced)
                                else -> stringResource(
                                    R.string.ticktick_last_sync,
                                    DateUtils.getRelativeTimeSpanString(syncState.lastSync).toString()
                                )
                            },
                            syncState.tasksError?.let { stringResource(R.string.ticktick_tasks_error, it) },
                            syncState.habitsError?.let { stringResource(R.string.ticktick_habits_error, it) },
                        ).joinToString("\n"),
                        onClick = { scope.launch { repository.sync() } },
                    )
                    if (projects.isNotEmpty()) {
                        ListPreference(
                            title = stringResource(R.string.ticktick_default_list),
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
