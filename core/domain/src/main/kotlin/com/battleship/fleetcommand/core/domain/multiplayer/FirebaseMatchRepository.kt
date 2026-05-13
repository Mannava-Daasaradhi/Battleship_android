// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/multiplayer/FirebaseMatchRepository.kt
package com.battleship.fleetcommand.core.domain.multiplayer

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import kotlinx.coroutines.flow.Flow

interface FirebaseMatchRepository {
    fun createGame(playerName: String): Flow<GameCreationResult>
    fun joinGame(roomCode: String, playerName: String): Flow<JoinResult>
    fun observeGameState(gameId: String): Flow<OnlineGameState>
    suspend fun submitShipPlacement(gameId: String, ships: List<ShipPlacement>): Result<Unit>
    suspend fun fireShot(gameId: String, coord: Coord): Result<Unit>
    fun observeOpponentShots(gameId: String): Flow<List<ShotData>>
    suspend fun setPresence(gameId: String, connected: Boolean)
    suspend fun claimVictory(gameId: String): Result<Unit>
    suspend fun forfeit(gameId: String, opponentUid: String): Result<Unit>
    suspend fun writeShotResult(
        gameId: String,
        shooterUid: String,
        shotIndex: Int,
        result: FireResult
    )
    suspend fun flipTurn(gameId: String, nextPlayerUid: String): Result<Unit>
}