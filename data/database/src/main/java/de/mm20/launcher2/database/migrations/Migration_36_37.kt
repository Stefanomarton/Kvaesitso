package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Tasks app: a task's parent, its kind (block) and its length, for the Oggi timeline. */
class Migration_36_37 : Migration(36, 37) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `TickTickTask` ADD COLUMN `parent` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `TickTickTask` ADD COLUMN `kind` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `TickTickTask` ADD COLUMN `durationMin` INTEGER NOT NULL DEFAULT 0")
    }
}
