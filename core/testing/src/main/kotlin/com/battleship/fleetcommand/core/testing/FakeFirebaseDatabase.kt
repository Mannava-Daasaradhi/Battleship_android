// core/testing/src/main/kotlin/com/battleship/fleetcommand/core/testing/FakeFirebaseDatabase.kt

package com.battleship.fleetcommand.core.testing

import com.battleship.fleetcommand.core.domain.engine.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.engine.ShipPlacement
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import com.battleship.fleetcommand.core.domain.multiplayer.GameCreationResult
import com.battleship.fleetcommand.core.domain.multiplayer.JoinResult
import com.battleship.fleetcommand.core.domain.multiplayer.OnlineGameState
import com.battleship.fleetcommand.core.domain.multiplayer.PlayerData
import com.battleship.fleetcommand.core.domain.multiplayer.ShotData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.util.UUID

/**
 * Pure Kotlin in-memory fake that implements [FirebaseMatchRepository].
 *
 * Stores game state in a flat map keyed by gameId. Test helpers allow
 * injecting opponent actions (shots, disconnects, status advances) without
 * touching Firebase at all.
 */
class FakeFirebaseDatabase : FirebaseMatchRepository {

    // ── In-memory store ───────────────────────────────────────────────────────

    private data class GameNode(
        val gameId: String,
        val roomCode: String,
        var hostUid: String,
        var guestUid: String? = null,
        var status: String = "waiting",
        var currentTurn: String = "",
        var winner: String? = null,
        val players: MutableMap<String, PlayerNode> = mutableMapOf(),
        val shots: MutableMap<String, MutableList<ShotNode>> = mutableMapOf(),
        val ships: MutableMap<String, String> = mutableMapOf()   // uid → JSON string
    )

    private data class PlayerNode(
        val name: String,
        var ready: Boolean = false,
        var connected: Boolean = true,
        var lastSeen: Long = System.currentTimeMillis()
    )

