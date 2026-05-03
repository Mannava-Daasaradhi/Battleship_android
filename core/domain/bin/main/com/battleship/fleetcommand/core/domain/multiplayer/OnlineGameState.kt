// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/multiplayer/OnlineGameState.kt
package com.battleship.fleetcommand.core.domain.multiplayer

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult

/**
 * Full snapshot of an online game as observed from Firebase Realtime DB.
 * Emitted by [FirebaseMatchRepository.observeGameState].
 * Section 6.10 spec.
 *
 * Zero Android imports — pure Kotlin domain model.
 * Firebase mapping lives in :core:multiplayer (GameSyncMapper).
 */
data class OnlineGameState(
    val gameId: String,
    val myUid: String,
    val opponentUid: String,
    val status: String,          // "waiting" | "battle" | "finished"
    val currentTurn: String,     // UID of the player whose turn it is
    val winner: String?,         // UID of winner, null while game in progress
    val players: Map<String, PlayerData>,
    val myShots: List<ShotData>,
    val opponentShots: List<ShotData>
) {
    val isMyTurn: Boolean get() = currentTurn == myUid
    val isFinished: Boolean get() = status == "finished"
}

/**
 * Presence and identity data for one player in an online game.
 * Section 6.3 Firebase schema: players/$uid node.
 */
data class PlayerData(
    val name: String,
    val ready: Boolean,
    val connected: Boolean,
    val lastSeen: Long
)

/**
 * A single shot record from Firebase.
 * Section 6.6 Firebase schema: shots/$shooterUid/$shotIndex node.
 */
data class ShotData(
    val row: Int,
    val col: Int,
    val result: FireResult?,     // null until defender writes the result
    val shipId: String?,         // null for MISS results
    val timestamp: Long
) {
    val coord: Coord get() = Coord.fromRowCol(row, col)
}