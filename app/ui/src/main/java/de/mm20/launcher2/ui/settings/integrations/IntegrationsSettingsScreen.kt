package de.mm20.launcher2.ui.settings.integrations

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.FeatureFlags
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.media.MediaIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.smartspacer.SmartspacerSettingsRoute
import de.mm20.launcher2.ui.settings.tasks.TasksIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.ticktick.TickTickSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object IntegrationsSettingsRoute : NavKey

@Composable
fun IntegrationsSettingsScreen() {
    val backStack = LocalBackStack.current

    PreferenceScreen(title = stringResource(R.string.preference_screen_integrations)) {
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_media_integration),
                    icon = R.drawable.play_circle_24px,
                    onClick = {
                        backStack.add(MediaIntegrationSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_tasks_integration),
                    icon = R.drawable.check_24px_sharp,
                    onClick = {
                        backStack.add(TasksIntegrationSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(R.string.ticktick),
                    icon = R.drawable.task_alt_24px,
                    onClick = {
                        backStack.add(TickTickSettingsRoute)
                    }
                )
                if (isAtLeastApiLevel(29) && FeatureFlags.smartspacerIntegration) {
                    Preference(
                        title = stringResource(R.string.preference_smartspacer_integration),
                        icon = R.drawable.smartspacer,
                        onClick = {
                            backStack.add(SmartspacerSettingsRoute)
                        }
                    )
                }
            }
        }
    }
}
