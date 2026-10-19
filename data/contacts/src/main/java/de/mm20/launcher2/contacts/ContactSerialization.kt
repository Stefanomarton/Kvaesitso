package de.mm20.launcher2.contacts

import android.content.Context
import androidx.core.net.toUri
import de.mm20.launcher2.contacts.providers.AndroidContact
import de.mm20.launcher2.contacts.providers.AndroidContactProvider
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.asUpdateResult
import de.mm20.launcher2.search.contact.CustomContactAction
import de.mm20.launcher2.search.contact.EmailAddress
import de.mm20.launcher2.search.contact.PhoneNumber
import de.mm20.launcher2.search.contact.PostalAddress
import de.mm20.launcher2.serialization.Json
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import org.json.JSONObject

internal class AndroidContactSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as AndroidContact
        return jsonObjectOf(
            "id" to searchable.id
        ).toString()
    }

    override val typePrefix: String
        get() = AndroidContact.Domain
}


internal class AndroidContactDeserializer(
    private val context: Context,
    private val permissionsManager: PermissionsManager
) : SearchableDeserializer {

    override suspend fun deserialize(serialized: String): SavableSearchable? {
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.Contacts)) return null
        val id = JSONObject(serialized).getLong("id")

        val androidContactProvider = AndroidContactProvider(context)

        return androidContactProvider.get(id)
    }
}
