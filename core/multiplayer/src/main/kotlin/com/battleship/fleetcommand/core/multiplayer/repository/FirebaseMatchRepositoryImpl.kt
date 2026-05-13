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
        val gameRef = database.getReference("${FirebaseSchema.GAMES}/$gameId")

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
    // Serialises List<ShipPlacement> -> List<ShipPlacementDto> -> JSON string.
    // ShipPlacement and Coord are NOT @Serializable so we map to DTO first.
    //
    // After marking this player ready, reads the players node to check whether
    // both participants are now ready. If so, advances status "setup" → "battle"
    // so both devices' WaitingForOpponentViewModels (if still watching) and
    // OnlineGameViewModels transition to the battle phase.
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
                    orientation = if (placement.orientation is Orientation.Horizontal) "H" else "V"
                )
            }
            val shipsJson = Json.encodeToString(dtos)

            val gameRef = database.getReference("${FirebaseSchema.GAMES}/$gameId")
            gameRef.child("${FirebaseSchema.BOARDS}/$myUid/${FirebaseSchema.BOARD_SHIPS}")
                .setValue(shipsJson).await()
            gameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.PLAYER_READY}")
                .setValue(true).await()

            Timber.d("FirebaseMatchRepositoryImpl: submitShipPlacement success gameId=$gameId uid=$myUid")

            // ── Check if both players are now ready → advance to "battle" ────────
            // Read the full players node and check every player's ready flag.
            // Two players must be present and both ready before we advance status.
            try {
                val playersSnapshot = gameRef.child(FirebaseSchema.PLAYERS).get().await()
                val playerEntries = playersSnapshot.children.toList()
                val bothReady = playerEntries.size >= 2 &&
                        playerEntries.all { playerSnap ->
                            playerSnap.child(FirebaseSchema.PLAYER_READY)
                                .getValue(Boolean::class.java) == true
                        }

                if (bothReady) {
                    val metaRef = gameRef.child(FirebaseSchema.META)
                    // Only advance if currently in "setup" to avoid overwriting "finished".
                    val currentStatus = metaRef.child(FirebaseSchema.STATUS)
                        .get().await()
                        .getValue(String::class.java)

                    if (currentStatus == FirebaseSchema.STATUS_SETUP) {
                        metaRef.child(FirebaseSchema.STATUS)
                            .setValue(FirebaseSchema.STATUS_BATTLE)
                            .await()
                        Timber.d("FirebaseMatchRepositoryImpl: submitShipPlacement both ready — advanced status setup→battle gameId=$gameId")
                    }
                }
            } catch (readyCheckEx: Exception) {
                // Non-fatal: the "battle" advancement is best-effort here.
                // The second player's device will also call submitShipPlacement and
                // will attempt the same check, so one of the two devices will succeed.
                Timber.w(readyCheckEx, "FirebaseMatchRepositoryImpl: submitShipPlacement ready-check failed gameId=$gameId")
            }

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

            Timber.d("FirebaseMatchRepositoryImpl: fireShot gameId=$gameId coord=(${coord.rowOf()},${coord.colOf()})")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "FirebaseMatchRepositoryImpl: fireShot failed")
            Result.failure(e)
        }
    }

    // ── observeOpponentShots ──────────────────────────────────────────────────
    // Listens to the shots/ node. Emits a flat List<ShotData> of ALL opponent shots
    // whenever any new shot arrives or an existing shot's result is written.
    //
    // BUG 1 FIX: The original onChildChanged handler called accumulated.removeAll { true }
    // and rebuilt only from the changed shooter's snapshot. This wiped shots from any
    // other shooter already in accumulated. The fix: keep a per-shooter map and rebuild
    // the flat list from the full map on every change, so shots from other shooters are
    // never lost.
    override fun observeOpponentShots(gameId: String): Flow<List<ShotData>> = callbackFlow {
        val myUid = authManager.currentUid ?: run {
            close(IllegalStateException("Not authenticated"))
            return@callbackFlow
        }
        val shotsRef = database.getReference(
            "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.SHOTS}"
        )

        // Per-shooter shot list. Key = shooterUid, value = all shots from that shooter.
        // Using a map means onChildChanged only replaces the affected shooter's list,
        // preserving shots from all other shooters.
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
                // BUG 1 FIX: Rebuild only this shooter's list from the updated snapshot.
                // All other shooters' shots remain intact in the map.
                val shots = snapshot.children.mapNotNull { mapper.mapShotSnapshot(it) }
                shotsByShooter[shooterUid] = shots
                trySend(shotsByShooter.values.flatten())
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
        try {
            val playerRef = database.getReference(
                "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.PLAYERS}/$myUid"
            )
            playerRef.child(FirebaseSchema.PLAYER_CONNECTED).setValue(connected).await()
            playerRef.child(FirebaseSchema.PLAYER_LAST_SEEN).setValue(ServerValue.TIMESTAMP).await()
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
                "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}"
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
    // Called by the DEFENDER only. Writes result to the shot at the given ordinal
    // index within the shooter's push-key list. Queries children to resolve index
    // to push-key, then updates the result field.
    override suspend fun writeShotResult(
        gameId: String,
        shooterUid: String,
        shotIndex: Int,
        result: FireResult
    ) {
        try {
            val shotsRef = database.getReference(
                "${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.SHOTS}/$shooterUid"
            )
            val snapshot = shotsRef.get().await()
            val pushKey = snapshot.children.elementAtOrNull(shotIndex)?.key
                ?: run {
                    Timber.w("FirebaseMatchRepositoryImpl: writeShotResult — no shot at index=$shotIndex for shooter=$shooterUid")
                    return
                }

            shotsRef.child(pushKey).child(FirebaseSchema.SHOT_RESULT)
                .setValue(result.toSchemaString()).await()

            Timber.d("FirebaseMatchRepositoryImpl: writeShotResult shooterUid=$shooterUid index=$shotIndex key=$pushKey result=$result")
        } catch (e: Exception) {
            Timber.e(e, "FirebaseMatchRepositoryImpl: writeShotResult failed")
        }
    }

    // ── flipTurn ──────────────────────────────────────────────────────────────
    // Writes nextPlayerUid to games/$gameId/meta/currentTurn.
    // Called by the DEFENDER after resolving each incoming shot so the turn
    // switches to the defender (who now becomes the attacker).
    override suspend fun flipTurn(gameId: String, nextPlayerUid: String): Result<Unit> {
        return try {
            database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}/${FirebaseSchema.CURRENT_TURN}")
                .setValue(nextPlayerUid)
                .await()
            Timber.d("FirebaseMatchRepositoryImpl: flipTurn gameId=$gameId nextTurn=$nextPlayerUid")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "FirebaseMatchRepositoryImpl: flipTurn failed")
            Result.failure(e)
        }
    }
}