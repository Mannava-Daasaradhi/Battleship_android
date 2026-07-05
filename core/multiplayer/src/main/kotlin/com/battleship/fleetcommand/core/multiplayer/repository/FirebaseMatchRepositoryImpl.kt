// FILE: core/multiplayer/src/main/kotlin/com/battleship/fleetcommand/core/multiplayer/repository/FirebaseMatchRepositoryImpl.kt

package com.battleship.fleetcommand.core.multiplayer.repository

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import com.battleship.fleetcommand.core.domain.multiplayer.GameCreationResult
import com.battleship.fleetcommand.core.domain.multiplayer.JoinResult
import com.battleship.fleetcommand.core.domain.multiplayer.OnlineGameState
import com.battleship.fleetcommand.core.domain.multiplayer.ShotData
import com.battleship.fleetcommand.core.domain.multiplayer.ShotResolutionResult
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.multiplayer.FirebaseSchema
import com.battleship.fleetcommand.core.multiplayer.auth.FirebaseAuthManager
import com.battleship.fleetcommand.core.multiplayer.mapper.GameSyncMapper
import com.battleship.fleetcommand.core.multiplayer.mapper.ShipPlacementDto
import com.battleship.fleetcommand.core.multiplayer.mapper.toSchemaString
import com.battleship.fleetcommand.core.multiplayer.matchmaking.MatchmakingRepository
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseMatchRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
    private val authManager: FirebaseAuthManager,
    private val matchmakingRepository: MatchmakingRepository,
    private val mapper: GameSyncMapper,
    private val functions: FirebaseFunctions,
) : FirebaseMatchRepository {

    private var lastShotTimestampMs: Long = 0L

    override fun createGame(playerName: String): Flow<GameCreationResult> = flow {
        emit(matchmakingRepository.createGame(playerName))
    }

    override fun joinGame(roomCode: String, playerName: String): Flow<JoinResult> = flow {
        emit(matchmakingRepository.joinGame(roomCode, playerName))
    }

    override fun observeGameState(gameId: String): Flow<OnlineGameState> = callbackFlow {
        val myUid = authManager.currentUid ?: run {
            close(IllegalStateException("Not authenticated"))
            return@callbackFlow
        }
        val gameRef = database.getReference("${FirebaseSchema.GAMES}/$gameId")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = mapper.mapGameSnapshot(snapshot, myUid)
                if (state != null) {
                    trySend(state)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        gameRef.addValueEventListener(listener)
        awaitClose { gameRef.removeEventListener(listener) }
    }

    override suspend fun submitShipPlacement(gameId: String, ships: List<ShipPlacement>): Result<Unit> {
        val myUid = authManager.currentUid ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val dtos: List<ShipPlacementDto> = ships.map { placement ->
                ShipPlacementDto(
                    shipId      = placement.shipId.name,
                    row         = placement.headCoord.rowOf(),
                    col         = placement.headCoord.colOf(),
                    orientation = if (placement.orientation is Orientation.Horizontal) "H" else "V"
                )
            }
            val shipsJson = Json.encodeToString(dtos)

            val gameRef = database.getReference("${FirebaseSchema.GAMES}/$gameId")
            gameRef.child("${FirebaseSchema.BOARDS}/$myUid/${FirebaseSchema.BOARD_SHIPS}")
                .setValue(shipsJson).await()
            gameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.PLAYER_READY}")
                .setValue(true).await()

            try {
                val playersSnapshot = gameRef.child(FirebaseSchema.PLAYERS).get().await()
                val playerEntries = playersSnapshot.children.toList()
                val bothReady = playerEntries.size >= 2 &&
                        playerEntries.all { playerSnap ->
                            playerSnap.child(FirebaseSchema.PLAYER_READY).getValue(Boolean::class.java) == true
                        }

                if (bothReady) {
                    val metaRef = gameRef.child(FirebaseSchema.META)
                    val currentStatus = metaRef.child(FirebaseSchema.STATUS).get().await().getValue(String::class.java)

                    if (currentStatus == FirebaseSchema.STATUS_SETUP) {
                        metaRef.child(FirebaseSchema.STATUS).setValue(FirebaseSchema.STATUS_BATTLE).await()
                    }
                }
            } catch (readyCheckEx: Exception) {
                Timber.w(readyCheckEx, "Ready-check failed")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fireShot(gameId: String, coord: Coord): Result<Unit> {
        val myUid = authManager.currentUid ?: return Result.failure(Exception("Not authenticated"))
        val now = System.currentTimeMillis()
        if (now - lastShotTimestampMs < GameConstants.SHOT_RATE_LIMIT_MS) {
            return Result.failure(Exception("Shot rate limit exceeded"))
        }
        lastShotTimestampMs = now

        return try {
            val shotData = mapOf(
                FirebaseSchema.SHOT_ROW       to coord.rowOf(),
                FirebaseSchema.SHOT_COL       to coord.colOf(),
                FirebaseSchema.SHOT_RESULT    to null,
                FirebaseSchema.SHOT_SHIP_ID   to null,
                FirebaseSchema.SHOT_TIMESTAMP to ServerValue.TIMESTAMP
            )
            database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.SHOTS}/$myUid")
                .push()
                .setValue(shotData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeOpponentShots(gameId: String): Flow<List<ShotData>> = callbackFlow {
        val myUid = authManager.currentUid ?: run {
            close(IllegalStateException("Not authenticated"))
            return@callbackFlow
        }
        val shotsRef = database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.SHOTS}")
        val shotsByShooter = mutableMapOf<String, List<ShotData>>()

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val shooterUid = snapshot.key ?: return
                if (shooterUid == myUid) return
                val shots = snapshot.children.mapNotNull { mapper.mapShotSnapshot(it) }
                shotsByShooter[shooterUid] = shots
                trySend(shotsByShooter.values.flatten())
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val shooterUid = snapshot.key ?: return
                if (shooterUid == myUid) return
                val shots = snapshot.children.mapNotNull { mapper.mapShotSnapshot(it) }
                shotsByShooter[shooterUid] = shots
                trySend(shotsByShooter.values.flatten())
            }

            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
        }

        shotsRef.addChildEventListener(listener)
        awaitClose { shotsRef.removeEventListener(listener) }
    }

    override suspend fun setPresence(gameId: String, connected: Boolean) {
        val myUid = authManager.currentUid ?: return
        try {
            val playerRef = database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.PLAYERS}/$myUid")
            playerRef.child(FirebaseSchema.PLAYER_CONNECTED).setValue(connected).await()
            playerRef.child(FirebaseSchema.PLAYER_LAST_SEEN).setValue(ServerValue.TIMESTAMP).await()
        } catch (_: Exception) {}
    }

    // ── Server-side operations via Cloud Functions (with client-side fallback) ───

    override suspend fun claimVictory(gameId: String): Result<Unit> {
        if (!SERVER_AUTHORITATIVE) return claimVictoryClientSide(gameId)
        return try {
            functions.getHttpsCallable("claimVictory")
                .call(mapOf("gameId" to gameId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.w(e, "claimVictory Cloud Function unavailable; falling back to direct write")
            claimVictoryClientSide(gameId)
        }
    }

    private suspend fun claimVictoryClientSide(gameId: String): Result<Unit> {
        val myUid = authManager.currentUid ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val updates = mapOf<String, Any?>(
                "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}/${FirebaseSchema.WINNER}" to myUid,
                "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}/${FirebaseSchema.STATUS}" to FirebaseSchema.STATUS_FINISHED,
            )
            database.reference.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "claimVictory client-side fallback failed for game=$gameId")
            Result.failure(e)
        }
    }

    override suspend fun forfeit(gameId: String): Result<Unit> {
        if (!SERVER_AUTHORITATIVE) return forfeitClientSide(gameId)
        return try {
            functions.getHttpsCallable("forfeit")
                .call(mapOf("gameId" to gameId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.w(e, "forfeit Cloud Function unavailable; falling back to direct write")
            forfeitClientSide(gameId)
        }
    }

    private suspend fun forfeitClientSide(gameId: String): Result<Unit> {
        val myUid = authManager.currentUid ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val metaRef = database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}")
            val metaSnap = metaRef.get().await()
            val hostUid = metaSnap.child(FirebaseSchema.HOST_UID).getValue(String::class.java)
            val guestUid = metaSnap.child(FirebaseSchema.GUEST_UID).getValue(String::class.java)
            val opponentUid = when (myUid) {
                hostUid -> guestUid
                guestUid -> hostUid
                else -> null
            } ?: return Result.failure(Exception("No opponent found for forfeit"))

            val updates = mapOf<String, Any?>(
                "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}/${FirebaseSchema.WINNER}" to opponentUid,
                "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}/${FirebaseSchema.STATUS}" to FirebaseSchema.STATUS_FINISHED,
            )
            database.reference.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "forfeit client-side fallback failed for game=$gameId")
            Result.failure(e)
        }
    }

    override suspend fun resolveShotServerSide(
        gameId: String,
        shooterUid: String,
        shotIndex: Int,
        row: Int,
        col: Int,
    ): Result<ShotResolutionResult> {
        // On Spark (no Cloud Functions) the defender resolves shots on-device instead —
        // signal failure so OnlineGameViewModel falls back to resolveShotClientSide().
        if (!SERVER_AUTHORITATIVE) {
            return Result.failure(IllegalStateException("Client-authoritative mode: resolve on device"))
        }
        return try {
            val data = mapOf(
                "gameId"     to gameId,
                "shooterUid" to shooterUid,
                "shotIndex"  to shotIndex,
                "row"        to row,
                "col"        to col,
            )
            val result = functions.getHttpsCallable("resolveShot")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val resultMap = result.data as? Map<String, Any?> ?: return Result.failure(Exception("Invalid response"))
            val resultStr = resultMap["result"] as? String ?: return Result.failure(Exception("Missing result"))
            val shipId = resultMap["shipId"] as? String

            val fireResult = when (resultStr) {
                "hit"  -> FireResult.HIT
                "sunk" -> FireResult.SUNK
                else   -> FireResult.MISS
            }

            Result.success(ShotResolutionResult(fireResult, shipId))
        } catch (e: Exception) {
            Timber.w(e, "resolveShot Cloud Function failed for game=$gameId shooter=$shooterUid index=$shotIndex")
            Result.failure(e)
        }
    }

    override suspend fun flipTurn(gameId: String, nextPlayerUid: String): Result<Unit> {
        return try {
            database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}/${FirebaseSchema.CURRENT_TURN}")
                .setValue(nextPlayerUid).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeShotResolution(
        gameId: String,
        shooterUid: String,
        pushKey: String,
        result: FireResult,
        shipId: String?,
        nextTurnUid: String,
    ): Result<Unit> {
        if (pushKey.isBlank()) {
            return Result.failure(IllegalArgumentException("writeShotResolution requires a non-blank pushKey"))
        }
        return try {
            val basePath = "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.SHOTS}/$shooterUid/$pushKey"
            val updates = mutableMapOf<String, Any?>(
                "$basePath/${FirebaseSchema.SHOT_RESULT}" to result.toSchemaString(),
                "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}/${FirebaseSchema.CURRENT_TURN}" to nextTurnUid,
            )
            if (shipId != null) {
                updates["$basePath/${FirebaseSchema.SHOT_SHIP_ID}"] = shipId
            }
            database.reference.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "writeShotResolution failed for game=$gameId shooter=$shooterUid pushKey=$pushKey")
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Server-authoritative online play — shot resolution, victory and forfeit verified by
         * Cloud Functions — requires the Firebase **Blaze** plan. On the free **Spark** plan there
         * are no Cloud Functions, so the clients resolve shots and decide the winner themselves
         * (client-authoritative). This keeps online multiplayer working for free at the cost of
         * server-side cheat prevention.
         *
         * To switch to the secure server-authoritative flow later: upgrade to Blaze, run
         * `firebase deploy --only functions`, then set this to `true` and ship an app update.
         *
         * Kept a plain `val` (not `const`) so the branch guards stay runtime checks and don't
         * trip Kotlin's unreachable-code analysis under -allWarningsAsErrors.
         */
        private val SERVER_AUTHORITATIVE = false
    }
}
