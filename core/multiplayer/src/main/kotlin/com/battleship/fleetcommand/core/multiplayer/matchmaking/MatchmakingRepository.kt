// FILE: core/multiplayer/src/main/kotlin/com/battleship/fleetcommand/core/multiplayer/matchmaking/MatchmakingRepository.kt

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

                // Write the room-code → gameId lookup index.
                // /roomCodes/{roomCode}/gameId = gameId
                // This node has .read: auth != null so guests can find the gameId
                // by room code without needing collection-level read on /games.
                database.getReference(FirebaseSchema.ROOM_CODES)
                    .child(roomCode)
                    .child(FirebaseSchema.GAME_ID)
                    .setValue(gameId)
                    .await()

                // Presence: server clears connected flag and stamps lastSeen on disconnect.
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
    //
    // Join flow — order of operations matters for security rules:
    //
    //  1. Read /roomCodes/{code}/gameId
    //     Allowed: .read = auth != null (no participant check needed)
    //
    //  2. Write /games/{gameId}/meta/guestUid = myUid
    //     Allowed by meta .write rule: data.guestUid === null && newData.guestUid === auth.uid
    //     MUST happen before any read of /games/{gameId} — the $gameId .read rule
    //     only grants access to participants (hostUid or guestUid). Writing guestUid
    //     first makes the guest a participant.
    //
    //  3. Read /games/{gameId}/meta to verify status
    //     Now allowed: guestUid === myUid satisfies $gameId .read rule.
    //
    //  4. If status is not "waiting", undo by clearing guestUid and return error.
    //
    suspend fun joinGame(roomCode: String, playerName: String): JoinResult {
        return try {
            withTimeout(FIREBASE_TIMEOUT_MS) {
                val myUid = authManager.ensureAnonymousAuth()
                val normalised = roomCode.uppercase().trim()

                // ── Step 1: resolve roomCode → gameId via the /roomCodes index ────
                // This read is allowed for any authenticated user (.read: auth != null).
                val gameIdSnapshot = database
                    .getReference(FirebaseSchema.ROOM_CODES)
                    .child(normalised)
                    .child(FirebaseSchema.GAME_ID)
                    .get()
                    .await()

                val gameId = gameIdSnapshot.getValue(String::class.java)
                if (gameId.isNullOrBlank()) {
                    Timber.d("MatchmakingRepository: joinGame roomCode=$normalised not found in /roomCodes")
                    return@withTimeout JoinResult.NotFound
                }

                val gameRef = database.getReference("${FirebaseSchema.GAMES}/$gameId")
                val metaRef = gameRef.child(FirebaseSchema.META)

                // ── Step 2: write guestUid FIRST ──────────────────────────────────
                // The meta .write rule allows this when guestUid is null.
                // If another guest already claimed this slot, the write fails →
                // we catch it below as GameAlreadyStarted.
                // We MUST do this before reading /games/{gameId} because the $gameId
                // .read rule only grants access to participants (hostUid or guestUid).
                try {
                    metaRef.child(FirebaseSchema.GUEST_UID).setValue(myUid).await()
                } catch (writeEx: Exception) {
                    Timber.d("MatchmakingRepository: joinGame guestUid write denied gameId=$gameId — game full or already started")
                    return@withTimeout JoinResult.GameAlreadyStarted
                }

                // ── Step 3: now we are a participant — read meta to check status ──
                val metaSnapshot = metaRef.get().await()

                if (!metaSnapshot.exists()) {
                    // Game was deleted between steps — undo is moot, just report not found.
                    Timber.w("MatchmakingRepository: joinGame meta missing after guestUid write gameId=$gameId")
                    return@withTimeout JoinResult.NotFound
                }

                val status = metaSnapshot
                    .child(FirebaseSchema.STATUS)
                    .getValue(String::class.java)

                if (status != FirebaseSchema.STATUS_WAITING) {
                    // Race condition: game moved out of waiting between our write and this
                    // read. Clear our guestUid so we don't block the host.
                    Timber.d("MatchmakingRepository: joinGame gameId=$gameId status=$status — not waiting, rolling back")
                    try {
                        metaRef.child(FirebaseSchema.GUEST_UID).setValue(null).await()
                    } catch (rollbackEx: Exception) {
                        Timber.w(rollbackEx, "MatchmakingRepository: joinGame rollback failed gameId=$gameId")
                    }
                    return@withTimeout JoinResult.GameAlreadyStarted
                }

                // ── Step 4: write our player node ────────────────────────────────
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