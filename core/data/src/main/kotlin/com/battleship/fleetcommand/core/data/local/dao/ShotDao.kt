package com.battleship.fleetcommand.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.battleship.fleetcommand.core.data.local.entity.ShotEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for shot records.
 * [observeByGameIdAndFiredBy] is backed by Room's Flow support so callers
 * receive live updates without polling.
 * Section 13 — Data Layer / DAOs.
 */
@Dao
interface ShotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shot: ShotEntity)

    @Query("SELECT * FROM shots WHERE gameId = :gameId ORDER BY shotIndex ASC")
    suspend fun getByGameId(gameId: String): List<ShotEntity>

    @Query(
        """
        SELECT * FROM shots
        WHERE gameId  = :gameId
          AND firedBy = :firedBy
        ORDER BY shotIndex ASC
        """
    )
    fun observeByGameIdAndFiredBy(gameId: String, firedBy: String): Flow<List<ShotEntity>>
}