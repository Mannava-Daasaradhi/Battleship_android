package com.battleship.fleetcommand.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.battleship.fleetcommand.core.data.local.entity.BoardStateEntity

/**
 * Room DAO for board state (ship placement) persistence.
 * One row per (gameId, slot) — upsert via REPLACE strategy.
 * Section 13 — Data Layer / DAOs.
 */
@Dao
interface BoardStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(boardState: BoardStateEntity)

    @Query(
        """
        SELECT * FROM board_states
        WHERE gameId = :gameId
          AND slot   = :slot
        LIMIT 1
        """
    )
    suspend fun getByGameIdAndSlot(gameId: String, slot: String): BoardStateEntity?
}