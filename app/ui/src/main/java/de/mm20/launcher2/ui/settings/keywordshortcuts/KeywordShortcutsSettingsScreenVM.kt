package de.mm20.launcher2.ui.settings.keywordshortcuts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class KeywordShortcutsSettingsScreenVM : ViewModel(), KoinComponent {
    private val searchUiSettings: SearchUiSettings by inject()
    private val searchableRepository: SavableSearchableRepository by inject()

    /** Keyword to its target, sorted by keyword; the target is null when the item no longer exists. */
    val shortcuts = searchUiSettings.keywordShortcuts.flatMapLatest { shortcuts ->
        searchableRepository.getByKeys(shortcuts.values.toList()).map { found ->
            val byKey = found.associateBy { it.key }
            shortcuts.entries.sortedBy { it.key }.map { it.key to byKey[it.value] }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun save(keyword: String, searchable: SavableSearchable, replaces: String?) {
        // Like gesture shortcuts: the item must be in the database to be found again by key.
        searchableRepository.insert(searchable)
        searchUiSettings.setKeywordShortcut(keyword, searchable.key, replaces)
    }

    fun remove(keyword: String) {
        searchUiSettings.removeKeywordShortcut(keyword)
    }
}
