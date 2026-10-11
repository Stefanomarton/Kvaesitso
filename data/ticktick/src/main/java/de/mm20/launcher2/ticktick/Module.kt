package de.mm20.launcher2.ticktick

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val tickTickModule = module {
    single { TickTickRepository(androidContext(), get()) }
}
