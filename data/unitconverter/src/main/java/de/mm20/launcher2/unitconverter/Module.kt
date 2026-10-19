package de.mm20.launcher2.unitconverter

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val unitConverterModule = module {
    single<UnitConverterRepository> { UnitConverterRepositoryImpl(androidContext(), get()) }
}