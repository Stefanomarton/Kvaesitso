package de.mm20.launcher2.files

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.files.providers.LocalFile
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.FileMetaType
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.asUpdateResult
import de.mm20.launcher2.serialization.Json
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.json.JSONException
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Serializable
internal data class SerializedFile(
    val id: String? = null,
    val authority: String? = null,
    val strategy: StorageStrategy? = null,
    val path: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val label: String? = null,
    val uri: String? = null,
    val thumbnailUri: String? = null,
    val isDirectory: Boolean? = null,
    val metadata: Map<FileMetaType, String>? = null,
    val timestamp: Long? = null,
)

internal class LocalFileSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as LocalFile
        return jsonObjectOf(
            "id" to searchable.id
        ).toString()
    }

    override val typePrefix: String
        get() = "file"
}

internal class LocalFileDeserializer(
    val context: Context
) : SearchableDeserializer, KoinComponent {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        val permissionsManager: PermissionsManager = get()
        if (!permissionsManager.checkPermissionOnce(
                PermissionGroup.ExternalStorage
            )
        ) return null
        val json = JSONObject(serialized)
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val sel = "${MediaStore.Files.FileColumns._ID} = ?"
        val selArgs = arrayOf(json.getLong("id").toString())
        val cursor = context.contentResolver.query(uri, proj, sel, selArgs, null) ?: return null
        cursor.use {
            if (cursor.moveToNext()) {
                val path = cursor.getStringOrNull(2) ?: return null
                if (!java.io.File(path).exists()) return null
                val directory = java.io.File(path).isDirectory
                val id = cursor.getLong(0)
                val mimeType = cursor.getStringOrNull(3).takeIf { it != "application/octet-stream" }
                    ?: if (directory) "resource/folder" else LocalFile.getMimetypeByFileExtension(
                        path.substringAfterLast(
                            '.'
                        )
                    )
                val size = cursor.getLong(1)
                return LocalFile(
                    path = path,
                    mimeType = mimeType,
                    size = size,
                    isDirectory = directory,
                    id = id,
                    metaData = LocalFile.getMetaData(context, mimeType, path)
                )
            }
        }
        return null
    }
}
