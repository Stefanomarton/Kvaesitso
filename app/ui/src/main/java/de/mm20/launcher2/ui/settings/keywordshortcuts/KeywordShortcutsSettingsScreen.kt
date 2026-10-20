package de.mm20.launcher2.ui.settings.keywordshortcuts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.SearchablePicker
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object KeywordShortcutsSettingsRoute : NavKey

@Composable
fun KeywordShortcutsSettingsScreen() {
    val viewModel: KeywordShortcutsSettingsScreenVM = viewModel()
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()

    // null: sheet closed, "": new shortcut, otherwise the keyword being edited
    var editing by remember { mutableStateOf<String?>(null) }

    PreferenceScreen(title = stringResource(R.string.preference_keyword_shortcuts)) {
        item {
            PreferenceCategory {
                for ((keyword, target) in shortcuts) {
                    Preference(
                        title = keyword,
                        summary = target?.let { it.labelOverride ?: it.label }
                            ?: stringResource(R.string.keyword_shortcut_missing),
                        onClick = { editing = keyword },
                        controls = {
                            IconButton(onClick = { viewModel.remove(keyword) }) {
                                Icon(
                                    painterResource(R.drawable.delete_24px),
                                    stringResource(R.string.keyword_shortcut_delete),
                                )
                            }
                        },
                    )
                }
                Preference(
                    title = stringResource(R.string.keyword_shortcut_add),
                    summary = stringResource(R.string.keyword_shortcut_add_summary),
                    icon = R.drawable.add_24px,
                    onClick = { editing = "" },
                )
            }
        }
    }

    val original = editing
    DismissableBottomSheet(
        expanded = original != null,
        onDismissRequest = { editing = null },
    ) {
        var keyword by remember(original) { mutableStateOf(original.orEmpty()) }
        var missingKeyword by remember(original) { mutableStateOf(false) }
        Column {
            OutlinedTextField(
                value = keyword,
                onValueChange = {
                    keyword = it
                    missingKeyword = false
                },
                label = { Text(stringResource(R.string.keyword_shortcut_keyword)) },
                supportingText = if (missingKeyword) {
                    { Text(stringResource(R.string.keyword_shortcut_keyword_required)) }
                } else null,
                isError = missingKeyword,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SearchablePicker(
                value = shortcuts.firstOrNull { it.first == original }?.second,
                onValueChanged = {
                    if (it == null) return@SearchablePicker
                    if (keyword.isBlank()) {
                        missingKeyword = true
                        return@SearchablePicker
                    }
                    viewModel.save(keyword, it, replaces = original?.takeIf { it.isNotEmpty() })
                    editing = null
                },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }
}
