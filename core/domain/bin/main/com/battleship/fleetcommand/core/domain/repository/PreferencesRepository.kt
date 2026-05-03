// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/repository/PreferencesRepository.kt
package com.battleship.fleetcommand.core.domain.repository

import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.core.domain.ship.AdjacencyMode
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for DataStore user preferences.
 * Implemented in :core:data (PreferencesRepositoryImpl via DataStore).
 * Zero Android imports — pure Kotlin + coroutines only.
 * Section 13 — Repository Interfaces.
 */
interface PreferencesRepository {
    fun observePlayerName(): Flow<String>
    fun observeDifficulty(): Flow<Difficulty>
    fun observeSoundEnabled(): Flow<Boolean>
    fun observeMusicEnabled(): Flow<Boolean>
    fun observeAdjacencyMode(): Flow<AdjacencyMode>
    suspend fun setPlayerName(name: String)
    suspend fun setDifficulty(difficulty: Difficulty)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setMusicEnabled(enabled: Boolean)
    suspend fun setAdjacencyMode(mode: AdjacencyMode)
    suspend fun getCurrentGameId(): String?
    suspend fun setCurrentGameId(id: String?)
    suspend fun getOnlinePlayerUid(): String?
    suspend fun setOnlinePlayerUid(uid: String)
}