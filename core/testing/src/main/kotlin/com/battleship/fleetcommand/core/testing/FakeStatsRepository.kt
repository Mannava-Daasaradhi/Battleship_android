// FILE: core/testing/src/main/kotlin/com/battleship/fleetcommand/core/testing/FakeStatsRepository.kt
package com.battleship.fleetcommand.core.testing

import com.battleship.fleetcommand.core.domain.model.GameMode
import com.battleship.fleetcommand.core.domain.model.GameResult
import com.battleship.fleetcommand.core.domain.model.PlayerStats
import com.battleship.fleetcommand.core.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake for [StatsRepository] used in unit tests.
 */
class FakeStatsRepository : StatsRepository {

    private val _stats = MutableStateFlow(PlayerStats.empty())

    override suspend fun getStats(): PlayerStats = _stats.value

    override fun observeStats(): Flow<PlayerStats> = _stats

    override suspend fun recordGameResult(result: GameResult) {
        val current = _stats.value
        val isWin = true // caller passes the winner's perspective
        _stats.value = current.copy(
            totalGames = current.totalGames + 1,
            wins = current.wins + 1,
            totalShots = current.totalShots + result.totalShots,
            totalHits = current.totalHits + result.totalHits,
            aiWins = current.aiWins + if (result.mode == GameMode.AI) 1 else 0,
            localWins = current.localWins + if (result.mode == GameMode.LOCAL) 1 else 0,
            onlineWins = current.onlineWins + if (result.mode == GameMode.ONLINE) 1 else 0,
        )
    }

    override suspend fun updateBestTime(durationSeconds: Long) {
        val current = _stats.value
        if (durationSeconds < current.bestTimeSeconds) {
            _stats.value = current.copy(bestTimeSeconds = durationSeconds)
        }
    }

    fun setStats(stats: PlayerStats) { _stats.value = stats }
    fun reset() { _stats.value = PlayerStats.empty() }
}