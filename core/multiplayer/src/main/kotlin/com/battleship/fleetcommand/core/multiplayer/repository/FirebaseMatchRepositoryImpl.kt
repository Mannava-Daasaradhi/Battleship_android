// core/multiplayer/src/main/kotlin/com/battleship/fleetcommand/core/multiplayer/repository/FirebaseMatchRepositoryImpl.kt

package com.battleship.fleetcommand.core.multiplayer.repository

import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.engine.ShipPlacement
import com.battleship.fleetcommand.core.domain.engine.Coord
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import com.battleship.fleetcommand.core.domain.multiplayer.GameCreationResult
import com.battleship.fleetcommand.core.domain.multiplayer.JoinResult
import com.battleship.fleetcommand.core.domain.multiplayer.OnlineGameState
import com.battleship.fleetcommand.core.domain.multiplayer.ShotData
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.multiplayer.FirebaseSchema
import com.battleship.fleetcommand.core.multiplayer.auth.FirebaseAuthManager
import com.battleship.fleetcommand.core.multiplayer.mapper.GameSyncMapper
import com.battleship.fleetcommand.core.multiplayer.mapper.ShipPlacementDto
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

    // ── createGame ────────────────────────────────────────────────────────────
    override fun createGame(playerName: String): Flow<GameCreationResult> = flow {
        emit(matchmakingRepository.createGame(playerName))
    }

    // ── joinGame ──────────────────────────────────────────────────────────────
    override fun joinGame(roomCode: String, playerName: String): Flow<JoinResult> = flow {
        emit(matchmakingRepository.joinGame(roomCode, playerName))
    }

    // ── observeGameState ──────────────────────────────────────────────────────
    override fun observeGameState(gameId: String): Flow<OnlineGameState> = callbackFlow {
        val myUid = authManager.currentUid ?: run {
            close(IllegalStateException("Not authenticated"))
            return@callbackFlow
        }
        val gameRef = database.getReference("${FirebaseSchema.GAMES_ROOT}/$gameId")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = mapper.mapGameSnapshot(snapshot, myUid)
                if (state != null) {
                    trySend(state)
                } else {
                    Timber.w("FirebaseMatchRepositoryImpl: observeGameState — mapper returned null for gameId=$gameId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Timber.e("FirebaseMatchRepositoryImpl: observeGameState cancelled error=${error.message}")
                close(error.toException())
            }
        }

        gameRef.addValueEventListener(listener)
        awaitClose { gameRef.removeEventListener(listener) }
    }

    // ── submitShipPlacement ───────────────────────────────────────────────────
    // Serialises List<ShipPlacement> → List<ShipPlacementDto> → JSON string.
    // ShipPlacement and Coord are NOT @Serializable so we map to DTO first.
    override suspend fun submitShipPlacement(
        gameId: String,
        ships: List<ShipPlacement>
    ): Result<Unit> {
        val myUid = authManager.currentUid
            ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val dtos: List<ShipPlacementDto> = ships.map { placement ->
                ShipPlacementDto(
                    shipId      = placement.shipId.name,
                    row         = placement.headCoord.rowOf(),
                    col         = placement.headCoord.colOf(),
                    orientation = if (placement.orientation is com.battleship.fleetcommand.core.domain.engine.Orientation.Horizontal) "H" else "V"
                )
            }
            val shipsJson = Json.encodeToString(dtos)

            val gameRef = database.getReference("${FirebaseSchema.GAMES_ROOT}/$gameId")
            gameRef.child("${FirebaseSchema.BOARDS}/$myUid/${FirebaseSchema.SHIPS}")
                .setValue(shipsJson).await()
            gameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.READY}")
                .setValue(true).await()

            Timber.d("FirebaseMatchRepositoryImpl: submitShipPlacement success gameId=$gameId uid=$myUid")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "FirebaseMatchRepositoryImpl: submitShipPlacement failed")
            Result.failure(e)
        }
    }

    // ── fireShot ──────────────────────────────────────────────────────────────
    override suspend fun fireShot(gameId: String, coord: Coord): Result<Unit> {
        val myUid = authManager.currentUid
            ?: return Result.failure(Exception("Not authenticated"))

        val now = System.currentTimeMillis()
        if (now - lastShotTimestampMs < GameConstants.SHOT_RATE_LIMIT_MS) {
            return Result.failure(Exception("Shot rate limit exceeded"))
        }
        lastShotTimestampMs = now

        return try {
            val shotData = mapOf(
                FirebaseSchema.ROW       to coord.rowOf(),
                FirebaseSchema.COL       to coord.colOf(),
                FirebaseSchema.RESULT    to null,
                FirebaseSchema.SHIP_ID   to null,
                FirebaseSchema.TIMESTAMP to ServerValue.TIMESTAMP
            )
            database.getReference("${FirebaseSchema.GAMES_ROOT}/$gameId/${FirebaseSchema.SHOTS}/$myUid")
                .push()
                .setValue(shotData)
                .await()

            Timber.d("FirebaseMatchRepositoryImpl: fireShot gameId=$gameId coord=(${coord.rowOf()},${coord.colOf()})")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "FirebaseMatchRepositoryImpl: fireShot failed")
            Result.failure(e)
        }
    }

    // ── observeOpponentShots ──────────────────────────────────────────────────
    // Listens to shots/ node; emits ShotData for shots fired by any uid != myUid.
    override fun observeOpponentShots(gameId: String): Flow<ShotData> = callbackFlow {
        val myUid = authManager.currentUid ?: run {
            close(IllegalStateException("Not authenticated"))
            return@callbackFlow
        }
        val shotsRef = database.getReference(
            "${FirebaseSchema.GAMES_ROOT}/$gameId/${FirebaseSchema.SHOTS}"
        )

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val shooterUid = snapshot.key ?: return
                if (shooterUid == myUid) return // ignore my own shots
                snapshot.children.forEach { shotSnapshot ->
                    val shot = mapper.mapShotSnapshot(shotSnapshot)
                    if (shot != null) trySend(shot)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // result updates — no re-emit needed; state flow handles this
            }

            override fun onCancelled(error: DatabaseError) {
                Timber.e("FirebaseMatchRepositoryImpl: observeOpponentShots cancelled error=${error.message}")
                close(error.toException())
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
        }

        shotsRef.addChildEventListener(listener)
        awaitClose { shotsRef.removeEventListener(listener) }
    }

    // ── setPresence ───────────────────────────────────────────────────────────
    override suspend fun setPresence(gameId: String, connected: Boolean) {
        val myUid = authManager.currentUid ?: return
        return try {
            val playerRef = database.getReference(
                "${FirebaseSchema.GAMES_ROOT}/$gameId/${FirebaseSchema.PLAYERS}/$myUid"
            )
            playerRef.child(FirebaseSchema.CONNECTED).setValue(connected).await()
            playerRef.child(FirebaseSchema.LAST_SEEN).setValue(ServerValue.TIMESTAMP).await()
            Timber.d("FirebaseMatchRepositoryImpl: setPresence gameId=$gameId connected=$connected")
        } catch (e: Exception) {
            Timber.e(e, "FirebaseMatchRepositoryImpl: setPresence failed")
        }
    }

    // ── claimVictory ──────────────────────────────────────────────────────────
    override suspend fun claimVictory(gameId: String): Result<Unit> {
        val myUid = authManager.currentUid
            ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val metaRef = database.getReference(
                "${FirebaseSchema.GAMES_ROOT}/$gameId/${FirebaseSchema.META}"
            )
            metaRef.child(FirebaseSchema.WINNER).setValue(myUid).await()
            metaRef.child(FirebaseSchema.STATUS).setValue(FirebaseSchema.STATUS_FINISHED).await()
            Timber.d("FirebaseMatchRepositoryImpl: claimVictory gameId=$gameId winner=$myUid")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "FirebaseMatchRepositoryImpl: claimVictory failed")
            Result.failure(e)
        }
    }

    // ── writeShotResult ───────────────────────────────────────────────────────
    // Called by the DEFENDER only. Writes result (and optional shipId) for a shot
    // that was fired at them. shotIndex is the push-key of the shot node.
   // CORRECT — matches FirebaseMatchRepository interface
    override suspend fun writeShotResult(
        gameId: String,
        shooterUid: String,
        shotIndex: Int,
        result: FireResult
    ) {
        return try {
            val shotRef = database.getReference(
                "${FirebaseSchema.GAMES_ROOT}/$gameId/${FirebaseSchema.SHOTS}/$shooterUid/$shotPushKey"
            )
            val updates = mutableMapOf<String, Any?>(
                FirebaseSchema.RESULT to result.toSchemaString()
            )
            if (shipId != null) updates[FirebaseSchema.SHIP_ID] = shipId
            shotRef.updateChildren(updates).await()
            Timber.d("FirebaseMatchRepositoryImpl: writeShotResult shooterUid=$shooterUid key=$shotPushKey result=$result")
        } catch (e: Exception) {
            Timber.e(e, "FirebaseMatchRepositoryImpl: writeShotResult failed")
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private fun FireResult.toSchemaString(): String = when (this) {
        FireResult.HIT  -> FirebaseSchema.RESULT_HIT
        FireResult.MISS -> FirebaseSchema.RESULT_MISS
        FireResult.SUNK -> FirebaseSchema.RESULT_SUNK
    }
}