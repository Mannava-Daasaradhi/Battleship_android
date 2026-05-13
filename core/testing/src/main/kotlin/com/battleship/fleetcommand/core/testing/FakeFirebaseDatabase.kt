// FILE: core/testing/src/main/kotlin/com/battleship/fleetcommand/core/testing/FakeFirebaseDatabase.kt

package com.battleship.fleetcommand.core.testing

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import com.battleship.fleetcommand.core.domain.multiplayer.GameCreationResult
import com.battleship.fleetcommand.core.domain.multiplayer.JoinResult
import com.battleship.fleetcommand.core.domain.multiplayer.OnlineGameState
import com.battleship.fleetcommand.core.domain.multiplayer.PlayerData
import com.battleship.fleetcommand.core.domain.multiplayer.ShotData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import java.util.UUID

/**
 * Pure Kotlin in-memory fake implementing [FirebaseMatchRepository].
 * No Firebase dependencies — safe for pure JVM unit tests.
 * Test helpers allow injecting opponent actions without touching Firebase.
 *
 * Mirrors real production behaviour:
 *  - joinGame advances status "waiting" → "setup" (both in lobby).
 *  - submitShipPlacement advances status "setup" → "battle" when both players ready.
 *  - joinGame is idempotent: if myUid == existing guestUid, re-join succeeds.
 */
class FakeFirebaseDatabase : FirebaseMatchRepository {

    // ── Internal storage ──────────────────────────────────────────────────────

    private data class GameNode(
        val gameId: String,
        val roomCode: String,
        var hostUid: String,
        var guestUid: String? = null,
        var status: String = "waiting",
        var currentTurn: String = "",
        var winner: String? = null,
        val players: MutableMap<String, PlayerNode> = mutableMapOf(),
        // shots keyed by shooterUid → ordered list
        val shots: MutableMap<String, MutableList<ShotNode>> = mutableMapOf(),
        val ships: MutableMap<String, String> = mutableMapOf()
    )

    private data class PlayerNode(
        val name: String,
        var ready: Boolean = false,
        var connected: Boolean = true,
        var lastSeen: Long = System.currentTimeMillis()
    )

