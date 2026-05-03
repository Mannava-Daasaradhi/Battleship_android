// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/repository/StatsRepository.kt
package com.battleship.fleetcommand.core.domain.repository

import com.battleship.fleetcommand.core.domain.model.GameResult
import com.battleship.fleetcommand.core.domain.model.PlayerStats
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for career statistics persistence.
 * Implemented in :core:data (StatsRepositoryImpl via Room).
 * Zero Android imports — pure Kotlin + coroutines only.
 * Section 13 — Repository Interfaces.
 */
interface StatsRepository {
    suspend fun getStats(): PlayerStats
    fun observeStats(): Flow<PlayerStats>
    suspend fun recordGameResult(result: GameResult)
    suspend fun updateBestTime(durationSeconds: Long)
}