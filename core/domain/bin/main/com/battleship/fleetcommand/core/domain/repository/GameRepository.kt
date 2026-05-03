// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/repository/GameRepository.kt
package com.battleship.fleetcommand.core.domain.repository

import com.battleship.fleetcommand.core.domain.model.Game
import com.battleship.fleetcommand.core.domain.model.Shot
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for game session persistence.
 * Implemented in :core:data (GameRepositoryImpl via Room).
 * Zero Android imports — pure Kotlin + coroutines only.
 * Section 13 — Repository Interfaces.
 */
interface GameRepository {
    suspend fun createGame(game: Game): String               // returns gameId (UUID)
    suspend fun saveGame(game: Game)
    suspend fun getGame(gameId: String): Game?
    suspend fun getUnfinishedGame(): Game?
    suspend fun finishGame(gameId: String, winner: PlayerSlot, durationSecs: Long)
    suspend fun saveBoardState(gameId: String, slot: PlayerSlot, placements: List<ShipPlacement>)
    suspend fun getBoardState(gameId: String, slot: PlayerSlot): List<ShipPlacement>?
    suspend fun saveShot(gameId: String, shot: Shot)
    suspend fun getShots(gameId: String): List<Shot>
    fun observeShots(gameId: String, firedBy: PlayerSlot): Flow<List<Shot>>
}