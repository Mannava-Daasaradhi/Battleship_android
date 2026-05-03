// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/model/GameResult.kt
package com.battleship.fleetcommand.core.domain.model

import com.battleship.fleetcommand.core.domain.player.PlayerSlot

/**
 * The outcome of a completed game, passed to [StatsRepository.recordGameResult].
 *
 * Carries everything the stats layer needs to update career numbers:
 *   - which player slot won
 *   - the game mode (for per-mode win counters)
 *   - shot accuracy data (totalShots, totalHits)
 *   - game duration in seconds (for leaderboard and personal-best logic)
 *
 * Section 13 — StatsRepository.recordGameResult(result: GameResult)
 */
data class GameResult(
    val winner: PlayerSlot,
    val mode: GameMode,
    val totalShots: Int,
    val totalHits: Int,
    val durationSeconds: Long
) {
    /** Integer accuracy percentage (0–100), used by the Sharpshooter leaderboard. */
    val accuracyPercent: Int
        get() = if (totalShots == 0) 0 else (totalHits * 100) / totalShots
}