    private data class ShotNode(
        val pushKey: String,
        val row: Int,
        val col: Int,
        var result: String? = null,
        var shipId: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Indexed by gameId
    private val games: MutableMap<String, GameNode> = mutableMapOf()

    // One StateFlow per game; screens observe this
    private val stateFlows: MutableMap<String, MutableStateFlow<GameNode?>> = mutableMapOf()

    // For opponent-shot observation (emitted via test helper)
    private val opponentShotFlows: MutableMap<String, MutableStateFlow<ShotData?>> = mutableMapOf()

    // Simulated "my UID" — tests can set this to control which player acts
    var myUid: String = "fake-uid-host"

    // ── FirebaseMatchRepository ───────────────────────────────────────────────

    override fun createGame(playerName: String): Flow<GameCreationResult> = flow {
        val gameId  = UUID.randomUUID().toString().take(16)
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
        opponentShotFlows[gameId] = MutableStateFlow(null)
        emit(GameCreationResult.Success(gameId = gameId, roomCode = roomCode))
    }

    override fun joinGame(roomCode: String, playerName: String): Flow<JoinResult> = flow {
        val node = games.values.firstOrNull {
            it.roomCode.equals(roomCode.uppercase(), ignoreCase = true)
        }
        when {
            node == null             -> emit(JoinResult.NotFound)
            node.status != "waiting" -> emit(JoinResult.GameAlreadyStarted)
            else -> {
                node.guestUid = myUid
                node.players[myUid] = PlayerNode(name = playerName)
                stateFlows[node.gameId]?.value = node
                emit(JoinResult.Success(gameId = node.gameId))
            }
        }
    }

    override fun observeGameState(gameId: String): Flow<OnlineGameState> {
        val flow = stateFlows.getOrPut(gameId) { MutableStateFlow(games[gameId]) }
        return flow.mapNotNull { node -> node?.toOnlineGameState(myUid) }
    }

    override suspend fun submitShipPlacement(
        gameId: String,
        ships: List<ShipPlacement>
    ): Result<Unit> {
        val node = games[gameId] ?: return Result.failure(Exception("Game not found: $gameId"))
        node.ships[myUid] = ships.toString() // simplified: store as toString for fake
        node.players[myUid]?.ready = true
        stateFlows[gameId]?.value = node
        return Result.success(Unit)
    }

    override suspend fun fireShot(gameId: String, coord: Coord): Result<Unit> {
        val node = games[gameId] ?: return Result.failure(Exception("Game not found: $gameId"))
        val key  = UUID.randomUUID().toString().take(8)
        val list = node.shots.getOrPut(myUid) { mutableListOf() }
        list.add(ShotNode(pushKey = key, row = coord.rowOf(), col = coord.colOf()))
        stateFlows[gameId]?.value = node
        return Result.success(Unit)
    }

    override fun observeOpponentShots(gameId: String): Flow<ShotData> {
        val sf = opponentShotFlows.getOrPut(gameId) { MutableStateFlow(null) }
        return sf.mapNotNull { it }
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

    override suspend fun writeShotResult(
        gameId: String,
        shooterUid: String,
        shotPushKey: String,
        result: FireResult,
        shipId: String?
    ) {
        val node = games[gameId] ?: return
        val shot = node.shots[shooterUid]?.firstOrNull { it.pushKey == shotPushKey } ?: return
        shot.result = result.name.lowercase()
        shot.shipId = shipId
        stateFlows[gameId]?.value = node
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /** Inject an opponent shot into the game; triggers [observeOpponentShots] collectors. */
    fun simulateOpponentShot(gameId: String, coord: Coord) {
        val node       = games[gameId] ?: return
        val opponentUid = if (node.hostUid == myUid) node.guestUid ?: return else node.hostUid
        val key        = UUID.randomUUID().toString().take(8)
        val list       = node.shots.getOrPut(opponentUid) { mutableListOf() }
        list.add(ShotNode(pushKey = key, row = coord.rowOf(), col = coord.colOf()))
        stateFlows[gameId]?.value = node

        opponentShotFlows[gameId]?.value = ShotData(
            pushKey    = key,
            shooterUid = opponentUid,
            row        = coord.rowOf(),
            col        = coord.colOf(),
            result     = null,
            shipId     = null,
            timestamp  = System.currentTimeMillis()
        )
    }

    /** Sets the opponent's connected flag to false, triggers state observers. */
    fun simulateOpponentDisconnect(gameId: String) {
        val node        = games[gameId] ?: return
        val opponentUid = if (node.hostUid == myUid) node.guestUid ?: return else node.hostUid
        node.players[opponentUid]?.connected = false
        stateFlows[gameId]?.value = node
    }

    /** Advances the game status (e.g. "waiting" → "battle") and triggers state observers. */
    fun advanceToStatus(gameId: String, status: String) {
        val node = games[gameId] ?: return
        node.status = status
        stateFlows[gameId]?.value = node
    }

    /** Returns the game node directly — useful for structural assertions in tests. */
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
            "players"     to node.players,
            "shots"       to node.shots
        )
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private fun GameNode.toOnlineGameState(myUid: String): OnlineGameState {
        val opponentUid = if (hostUid == myUid) guestUid ?: "" else hostUid
        val myShots     = shots[myUid]?.map { it.toShotData(myUid) } ?: emptyList()
        val oppShots    = shots[opponentUid]?.map { it.toShotData(opponentUid) } ?: emptyList()
        return OnlineGameState(
            gameId        = gameId,
            myUid         = myUid,
            opponentUid   = opponentUid,
            status        = status,
            currentTurn   = currentTurn,
            winner        = winner,
            players       = players.mapValues { (uid, p) ->
                PlayerData(uid = uid, name = p.name, ready = p.ready, connected = p.connected, lastSeen = p.lastSeen)
            },
            myShots       = myShots,
            opponentShots = oppShots
        )
    }

    private fun ShotNode.toShotData(shooterUid: String) = ShotData(
        pushKey    = pushKey,
        shooterUid = shooterUid,
        row        = row,
        col        = col,
        result     = result,
        shipId     = shipId,
        timestamp  = timestamp
    )

    private fun generateRoomCode(): String {
        val charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { charset.random() }.joinToString("")
    }
}