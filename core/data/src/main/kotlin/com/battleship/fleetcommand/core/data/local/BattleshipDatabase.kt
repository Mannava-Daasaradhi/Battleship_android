package com.battleship.fleetcommand.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.battleship.fleetcommand.core.data.local.dao.BoardStateDao
import com.battleship.fleetcommand.core.data.local.dao.GameDao
import com.battleship.fleetcommand.core.data.local.dao.ShotDao
import com.battleship.fleetcommand.core.data.local.dao.StatsDao
import com.battleship.fleetcommand.core.data.local.entity.BoardStateEntity
import com.battleship.fleetcommand.core.data.local.entity.GameEntity
import com.battleship.fleetcommand.core.data.local.entity.ShotEntity
import com.battleship.fleetcommand.core.data.local.entity.StatsEntity

/**
 * Single Room database for Battleship Fleet Command.
 * Version 1 — no migrations required on first install.
 * Created via [BattleshipDatabase.create] and provided as a singleton
 * by DatabaseModule in :app.
 *
 * Schema exported to app/schemas/ for version-control and migration testing.
 * Section 13 — Data Layer / Room Database.
 */
@Database(
    entities = [
        GameEntity::class,
        ShotEntity::class,
        StatsEntity::class,
        BoardStateEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BattleshipDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao
    abstract fun shotDao(): ShotDao
    abstract fun statsDao(): StatsDao
    abstract fun boardStateDao(): BoardStateDao

    companion object {
        fun create(context: Context): BattleshipDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                BattleshipDatabase::class.java,
                "battleship_db"
            ).build()
    }
}