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

    /**
     * Writes the resolved result (and optional shipId) for a shot the opponent fired.
     *
     * [shipId] must be non-null for HIT and SUNK outcomes — it identifies which ship
     * was struck so the attacker's board can render SUNK cells correctly once the final
     * SUNK result arrives.  Pass null only for MISS.
     *
     * Kept for backwards compatibility with tests; prefer [commitShotAndFlipTurn] for
     * production use so result + turn-flip are written atomically.
     */
    suspend fun writeShotResult(
        gameId: String,
        shooterUid: String,
        shotIndex: Int,
        result: FireResult,
        shipId: String? = null,
    )

    /**
     * Atomically writes the shot result + shipId AND flips currentTurn in a single
     * Firebase multi-path update (one network round trip, one ValueEventListener event).
     *
     * This replaces the previous two-call pattern of writeShotResult() + flipTurn()
     * which produced an observable intermediate state between writes, causing:
     *   1. Turn lag — the attacker briefly saw their turn re-enable before it flipped.
     *   2. Partial sunk rendering — the board rebuilt between result-write and
     *      shipId-write, leaving shipId=null in the snapshot so sunk cells stayed red.
     *
     * [nextTurnUid] is the UID of the player whose turn it becomes (the attacker, i.e.
     * the player who fired the shot, since the turn returns to them after the defender
     * resolves it). Wait — actually it's the ATTACKER who just fired; after the defender
     * resolves, the turn flips to the DEFENDER so they can fire next.
     * Caller must pass the correct nextTurnUid.
     */
    suspend fun commitShotAndFlipTurn(
        gameId: String,
        shooterUid: String,
        shotIndex: Int,
        result: FireResult,
        shipId: String?,
        nextTurnUid: String,
    ): Result<Unit>

    suspend fun flipTurn(gameId: String, nextPlayerUid: String): Result<Unit>
}