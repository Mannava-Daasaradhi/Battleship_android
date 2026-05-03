// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/model/Game.kt
package com.battleship.fleetcommand.core.domain.model

import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.player.Difficulty

/**
 * Domain model for a single game session.
 * Maps 1-to-1 with GameEntity (core:data) but carries zero Room annotations.
 * The GameMapper in :core:data handles GameEntity ↔ Game conversion.
 *
 * Fields mirror GameEntity from Section 4.5 / Section 13 exactly.
 */
data class Game(
    val id: String,                      // UUID
    val mode: GameMode,
    val startedAt: Long,                 // epoch millis
    val finishedAt: Long? = null,
    val winner: PlayerSlot? = null,
    val difficulty: Difficulty? = null,  // null for LOCAL and ONLINE modes
    val durationSecs: Long? = null,
    val player1Name: String,
    val player2Name: String
) {
    /** True when the game has been completed (win or resign). */
    val isFinished: Boolean get() = finishedAt != null
}