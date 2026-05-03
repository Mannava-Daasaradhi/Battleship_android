// core/multiplayer/src/main/kotlin/com/battleship/fleetcommand/core/multiplayer/matchmaking/MatchmakingRepository.kt

package com.battleship.fleetcommand.core.multiplayer.matchmaking

import com.battleship.fleetcommand.core.multiplayer.FirebaseSchema
import com.battleship.fleetcommand.core.multiplayer.auth.FirebaseAuthManager
import com.battleship.fleetcommand.core.domain.multiplayer.GameCreationResult
import com.battleship.fleetcommand.core.domain.multiplayer.JoinResult
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchmakingRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val authManager: FirebaseAuthManager
) {

    // ── CREATE GAME (Host) ────────────────────────────────────────────────────
    suspend fun createGame(playerName: String): GameCreationResult {
        return try {
            val myUid = authManager.ensureAnonymousAuth()
            val gamesRef = database.getReference(FirebaseSchema.GAMES_ROOT)
            val newGameRef = gamesRef.push()
            val gameId = newGameRef.key ?: return GameCreationResult.Failure("Push key was null")
            val roomCode = RoomCodeGenerator.generate()

            val meta = mapOf(
                FirebaseSchema.HOST_UID    to myUid,
                FirebaseSchema.GUEST_UID   to null,
                FirebaseSchema.STATUS      to FirebaseSchema.STATUS_WAITING,
                FirebaseSchema.CREATED_AT  to ServerValue.TIMESTAMP,
                FirebaseSchema.UPDATED_AT  to ServerValue.TIMESTAMP,
                FirebaseSchema.WINNER      to null,
                FirebaseSchema.CURRENT_TURN to myUid,
                FirebaseSchema.ROOM_CODE   to roomCode
            )

            newGameRef.child(FirebaseSchema.META).setValue(meta).await()

            val playerData = mapOf(
                FirebaseSchema.NAME       to playerName,
                FirebaseSchema.READY      to false,
                FirebaseSchema.CONNECTED  to true,
                FirebaseSchema.LAST_SEEN  to ServerValue.TIMESTAMP
            )
            newGameRef.child("${FirebaseSchema.PLAYERS}/$myUid").setValue(playerData).await()

            // Presence: server sets connected=false and stamps lastSeen on disconnect
            newGameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.CONNECTED}")
                .onDisconnect().setValue(false)
            newGameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.LAST_SEEN}")
                .onDisconnect().setValue(ServerValue.TIMESTAMP)

            Timber.d("MatchmakingRepository: createGame success gameId=$gameId roomCode=$roomCode")
            GameCreationResult.Success(gameId = gameId, roomCode = roomCode)
        } catch (e: Exception) {
            Timber.e(e, "MatchmakingRepository: createGame failed")
            GameCreationResult.Failure(e.message ?: "Unknown error")
        }
    }

    // ── JOIN GAME (Guest) ─────────────────────────────────────────────────────
    // Room code lookup via Firebase .orderByChild — no Cloud Functions required.
    suspend fun joinGame(roomCode: String, playerName: String): JoinResult {
        return try {
            val myUid = authManager.ensureAnonymousAuth()
            val normalised = roomCode.uppercase().trim()

            val snapshot = database.getReference(FirebaseSchema.GAMES_ROOT)
                .orderByChild("${FirebaseSchema.META}/${FirebaseSchema.ROOM_CODE}")
                .equalTo(normalised)
                .limitToFirst(1)
                .get()
                .await()

            if (!snapshot.exists()) {
                Timber.d("MatchmakingRepository: joinGame roomCode=$normalised not found")
                return JoinResult.NotFound
            }

            val gameEntry = snapshot.children.firstOrNull() ?: return JoinResult.NotFound
            val gameId    = gameEntry.key ?: return JoinResult.NotFound
            val status    = gameEntry.child("${FirebaseSchema.META}/${FirebaseSchema.STATUS}")
                .getValue(String::class.java)

            if (status != FirebaseSchema.STATUS_WAITING) {
                Timber.d("MatchmakingRepository: joinGame gameId=$gameId status=$status — not waiting")
                return JoinResult.GameAlreadyStarted
            }

            val gameRef = database.getReference("${FirebaseSchema.GAMES_ROOT}/$gameId")

            // Write guestUid into meta
            gameRef.child("${FirebaseSchema.META}/${FirebaseSchema.GUEST_UID}").setValue(myUid).await()

            val playerData = mapOf(
                FirebaseSchema.NAME      to playerName,
                FirebaseSchema.READY     to false,
                FirebaseSchema.CONNECTED to true,
                FirebaseSchema.LAST_SEEN to ServerValue.TIMESTAMP
            )
            gameRef.child("${FirebaseSchema.PLAYERS}/$myUid").setValue(playerData).await()

            gameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.CONNECTED}")
                .onDisconnect().setValue(false)
            gameRef.child("${FirebaseSchema.PLAYERS}/$myUid/${FirebaseSchema.LAST_SEEN}")
                .onDisconnect().setValue(ServerValue.TIMESTAMP)

            Timber.d("MatchmakingRepository: joinGame success gameId=$gameId")
            JoinResult.Success(gameId = gameId)
        } catch (e: Exception) {
            Timber.e(e, "MatchmakingRepository: joinGame failed")
            JoinResult.NetworkError
        }
    }
}