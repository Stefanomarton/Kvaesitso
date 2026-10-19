package de.mm20.launcher2.files

import de.mm20.launcher2.files.providers.LocalFile
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val filesModule = module {
    factory<SearchableRepository<File>>(named<File>()) {
        FileRepository(
            androidContext(),
            get(),
            get()
        )
    }
    factory<SearchableDeserializer>(named(LocalFile.Domain)) { LocalFileDeserializer(androidContext()) }
}