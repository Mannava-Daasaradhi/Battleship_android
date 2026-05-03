// FILE: core/testing/src/main/kotlin/com/battleship/fleetcommand/core/testing/GameBuilder.kt
package com.battleship.fleetcommand.core.testing

import com.battleship.fleetcommand.core.domain.model.Game
import com.battleship.fleetcommand.core.domain.model.GameMode
import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.core.domain.player.PlayerSlot

/**
 * Test builder for [Game]. Provides sensible defaults for unit tests.
 */
class GameBuilder {

    private var id: String = "test-game-id"
    private var mode: GameMode = GameMode.AI
    private var startedAt: Long = 1_000_000L
    private var finishedAt: Long? = null
    private var winner: PlayerSlot? = null
    private var difficulty: Difficulty? = Difficulty.MEDIUM
    private var durationSecs: Long? = null
    private var player1Name: String = "Player 1"
    private var player2Name: String = "Player 2"

    fun withId(id: String) = apply { this.id = id }
    fun withMode(mode: GameMode) = apply { this.mode = mode }
    fun withDifficulty(difficulty: Difficulty?) = apply { this.difficulty = difficulty }
    fun withWinner(winner: PlayerSlot, durationSecs: Long = 120L) = apply {
        this.winner = winner
        this.finishedAt = startedAt + durationSecs * 1000
        this.durationSecs = durationSecs
    }
    fun withPlayer1Name(name: String) = apply { this.player1Name = name }
    fun withPlayer2Name(name: String) = apply { this.player2Name = name }

    fun build(): Game = Game(
        id = id,
        mode = mode,
        startedAt = startedAt,
        finishedAt = finishedAt,
        winner = winner,
        difficulty = difficulty,
        durationSecs = durationSecs,
        player1Name = player1Name,
        player2Name = player2Name
    )

    companion object {
        fun aFinishedAiGame(winner: PlayerSlot = PlayerSlot.ONE): Game =
            GameBuilder()
                .withMode(GameMode.AI)
                .withDifficulty(Difficulty.HARD)
                .withWinner(winner)
                .build()

        fun anUnfinishedGame(): Game = GameBuilder().build()
    }
}