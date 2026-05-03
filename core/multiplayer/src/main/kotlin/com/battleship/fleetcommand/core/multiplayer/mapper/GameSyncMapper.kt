// FILE: core/multiplayer/src/main/kotlin/com/battleship/fleetcommand/core/multiplayer/mapper/GameSyncMapper.kt
package com.battleship.fleetcommand.core.multiplayer.mapper

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.multiplayer.OnlineGameState
import com.battleship.fleetcommand.core.domain.multiplayer.PlayerData
import com.battleship.fleetcommand.core.domain.multiplayer.ShotData
import com.battleship.fleetcommand.core.multiplayer.FirebaseSchema
import com.google.firebase.database.DataSnapshot
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps Firebase [DataSnapshot] objects to domain model types.
 *
 * All mapping is defensive — missing or malformed nodes produce sensible defaults
 * rather than crashes. Timber.w() logs any unexpected shapes for debugging.
 *
 * Section 6.10 spec.
 */
@Singleton
class GameSyncMapper @Inject constructor() {

    /**
     * Maps a full /games/{gameId} snapshot to [OnlineGameState].
     *
     * @param snapshot the full game node snapshot
     * @param myUid    the UID of the local player
     */
    fun mapGameSnapshot(snapshot: DataSnapshot, myUid: String): OnlineGameState? {
        val gameId = snapshot.key ?: run {
            Timber.w("GameSyncMapper: snapshot has null key")
            return null
        }

        val meta = snapshot.child(FirebaseSchema.META)
        val hostUid = meta.child(FirebaseSchema.HOST_UID).getValue(String::class.java) ?: run {
            Timber.w("GameSyncMapper: hostUid missing in game=$gameId")
            return null
        }
        val guestUid = meta.child(FirebaseSchema.GUEST_UID).getValue(String::class.java)
        val status = meta.child(FirebaseSchema.STATUS).getValue(String::class.java)
            ?: FirebaseSchema.STATUS_WAITING
        val currentTurn = meta.child(FirebaseSchema.CURRENT_TURN).getValue(String::class.java)
            ?: hostUid
        val winner = meta.child(FirebaseSchema.WINNER).getValue(String::class.java)

        val opponentUid = if (myUid == hostUid) guestUid ?: "" else hostUid

        // ── Players ───────────────────────────────────────────────────────
        val players = mutableMapOf<String, PlayerData>()
        snapshot.child(FirebaseSchema.PLAYERS).children.forEach { playerSnap ->
            val uid = playerSnap.key ?: return@forEach
            mapPlayerData(playerSnap)?.let { players[uid] = it }
        }

        // ── Shots ─────────────────────────────────────────────────────────
        val myShots = mutableListOf<ShotData>()
        val opponentShots = mutableListOf<ShotData>()

        snapshot.child(FirebaseSchema.SHOTS).children.forEach { shooterSnap ->
            val shooterUid = shooterSnap.key ?: return@forEach
            val shots = shooterSnap.children.mapNotNull { mapShotSnapshot(it) }
            if (shooterUid == myUid) myShots.addAll(shots)
            else opponentShots.addAll(shots)
        }

        return OnlineGameState(
            gameId = gameId,
            myUid = myUid,
            opponentUid = opponentUid,
            status = status,
            currentTurn = currentTurn,
            winner = winner,
            players = players,
            myShots = myShots,
            opponentShots = opponentShots,
        )
    }

    /**
     * Maps a /games/{gameId}/players/{uid} snapshot to [PlayerData].
     */
    fun mapPlayerData(snapshot: DataSnapshot): PlayerData? {
        return try {
            PlayerData(
                name = snapshot.child(FirebaseSchema.PLAYER_NAME)
                    .getValue(String::class.java) ?: "Unknown",
                ready = snapshot.child(FirebaseSchema.PLAYER_READY)
                    .getValue(Boolean::class.java) ?: false,
                connected = snapshot.child(FirebaseSchema.PLAYER_CONNECTED)
                    .getValue(Boolean::class.java) ?: false,
                lastSeen = snapshot.child(FirebaseSchema.PLAYER_LAST_SEEN)
                    .getValue(Long::class.java) ?: 0L,
            )
        } catch (e: Exception) {
            Timber.w(e, "GameSyncMapper: failed to map player data at ${snapshot.ref.path}")
            null
        }
    }

    /**
     * Maps a /games/{gameId}/shots/{shooterUid}/{pushKey} snapshot to [ShotData].
     * Returns null for malformed nodes so callers can skip gracefully.
     */
    fun mapShotSnapshot(snapshot: DataSnapshot): ShotData? {
        return try {
            val row = snapshot.child(FirebaseSchema.SHOT_ROW).getValue(Int::class.java)
                ?: return null
            val col = snapshot.child(FirebaseSchema.SHOT_COL).getValue(Int::class.java)
                ?: return null
            val resultStr = snapshot.child(FirebaseSchema.SHOT_RESULT)
                .getValue(String::class.java)
            val shipIdStr = snapshot.child(FirebaseSchema.SHOT_SHIP_ID)
                .getValue(String::class.java)
            val timestamp = snapshot.child(FirebaseSchema.SHOT_TIMESTAMP)
                .getValue(Long::class.java) ?: 0L

            ShotData(
                row = row,
                col = col,
                result = resultStr?.toFireResult(),
                shipId = shipIdStr,
                timestamp = timestamp,
            )
        } catch (e: Exception) {
            Timber.w(e, "GameSyncMapper: failed to map shot at ${snapshot.ref.path}")
            null
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun String.toFireResult(): FireResult? = when (this) {
        FirebaseSchema.RESULT_HIT  -> FireResult.HIT
        FirebaseSchema.RESULT_MISS -> FireResult.MISS
        FirebaseSchema.RESULT_SUNK -> FireResult.SUNK
        else -> {
            Timber.w("GameSyncMapper: unknown FireResult string '$this'")
            null
        }
    }
}

// ── Extension: FireResult → Firebase string ───────────────────────────────────
internal fun FireResult.toSchemaString(): String = when (this) {
    FireResult.HIT  -> FirebaseSchema.RESULT_HIT
    FireResult.MISS -> FirebaseSchema.RESULT_MISS
    FireResult.SUNK -> FirebaseSchema.RESULT_SUNK
}

// ── ShipPlacement DTO for Firebase JSON serialization ─────────────────────────
// Lives here so :core:multiplayer controls the wire format independently of domain.

internal data class ShipPlacementDto(
    val shipId: String = "",
    val row: Int = 0,
    val col: Int = 0,
    val orientation: String = "H",  // "H" = Horizontal, "V" = Vertical
)