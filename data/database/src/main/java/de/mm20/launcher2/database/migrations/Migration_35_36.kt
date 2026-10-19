package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** TickTick habits are gone: the Tasks app replacing TickTick has none. */
class Migration_35_36 : Migration(35, 36) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `TickTickHabit`")
        connection.execSQL("DROP TABLE IF EXISTS `TickTickCheckin`")
    }
}
