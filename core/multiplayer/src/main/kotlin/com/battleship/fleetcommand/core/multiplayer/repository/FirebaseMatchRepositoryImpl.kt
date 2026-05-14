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
    private val mapper: GameSyncMapper
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
        } catch (e: Exception) {}
    }

    override suspend fun claimVictory(gameId: String): Result<Unit> {
        val myUid = authManager.currentUid ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val metaRef = database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}")
            metaRef.child(FirebaseSchema.WINNER).setValue(myUid).await()
            metaRef.child(FirebaseSchema.STATUS).setValue(FirebaseSchema.STATUS_FINISHED).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forfeit(gameId: String, opponentUid: String): Result<Unit> {
        return try {
            val metaRef = database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}")
            metaRef.child(FirebaseSchema.WINNER).setValue(opponentUid).await()
            metaRef.child(FirebaseSchema.STATUS).setValue(FirebaseSchema.STATUS_FINISHED).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Legacy single-field write. Kept to satisfy the interface contract and for test
     * compatibility. In production the VM calls [commitShotAndFlipTurn] instead.
     */
    override suspend fun writeShotResult(
        gameId: String,
        shooterUid: String,
        shotIndex: Int,
        result: FireResult,
        shipId: String?,
    ) {
        try {
            val shotsRef = database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.SHOTS}/$shooterUid")
            val snapshot = shotsRef.get().await()
            val pushKey = snapshot.children.elementAtOrNull(shotIndex)?.key ?: return

            val shotNode = shotsRef.child(pushKey)
            shotNode.child(FirebaseSchema.SHOT_RESULT).setValue(result.toSchemaString()).await()
            shotNode.child(FirebaseSchema.SHOT_SHIP_ID).setValue(shipId).await()
        } catch (e: Exception) {
            Timber.w(e, "writeShotResult failed for game=$gameId shooter=$shooterUid index=$shotIndex")
        }
    }

    /**
     * Atomically writes the shot result + shipId AND flips currentTurn in a SINGLE
     * Firebase multi-path updateChildren() call.
     *
     * Why this matters:
     * The old pattern was writeShotResult() + flipTurn() — two separate .await() calls.
     * Firebase's ValueEventListener fires after EACH write, so both devices saw a
     * partial intermediate state between the two writes:
     *   - After write 1 (result written): shot shows resolved, but turn not yet flipped.
     *     Attacker's board briefly thought it was still their turn (lag/bug).
     *     Attacker's buildOpponentBoard ran with shipId=null → sunk cells stayed red.
     *   - After write 2 (shipId written): board re-rendered with correct shipId.
     *     But the damage is done — the intermediate render caused visible jank.
     *
     * updateChildren() writes all paths in a single operation → single listener event
     * → both boards rebuild once with complete, consistent data.
     *
     * Firebase path structure written:
     *   games/{gameId}/shots/{shooterUid}/{pushKey}/result  = "hit"|"miss"|"sunk"
     *   games/{gameId}/shots/{shooterUid}/{pushKey}/shipId  = "CARRIER"|null|etc.
     *   games/{gameId}/meta/currentTurn                     = nextTurnUid
     */
    override suspend fun commitShotAndFlipTurn(
        gameId: String,
        shooterUid: String,
        shotIndex: Int,
        result: FireResult,
        shipId: String?,
        nextTurnUid: String,
    ): Result<Unit> {
        return try {
            // Resolve the push-key for this shot by index.
            // Firebase uses push-keys (chronological), so elementAtOrNull by index is correct.
            val shotsRef = database.getReference(
                "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.SHOTS}/$shooterUid"
            )
            val snapshot = shotsRef.get().await()
            val pushKey = snapshot.children.elementAtOrNull(shotIndex)?.key
                ?: return Result.failure(Exception("Shot push-key not found at index $shotIndex"))

            // Build a single multi-path update map.
            // Keys are RELATIVE to the game root (/games/{gameId}).
            val gameRootRef = database.getReference("${FirebaseSchema.GAMES}/$gameId")

            val updates: Map<String, Any?> = buildMap {
                put(
                    "${FirebaseSchema.SHOTS}/$shooterUid/$pushKey/${FirebaseSchema.SHOT_RESULT}",
                    result.toSchemaString()
                )
                put(
                    "${FirebaseSchema.SHOTS}/$shooterUid/$pushKey/${FirebaseSchema.SHOT_SHIP_ID}",
                    shipId   // null is valid here — Firebase will write null (clearing stale values)
                )
                put(
                    "${FirebaseSchema.META}/${FirebaseSchema.CURRENT_TURN}",
                    nextTurnUid
                )
            }

            gameRootRef.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.w(e, "commitShotAndFlipTurn failed game=$gameId shooter=$shooterUid index=$shotIndex")
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
}