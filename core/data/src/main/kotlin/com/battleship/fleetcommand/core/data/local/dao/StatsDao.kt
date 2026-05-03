package com.battleship.fleetcommand.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.battleship.fleetcommand.core.data.local.entity.StatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for career statistics.
 * The stats table always holds exactly one row (id = 1).
 * [observe] returns a Flow so the Stats screen updates in real time.
 * Section 13 — Data Layer / DAOs.
 */
@Dao
interface StatsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stats: StatsEntity)

    @Update
    suspend fun update(stats: StatsEntity)

    @Query("SELECT * FROM stats WHERE id = 1 LIMIT 1")
    suspend fun get(): StatsEntity?

    @Query("SELECT * FROM stats WHERE id = 1 LIMIT 1")
    fun observe(): Flow<StatsEntity?>
}