package de.mm20.launcher2.tasksapp

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val tasksAppModule = module {
    single { TasksAppRepository(androidContext(), get()) }
}
