package com.battleship.fleetcommand.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.battleship.fleetcommand.core.data.local.entity.GameEntity

/**
 * Room DAO for game session CRUD.
 * Section 13 — Data Layer / DAOs.
 */
@Dao
interface GameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: GameEntity)

    @Query("SELECT * FROM games WHERE id = :gameId LIMIT 1")
    suspend fun getById(gameId: String): GameEntity?

    @Query("SELECT * FROM games WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getUnfinishedGame(): GameEntity?

    @Query(
        """
        UPDATE games
        SET finishedAt   = :finishedAt,
            winner       = :winner,
            durationSecs = :durationSecs
        WHERE id = :gameId
        """
    )
    suspend fun finishGame(
        gameId: String,
        finishedAt: Long,
        winner: String,
        durationSecs: Long
    )
}