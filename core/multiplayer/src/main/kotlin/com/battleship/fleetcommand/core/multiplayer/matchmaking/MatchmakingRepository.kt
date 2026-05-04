// core/multiplayer/src/main/kotlin/com/battleship/fleetcommand/core/multiplayer/matchmaking/MatchmakingRepository.kt

package com.battleship.fleetcommand.core.multiplayer.matchmaking

import com.battleship.fleetcommand.core.domain.multiplayer.GameCreationResult
import com.battleship.fleetcommand.core.domain.multiplayer.JoinResult
import com.battleship.fleetcommand.core.multiplayer.FirebaseSchema
import com.battleship.fleetcommand.core.multiplayer.auth.FirebaseAuthManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Timeout for any single Firebase Realtime Database operation. */
private const val FIREBASE_TIMEOUT_MS = 10_000L

@Singleton
class MatchmakingRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val authManager: FirebaseAuthManager
) {

    // ── CREATE GAME (Host) ────────────────────────────────────────────────────
    suspend fun createGame(playerName: String): GameCreationResult {
        return try {
            withTimeout(FIREBASE_TIMEOUT_MS) {
                val myUid = authManager.ensureAnonymousAuth()
                val gamesRef = database.getReference(FirebaseSchema.GAMES)
                val newGameRef = gamesRef.push()
                val gameId = newGameRef.key
                    ?: return@withTimeout GameCreationResult.Failure("Push key was null")
                val roomCode = RoomCodeGenerator.generate()

                val meta = mapOf(
                    FirebaseSchema.HOST_UID      to myUid,
                    FirebaseSchema.GUEST_UID     to null,
                    FirebaseSchema.STATUS        to FirebaseSchema.STATUS_WAITING,
                    FirebaseSchema.CREATED_AT    to ServerValue.TIMESTAMP,
                    FirebaseSchema.UPDATED_AT    to ServerValue.TIMESTAMP,
                    FirebaseSchema.WINNER        to null,
                    FirebaseSchema.CURRENT_TURN  to myUid,
                    FirebaseSchema.ROOM_CODE     to roomCode
                )

                newGameRef.child(FirebaseSchema.META).setValue(meta).await()

                val playerData = mapOf(
                    FirebaseSchema.PLAYER_NAME      to playerName,
                    FirebaseSchema.PLAYER_READY     to false,
                    FirebaseSchema.PLAYER_CONNECTED to true,
                    FirebaseSchema.PLAYER_LAST_SEEN to ServerValue.TIMESTAMP
                )
                newGameRef.child("${FirebaseSchema.PLAYERS}/$myUid").setValue(playerData).await()

                // Presence: server stamps lastSeen and clears connected on disconnect
                newGameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.PLAYER_CONNECTED}")
                    .onDisconnect().setValue(false)
                newGameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.PLAYER_LAST_SEEN}")
                    .onDisconnect().setValue(ServerValue.TIMESTAMP)

                Timber.d("MatchmakingRepository: createGame success gameId=$gameId roomCode=$roomCode")
                GameCreationResult.Success(gameId = gameId, roomCode = roomCode)
            }
        } catch (e: Exception) {
            val msg = when {
                e is kotlinx.coroutines.TimeoutCancellationException ->
                    "Connection timed out. Check your internet and that Firebase Realtime Database is created."
                else -> e.message ?: "Unknown error"
            }
            Timber.e(e, "MatchmakingRepository: createGame failed")
            GameCreationResult.Failure(msg)
        }
    }

    // ── JOIN GAME (Guest) ─────────────────────────────────────────────────────
    suspend fun joinGame(roomCode: String, playerName: String): JoinResult {
        return try {
            withTimeout(FIREBASE_TIMEOUT_MS) {
                val myUid = authManager.ensureAnonymousAuth()
                val normalised = roomCode.uppercase().trim()

                val snapshot = database.getReference(FirebaseSchema.GAMES)
                    .orderByChild("${FirebaseSchema.META}/${FirebaseSchema.ROOM_CODE}")
                    .equalTo(normalised)
                    .limitToFirst(1)
                    .get()
                    .await()

                if (!snapshot.exists()) {
                    Timber.d("MatchmakingRepository: joinGame roomCode=$normalised not found")
                    return@withTimeout JoinResult.NotFound
                }

                val gameEntry = snapshot.children.firstOrNull()
                    ?: return@withTimeout JoinResult.NotFound
                val gameId = gameEntry.key
                    ?: return@withTimeout JoinResult.NotFound
                val status = gameEntry
                    .child("${FirebaseSchema.META}/${FirebaseSchema.STATUS}")
                    .getValue(String::class.java)

                if (status != FirebaseSchema.STATUS_WAITING) {
                    Timber.d("MatchmakingRepository: joinGame gameId=$gameId status=$status — not waiting")
                    return@withTimeout JoinResult.GameAlreadyStarted
                }

                val gameRef = database.getReference("${FirebaseSchema.GAMES}/$gameId")

                gameRef.child("${FirebaseSchema.META}/${FirebaseSchema.GUEST_UID}")
                    .setValue(myUid).await()

                val playerData = mapOf(
                    FirebaseSchema.PLAYER_NAME      to playerName,
                    FirebaseSchema.PLAYER_READY     to false,
                    FirebaseSchema.PLAYER_CONNECTED to true,
                    FirebaseSchema.PLAYER_LAST_SEEN to ServerValue.TIMESTAMP
                )
                gameRef.child("${FirebaseSchema.PLAYERS}/$myUid").setValue(playerData).await()

                gameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.PLAYER_CONNECTED}")
                    .onDisconnect().setValue(false)
                gameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.PLAYER_LAST_SEEN}")
                    .onDisconnect().setValue(ServerValue.TIMESTAMP)

                Timber.d("MatchmakingRepository: joinGame success gameId=$gameId")
                JoinResult.Success(gameId = gameId)
            }
        } catch (e: Exception) {
            val msg = when {
                e is kotlinx.coroutines.TimeoutCancellationException ->
                    "Connection timed out. Check your internet and that Firebase Realtime Database is created."
                else -> e.message ?: "Unknown error"
            }
            Timber.e(e, "MatchmakingRepository: joinGame failed")
            JoinResult.Failure(msg)
        }
    }
}