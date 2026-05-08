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
    //  2. Read current guestUid from /games/{gameId}/meta.
    //     • If guestUid == myUid  → we already joined (idempotent re-join after
    //       a network blip or the user pressing Back and retrying). Skip the write
    //       and proceed to Step 4 directly.
    //     • If guestUid is non-null and != myUid → someone else is the guest →
    //       return GameAlreadyStarted immediately (before writing anything).
    //     • If guestUid is null → claim the slot by writing myUid (Step 3).
    //
    //  3. Write /games/{gameId}/meta/guestUid = myUid
    //     Allowed by meta .write rule: data.guestUid === null && newData.guestUid === auth.uid
    //     If the write is rejected another guest grabbed the slot concurrently → GameAlreadyStarted.
    //
    //  4. Read /games/{gameId}/meta to verify status
    //     Now allowed: guestUid === myUid satisfies $gameId .read rule.
    //
    //  5. If status is not "waiting" (and we wrote guestUid in Step 3), undo by
    //     clearing guestUid and return GameAlreadyStarted.
    //
    //  6. Write player node. Advance status "waiting" → "setup" so both
    //     WaitingForOpponentViewModels know to move to ship placement.
    //
    suspend fun joinGame(roomCode: String, playerName: String): JoinResult {
        return try {
            withTimeout(FIREBASE_TIMEOUT_MS) {
                val myUid = authManager.ensureAnonymousAuth()
                val normalised = roomCode.uppercase().trim()

                // ── Step 1: resolve roomCode → gameId via the /roomCodes index ────
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

                // ── Step 2: check existing guestUid before attempting write ────────
                // We need to read meta first. If we're not yet a participant we only
                // have access to /roomCodes, NOT to /games/{gameId}. However, the
                // Firebase security rule allows us to read/write guestUid when it is
                // null (the meta .write rule). We attempt the write; if it fails the
                // guest slot is taken.
                //
                // Optimisation: try to read the meta directly (will fail if security
                // rules block it and guestUid is null — fall through to write attempt).
                val existingGuestUid: String? = try {
                    metaRef.child(FirebaseSchema.GUEST_UID).get().await()
                        .getValue(String::class.java)
                } catch (_: Exception) {
                    // Not yet a participant — we can't read meta yet. Proceed to write.
                    null
                }

                when {
                    existingGuestUid == myUid -> {
                        // ── Idempotent re-join: we already claimed this slot ──────────
                        // (e.g. user pressed Back and retried, or a network retry).
                        // Skip the write; proceed directly to player-node and status update.
                        Timber.d("MatchmakingRepository: joinGame idempotent re-join gameId=$gameId")
                    }

                    existingGuestUid != null -> {
                        // Another player has already claimed the guest slot.
                        Timber.d("MatchmakingRepository: joinGame guest slot taken gameId=$gameId existingGuest=$existingGuestUid")
                        return@withTimeout JoinResult.GameAlreadyStarted
                    }

                    else -> {
                        // ── Step 3: guestUid is null — claim the slot ─────────────────
                        try {
                            metaRef.child(FirebaseSchema.GUEST_UID).setValue(myUid).await()
                        } catch (writeEx: Exception) {
                            Timber.d("MatchmakingRepository: joinGame guestUid write denied gameId=$gameId — game full or already started")
                            return@withTimeout JoinResult.GameAlreadyStarted
                        }
                    }
                }

                // ── Step 4: read meta to verify status ────────────────────────────
                val metaSnapshot = metaRef.get().await()

                if (!metaSnapshot.exists()) {
                    Timber.w("MatchmakingRepository: joinGame meta missing after guestUid write gameId=$gameId")
                    return@withTimeout JoinResult.NotFound
                }

                val status = metaSnapshot
                    .child(FirebaseSchema.STATUS)
                    .getValue(String::class.java)

                // ── Step 5: bail out if game is not in a joinable state ───────────
                // "waiting" is joinable. "setup" is joinable only if this is our own
                // UID (idempotent re-join handled above). Any other status → reject.
                if (status != FirebaseSchema.STATUS_WAITING && status != FirebaseSchema.STATUS_SETUP) {
                    Timber.d("MatchmakingRepository: joinGame gameId=$gameId status=$status — not joinable, rolling back guestUid")
                    // Only roll back if we were the one who wrote guestUid (existingGuestUid was null).
                    if (existingGuestUid == null) {
                        try {
                            metaRef.child(FirebaseSchema.GUEST_UID).setValue(null).await()
                        } catch (rollbackEx: Exception) {
                            Timber.w(rollbackEx, "MatchmakingRepository: joinGame rollback failed gameId=$gameId")
                        }
                    }
                    return@withTimeout JoinResult.GameAlreadyStarted
                }

                // ── Step 6: write player node ─────────────────────────────────────
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

                // ── Step 7: advance status "waiting" → "setup" ────────────────────
                // This is the critical transition that unblocks both devices'
                // WaitingForOpponentScreen. Without it, neither device ever moves past
                // the "waiting for opponent" spinner because bothReady only fires on
                // "setup" (both ready) or "battle". "waiting" → "setup" signals that
                // both players are in the lobby and can start placing ships.
                if (status == FirebaseSchema.STATUS_WAITING) {
                    try {
                        metaRef.child(FirebaseSchema.STATUS)
                            .setValue(FirebaseSchema.STATUS_SETUP)
                            .await()
                        Timber.d("MatchmakingRepository: joinGame advanced status waiting→setup gameId=$gameId")
                    } catch (statusEx: Exception) {
                        // Non-fatal: status advancement failed. WaitingForOpponentScreen will
                        // still show "Opponent connected" once the player node is present.
                        Timber.w(statusEx, "MatchmakingRepository: joinGame status advancement failed gameId=$gameId")
                    }
                }

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