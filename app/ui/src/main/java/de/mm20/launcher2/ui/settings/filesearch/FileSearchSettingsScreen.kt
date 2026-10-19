package de.mm20.launcher2.ui.settings.filesearch

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable

@Serializable
data object FileSearchSettingsRoute: NavKey

@Composable
fun FileSearchSettingsScreen() {
    val viewModel: FileSearchSettingsScreenVM = viewModel()
    val context = LocalContext.current

    PreferenceScreen(title = stringResource(R.string.preference_search_files)) {
        item {
            PreferenceCategory {
                val localFiles by viewModel.localFiles.collectAsState()
                val hasFilePermission by viewModel.hasFilePermission.collectAsState()
                GuardedPreference(
                    locked = hasFilePermission == false,
                    onUnlock = {
                        viewModel.requestFilePermission(context as AppCompatActivity)
                    },
                    description = stringResource(
                        if (isAtLeastApiLevel(29)) R.string.missing_permission_file_search_settings_android10 else R.string.missing_permission_file_search_settings
                    ),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_search_localfiles),
                        summary = stringResource(R.string.preference_search_localfiles_summary),
                        value = localFiles == true && hasFilePermission == true,
                        onValueChanged = {
                            viewModel.setLocalFiles(it)
                        },
                        enabled = hasFilePermission == true
                    )
                }
            }
        }
    }
}
