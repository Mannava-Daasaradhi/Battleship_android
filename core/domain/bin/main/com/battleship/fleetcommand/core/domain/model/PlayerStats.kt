// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/model/PlayerStats.kt
package com.battleship.fleetcommand.core.domain.model

/**
 * Career statistics for the local player — the domain model returned by
 * [StatsRepository.getStats] and [StatsRepository.observeStats].
 *
 * Maps 1-to-1 with StatsEntity (core:data) but carries zero Room annotations
 * so :core:domain stays pure Kotlin. The data mapper in :core:data handles
 * the StatsEntity ↔ PlayerStats conversion.
 *
 * Fields mirror StatsEntity from Section 4.5 / Section 13 exactly.
 */
data class PlayerStats(
    val totalGames: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val bestTimeSeconds: Long = Long.MAX_VALUE,
    val totalShots: Int = 0,
    val totalHits: Int = 0,
    val winStreak: Int = 0,
    val currentStreak: Int = 0,
    val onlineWins: Int = 0,
    val localWins: Int = 0,
    val aiWins: Int = 0
) {
    /** Overall accuracy percentage (0–100), used on the Statistics screen. */
    val accuracyPercent: Int
        get() = if (totalShots == 0) 0 else (totalHits * 100) / totalShots

    /** Win-rate percentage (0–100). */
    val winRatePercent: Int
        get() = if (totalGames == 0) 0 else (wins * 100) / totalGames

    /**
     * Returns true when [bestTimeSeconds] holds a real value
     * (i.e. at least one timed game has been completed).
     */
    val hasBestTime: Boolean
        get() = bestTimeSeconds != Long.MAX_VALUE

    companion object {
        /** Convenience factory for an empty/initial stats object. */
        fun empty(): PlayerStats = PlayerStats()
    }
}