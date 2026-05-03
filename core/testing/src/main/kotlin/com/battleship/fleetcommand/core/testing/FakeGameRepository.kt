// FILE: core/testing/src/main/kotlin/com/battleship/fleetcommand/core/testing/FakeGameRepository.kt
package com.battleship.fleetcommand.core.testing

import com.battleship.fleetcommand.core.domain.model.Game
import com.battleship.fleetcommand.core.domain.model.Shot
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.repository.GameRepository
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake for [GameRepository] used in unit tests.
 * All state is held in mutable maps — no Room, no disk.
 */
class FakeGameRepository : GameRepository {

    val games = mutableMapOf<String, Game>()
    val boardStates = mutableMapOf<String, List<ShipPlacement>>() // key: "$gameId-$slot"
    val shots = mutableMapOf<String, MutableList<Shot>>()         // key: gameId
    private val shotsFlow = MutableStateFlow<Map<String, List<Shot>>>(emptyMap())

    override suspend fun createGame(game: Game): String {
        games[game.id] = game
        return game.id
    }

    override suspend fun saveGame(game: Game) {
        games[game.id] = game
    }

    override suspend fun getGame(gameId: String): Game? = games[gameId]

    override suspend fun getUnfinishedGame(): Game? =
        games.values.firstOrNull { !it.isFinished }

    override suspend fun finishGame(gameId: String, winner: PlayerSlot, durationSecs: Long) {
        val game = games[gameId] ?: return
        games[gameId] = game.copy(
            winner = winner,
            durationSecs = durationSecs,
            finishedAt = System.currentTimeMillis()
        )
    }

    override suspend fun saveBoardState(gameId: String, slot: PlayerSlot, placements: List<ShipPlacement>) {
        boardStates["$gameId-$slot"] = placements
    }

    override suspend fun getBoardState(gameId: String, slot: PlayerSlot): List<ShipPlacement>? =
        boardStates["$gameId-$slot"]

    override suspend fun saveShot(gameId: String, shot: Shot) {
        shots.getOrPut(gameId) { mutableListOf() }.add(shot)
        shotsFlow.value = shots.toMap()
    }

    override suspend fun getShots(gameId: String): List<Shot> =
        shots[gameId] ?: emptyList()

    override fun observeShots(gameId: String, firedBy: PlayerSlot): Flow<List<Shot>> =
        shotsFlow.map { all ->
            (all[gameId] ?: emptyList()).filter { it.firedBy == firedBy }
        }

    fun reset() {
        games.clear()
        boardStates.clear()
        shots.clear()
        shotsFlow.value = emptyMap()
    }
}