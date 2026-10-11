package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_34_35 : Migration(34, 35) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `TickTickProject` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`color` INTEGER, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `TickTickTask` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `content` TEXT, `startDate` INTEGER, `dueDate` INTEGER, " +
                    "`isAllDay` INTEGER NOT NULL, `timeZone` TEXT, `priority` INTEGER NOT NULL, " +
                    "`completed` INTEGER NOT NULL, `repeatFlag` TEXT, `tags` TEXT, " +
                    "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `TickTickHabit` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`iconRes` TEXT, `color` INTEGER, `sortOrder` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                    "`goal` REAL NOT NULL, `step` REAL NOT NULL, `unit` TEXT, `repeatRule` TEXT, " +
                    "PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `TickTickCheckin` (`habitId` TEXT NOT NULL, `stamp` INTEGER NOT NULL, " +
                    "`value` REAL NOT NULL, `goal` REAL NOT NULL, `status` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`habitId`, `stamp`))"
        )
    }
}