    private data class ShotNode(
        val index: Int,          // sequential index within this shooter's list
        val row: Int,
        val col: Int,
        var result: FireResult? = null,
        var shipId: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val games = mutableMapOf<String, GameNode>()
    private val stateFlows = mutableMapOf<String, MutableStateFlow<GameNode?>>()

    /** Simulated local uid — set this in tests to control which player is "me". */
    var myUid: String = "fake-uid-host"

    // ── FirebaseMatchRepository ───────────────────────────────────────────────

    override fun createGame(playerName: String): Flow<GameCreationResult> = flow {
        val gameId   = UUID.randomUUID().toString().take(16)
        val roomCode = generateRoomCode()
        val node = GameNode(
            gameId      = gameId,
            roomCode    = roomCode,
            hostUid     = myUid,
            currentTurn = myUid
        )
        node.players[myUid] = PlayerNode(name = playerName)
        games[gameId] = node
        stateFlows[gameId] = MutableStateFlow(node)
        emit(GameCreationResult.Success(gameId = gameId, roomCode = roomCode))
    }

    override fun joinGame(roomCode: String, playerName: String): Flow<JoinResult> = flow {
        val node = games.values.firstOrNull {
            it.roomCode.equals(roomCode.trim().uppercase(), ignoreCase = false)
        }
        when {
            node == null -> emit(JoinResult.NotFound)

            // Idempotent re-join: the guest is already us (e.g. retry after network blip).
            node.guestUid == myUid -> {
                // Just refresh player data and re-emit success.
                node.players[myUid] = PlayerNode(name = playerName)
                stateFlows[node.gameId]?.value = node
                emit(JoinResult.Success(gameId = node.gameId))
            }

            // Another guest is already in the slot, or game is not in a joinable state.
            node.guestUid != null -> emit(JoinResult.GameAlreadyStarted)
            node.status != "waiting" -> emit(JoinResult.GameAlreadyStarted)

            else -> {
                // Claim the guest slot and advance status waiting → setup.
                node.guestUid = myUid
                node.players[myUid] = PlayerNode(name = playerName)
                node.status = "setup"   // mirrors MatchmakingRepository.joinGame Step 7
                stateFlows[node.gameId]?.value = node
                emit(JoinResult.Success(gameId = node.gameId))
            }
        }
    }

    override fun observeGameState(gameId: String): Flow<OnlineGameState> {
        val sf = stateFlows.getOrPut(gameId) { MutableStateFlow(games[gameId]) }
        return sf.mapNotNull { node -> node?.toOnlineGameState() }
    }

    override suspend fun submitShipPlacement(
        gameId: String,
        ships: List<ShipPlacement>
    ): Result<Unit> {
        val node = games[gameId] ?: return Result.failure(Exception("Game not found: $gameId"))
        node.ships[myUid] = ships.size.toString() // simplified — just record it was submitted
        node.players[myUid]?.ready = true

        // Advance status "setup" → "battle" when both players are ready.
        // Mirrors FirebaseMatchRepositoryImpl.submitShipPlacement ready-check logic.
        if (node.status == "setup" &&
            node.players.size >= 2 &&
            node.players.values.all { it.ready }
        ) {
            node.status = "battle"
        }

        stateFlows[gameId]?.value = node
        return Result.success(Unit)
    }

    override suspend fun fireShot(gameId: String, coord: Coord): Result<Unit> {
        val node = games[gameId] ?: return Result.failure(Exception("Game not found: $gameId"))
        val list  = node.shots.getOrPut(myUid) { mutableListOf() }
        val index = list.size
        list.add(ShotNode(index = index, row = coord.rowOf(), col = coord.colOf()))
        stateFlows[gameId]?.value = node
        return Result.success(Unit)
    }

    // Returns Flow<List<ShotData>> — matches the real interface signature.
    override fun observeOpponentShots(gameId: String): Flow<List<ShotData>> {
        val sf = stateFlows.getOrPut(gameId) { MutableStateFlow(games[gameId]) }
        return sf.mapNotNull { node ->
            node ?: return@mapNotNull null
            val opponentUid = if (node.hostUid == myUid) node.guestUid ?: return@mapNotNull null
                              else node.hostUid
            node.shots[opponentUid]?.map { it.toShotData() } ?: emptyList()
        }
    }

    override suspend fun setPresence(gameId: String, connected: Boolean) {
        val node = games[gameId] ?: return
        node.players[myUid]?.connected = connected
        node.players[myUid]?.lastSeen  = System.currentTimeMillis()
        stateFlows[gameId]?.value = node
    }

    override suspend fun claimVictory(gameId: String): Result<Unit> {
        val node = games[gameId] ?: return Result.failure(Exception("Game not found: $gameId"))
        node.winner = myUid
        node.status = "finished"
        stateFlows[gameId]?.value = node
        return Result.success(Unit)
    }

    // Signature matches the real interface exactly:
    // writeShotResult(gameId, shooterUid, shotIndex: Int, result: FireResult)
    override suspend fun writeShotResult(
        gameId: String,
        shooterUid: String,
        shotIndex: Int,
        result: FireResult
    ) {
        val node = games[gameId] ?: return
        val shot = node.shots[shooterUid]?.getOrNull(shotIndex) ?: return
        shot.result = result
        stateFlows[gameId]?.value = node
    }

    /**
     * BUG 1 FIX — Turn switching.
     * Updates [currentTurn] to [nextPlayerUid] and emits on the state flow so that
     * both [observeGameState] subscribers see the updated turn immediately.
     * Mirrors [FirebaseMatchRepositoryImpl.flipTurn].
     */
    override suspend fun flipTurn(gameId: String, nextPlayerUid: String): Result<Unit> {
        val node = games[gameId]
            ?: return Result.failure(Exception("flipTurn: game not found: $gameId"))
        node.currentTurn = nextPlayerUid
        stateFlows[gameId]?.value = node
        return Result.success(Unit)
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /** Injects a shot from the opponent into the game; triggers [observeOpponentShots]. */
    fun simulateOpponentShot(gameId: String, coord: Coord) {
        val node        = games[gameId] ?: return
        val opponentUid = if (node.hostUid == myUid) node.guestUid ?: return else node.hostUid
        val list        = node.shots.getOrPut(opponentUid) { mutableListOf() }
        list.add(ShotNode(index = list.size, row = coord.rowOf(), col = coord.colOf()))
        stateFlows[gameId]?.value = node
    }

    /** Sets the opponent's connected flag to false, triggers state observers. */
    fun simulateOpponentDisconnect(gameId: String) {
        val node        = games[gameId] ?: return
        val opponentUid = if (node.hostUid == myUid) node.guestUid ?: return else node.hostUid
        node.players[opponentUid]?.connected = false
        stateFlows[gameId]?.value = node
    }

    /** Moves game to the given status string ("waiting"/"setup"/"battle"/"finished"). */
    fun advanceToStatus(gameId: String, status: String) {
        val node = games[gameId] ?: return
        node.status = status
        stateFlows[gameId]?.value = node
    }

    /**
     * Returns raw game metadata as a plain map — useful for structural assertions.
     * Keys: gameId, roomCode, hostUid, guestUid, status, currentTurn, winner.
     */
    fun getGame(gameId: String): Map<String, Any?>? {
        val node = games[gameId] ?: return null
        return mapOf(
            "gameId"      to node.gameId,
            "roomCode"    to node.roomCode,
            "hostUid"     to node.hostUid,
            "guestUid"    to node.guestUid,
            "status"      to node.status,
            "currentTurn" to node.currentTurn,
            "winner"      to node.winner,
            "players"     to node.players.toMap(),
            "shots"       to node.shots.toMap()
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun GameNode.toOnlineGameState(): OnlineGameState {
        val opponentUid = if (hostUid == myUid) guestUid ?: "" else hostUid
        return OnlineGameState(
            gameId        = gameId,
            myUid         = myUid,
            opponentUid   = opponentUid,
            status        = status,
            currentTurn   = currentTurn,
            winner        = winner,
            players       = players.mapValues { (_, p) ->
                PlayerData(
                    name      = p.name,
                    ready     = p.ready,
                    connected = p.connected,
                    lastSeen  = p.lastSeen
                )
            },
            myShots       = shots[myUid]?.map { it.toShotData() } ?: emptyList(),
            opponentShots = shots[opponentUid]?.map { it.toShotData() } ?: emptyList()
        )
    }

    private fun ShotNode.toShotData() = ShotData(
        row       = row,
        col       = col,
        result    = result,
        shipId    = shipId,
        timestamp = timestamp
    )

    private fun generateRoomCode(): String {
        val charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { charset.random() }.joinToString("")
    }
}