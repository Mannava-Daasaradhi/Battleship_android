// FILE: core/testing/src/main/kotlin/com/battleship/fleetcommand/core/testing/FakePreferencesRepository.kt
package com.battleship.fleetcommand.core.testing

import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.core.domain.repository.PreferencesRepository
import com.battleship.fleetcommand.core.domain.ship.AdjacencyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake for [PreferencesRepository] used in unit tests.
 */
class FakePreferencesRepository : PreferencesRepository {

    private val playerName    = MutableStateFlow("Player 1")
    private val difficulty    = MutableStateFlow(Difficulty.MEDIUM)
    private val soundEnabled  = MutableStateFlow(true)
    private val musicEnabled  = MutableStateFlow(true)
    private val adjacencyMode = MutableStateFlow(AdjacencyMode.RELAXED)
    private var currentGameId: String? = null
    private var onlinePlayerUid: String? = null

    override fun observePlayerName(): Flow<String>       = playerName
    override fun observeDifficulty(): Flow<Difficulty>   = difficulty
    override fun observeSoundEnabled(): Flow<Boolean>    = soundEnabled
    override fun observeMusicEnabled(): Flow<Boolean>    = musicEnabled
    override fun observeAdjacencyMode(): Flow<AdjacencyMode> = adjacencyMode

    override suspend fun setPlayerName(name: String)             { playerName.value = name }
    override suspend fun setDifficulty(d: Difficulty)            { difficulty.value = d }
    override suspend fun setSoundEnabled(enabled: Boolean)       { soundEnabled.value = enabled }
    override suspend fun setMusicEnabled(enabled: Boolean)       { musicEnabled.value = enabled }
    override suspend fun setAdjacencyMode(mode: AdjacencyMode)  { adjacencyMode.value = mode }
    override suspend fun getCurrentGameId(): String?             = currentGameId
    override suspend fun setCurrentGameId(id: String?)          { currentGameId = id }
    override suspend fun getOnlinePlayerUid(): String?           = onlinePlayerUid
    override suspend fun setOnlinePlayerUid(uid: String)        { onlinePlayerUid = uid }
}