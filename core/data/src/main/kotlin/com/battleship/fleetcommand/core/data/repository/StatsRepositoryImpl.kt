package com.battleship.fleetcommand.core.data.repository

import com.battleship.fleetcommand.core.data.local.BattleshipDatabase
import com.battleship.fleetcommand.core.data.local.entity.StatsEntity
import com.battleship.fleetcommand.core.domain.model.GameMode
import com.battleship.fleetcommand.core.domain.model.GameResult
import com.battleship.fleetcommand.core.domain.model.PlayerStats
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [StatsRepository].
 * Bound in :app's RepositoryModule via @Binds.
 *
 * The stats table always holds exactly one row (id = 1).
 * [recordGameResult] treats [PlayerSlot.ONE] as the local player:
 *   - winner == ONE  → win
 *   - winner == TWO  → loss
 * This contract must be maintained by all call sites.
 *
 * Section 13 — Data Layer / Repository Implementations.
 */
@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val db: BattleshipDatabase
) : StatsRepository {

    override suspend fun getStats(): PlayerStats =
        (db.statsDao().get() ?: StatsEntity()).toDomain()

    override fun observeStats(): Flow<PlayerStats> =
        db.statsDao().observe().map { entity -> (entity ?: StatsEntity()).toDomain() }

    override suspend fun recordGameResult(result: GameResult) {
        // Ensure singleton row exists before updating
        db.statsDao().insertIfAbsent(StatsEntity())

        val current = db.statsDao().get() ?: StatsEntity()
        val didWin  = result.winner == PlayerSlot.ONE

        val newStreak = if (didWin) current.currentStreak + 1 else 0
        val newBest   = if (didWin && result.durationSeconds < current.bestTimeSeconds)
            result.durationSeconds
        else
            current.bestTimeSeconds

        val updatedWinsByMode = when (result.mode) {
            GameMode.AI     -> Triple(current.aiWins + if (didWin) 1 else 0, current.localWins, current.onlineWins)
            GameMode.LOCAL  -> Triple(current.aiWins, current.localWins + if (didWin) 1 else 0, current.onlineWins)
            GameMode.ONLINE -> Triple(current.aiWins, current.localWins, current.onlineWins + if (didWin) 1 else 0)
        }

        db.statsDao().update(
            current.copy(
                totalGames    = current.totalGames + 1,
                wins          = current.wins + if (didWin) 1 else 0,
                losses        = current.losses + if (!didWin) 1 else 0,
                winStreak     = maxOf(current.winStreak, newStreak),
                currentStreak = newStreak,
                bestTimeSeconds = newBest,
                totalShots    = current.totalShots + result.totalShots,
                totalHits     = current.totalHits + result.totalHits,
                aiWins        = updatedWinsByMode.first,
                localWins     = updatedWinsByMode.second,
                onlineWins    = updatedWinsByMode.third
            )
        )
    }

    override suspend fun updateBestTime(durationSeconds: Long) {
        db.statsDao().insertIfAbsent(StatsEntity())
        val current = db.statsDao().get() ?: StatsEntity()
        if (durationSeconds < current.bestTimeSeconds) {
            db.statsDao().update(current.copy(bestTimeSeconds = durationSeconds))
        }
    }

    // ── Domain mapper ─────────────────────────────────────────────────────────

    private fun StatsEntity.toDomain(): PlayerStats = PlayerStats(
        totalGames      = totalGames,
        wins            = wins,
        losses          = losses,
        draws           = draws,
        bestTimeSeconds = bestTimeSeconds,
        totalShots      = totalShots,
        totalHits       = totalHits,
        winStreak       = winStreak,
        currentStreak   = currentStreak,
        onlineWins      = onlineWins,
        localWins       = localWins,
        aiWins          = aiWins
    )
}