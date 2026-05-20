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

    /**
     * Calls the server-side `claimVictory` Cloud Function which validates that all
     * opponent ship cells have been hit before declaring a winner.
     */
    suspend fun claimVictory(gameId: String): Result<Unit>

    /**
     * Calls the server-side `forfeit` Cloud Function to set the opponent as winner.
     */
    suspend fun forfeit(gameId: String): Result<Unit>

    /**
     * Calls the server-side `resolveShot` Cloud Function. The server reads the
     * defender's board (using admin SDK), resolves hit/miss/sunk, writes the result,
     * and flips the turn — all in a single trusted operation.
     *
     * Called by the DEFENDER when an opponent shot arrives without a result.
     */
    suspend fun resolveShotServerSide(
        gameId: String,
        shooterUid: String,
        shotIndex: Int,
        row: Int,
        col: Int,
    ): Result<ShotResolutionResult>

    suspend fun flipTurn(gameId: String, nextPlayerUid: String): Result<Unit>
}

/**
 * Result returned by the server-side shot resolution Cloud Function.
 */
data class ShotResolutionResult(
    val result: FireResult,
    val shipId: String?,
)