// FILE: core/data/src/main/kotlin/com/battleship/fleetcommand/core/data/datastore/PreferencesRepositoryImpl.kt
package com.battleship.fleetcommand.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.core.domain.repository.PreferencesRepository
import com.battleship.fleetcommand.core.domain.ship.AdjacencyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of [PreferencesRepository].
 * Section 13 — Data Layer.
 *
 * Bound in :app's DatabaseModule as a @Singleton.
 */
@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    override fun observePlayerName(): Flow<String> =
        dataStore.data.map { it[DataStoreKeys.PLAYER_NAME] ?: "Player" }

    override fun observeDifficulty(): Flow<Difficulty> =
        dataStore.data.map { prefs ->
            prefs[DataStoreKeys.DIFFICULTY]?.let {
                runCatching { Difficulty.valueOf(it) }.getOrNull()
            } ?: Difficulty.MEDIUM
        }

    override fun observeSoundEnabled(): Flow<Boolean> =
        dataStore.data.map { it[DataStoreKeys.SOUND_ENABLED] ?: true }

    override fun observeMusicEnabled(): Flow<Boolean> =
        dataStore.data.map { it[DataStoreKeys.MUSIC_ENABLED] ?: true }

    override fun observeAdjacencyMode(): Flow<AdjacencyMode> =
        dataStore.data.map { prefs ->
            prefs[DataStoreKeys.ADJACENCY_MODE]?.let {
                runCatching { AdjacencyMode.valueOf(it) }.getOrNull()
            } ?: AdjacencyMode.RELAXED
        }

    override suspend fun setPlayerName(name: String) {
        dataStore.edit { it[DataStoreKeys.PLAYER_NAME] = name }
    }

    override suspend fun setDifficulty(difficulty: Difficulty) {
        dataStore.edit { it[DataStoreKeys.DIFFICULTY] = difficulty.name }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[DataStoreKeys.SOUND_ENABLED] = enabled }
    }

    override suspend fun setMusicEnabled(enabled: Boolean) {
        dataStore.edit { it[DataStoreKeys.MUSIC_ENABLED] = enabled }
    }

    override suspend fun setAdjacencyMode(mode: AdjacencyMode) {
        dataStore.edit { it[DataStoreKeys.ADJACENCY_MODE] = mode.name }
    }

    override suspend fun getCurrentGameId(): String? =
        dataStore.data.first()[DataStoreKeys.CURRENT_GAME_ID]

    override suspend fun setCurrentGameId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(DataStoreKeys.CURRENT_GAME_ID)
            else prefs[DataStoreKeys.CURRENT_GAME_ID] = id
        }
    }

    override suspend fun getOnlinePlayerUid(): String? =
        dataStore.data.first()[DataStoreKeys.ONLINE_PLAYER_UID]

    override suspend fun setOnlinePlayerUid(uid: String) {
        dataStore.edit { it[DataStoreKeys.ONLINE_PLAYER_UID] = uid }
    }
}