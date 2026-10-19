package de.mm20.launcher2.ui.settings.filesearch

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.FileSearchSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FileSearchSettingsScreenVM : ViewModel(), KoinComponent {
    private val fileSearchSettings: FileSearchSettings by inject()
    private val permissionsManager: PermissionsManager by inject()

    val hasFilePermission = permissionsManager.hasPermission(PermissionGroup.ExternalStorage)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val localFiles = fileSearchSettings.localFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setLocalFiles(localFiles: Boolean) {
        fileSearchSettings.setLocalFiles(localFiles)
    }

    fun requestFilePermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.ExternalStorage)
    }
}
