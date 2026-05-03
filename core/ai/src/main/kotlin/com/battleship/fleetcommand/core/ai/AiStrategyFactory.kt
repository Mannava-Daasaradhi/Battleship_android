// FILE: core/ai/src/main/kotlin/com/battleship/fleetcommand/core/ai/AiStrategyFactory.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.player.Difficulty

/**
 * Factory that produces the correct [AiStrategy] for a given [Difficulty].
 *
 * Usage:
 * ```kotlin
 * val strategy = AiStrategyFactory.create(Difficulty.HARD)
 * strategy.selectShot(board)
 * ```
 *
 * Pure Kotlin — zero Android imports.
 * Each call returns a fresh instance with clean state.
 */
object AiStrategyFactory {

    /**
     * Creates a new [AiStrategy] instance for the given [difficulty].
     * Always returns a freshly constructed object — never reuses state from a previous game.
     */
    fun create(difficulty: Difficulty): AiStrategy = when (difficulty) {
        Difficulty.EASY   -> EasyAi()
        Difficulty.MEDIUM -> MediumAi()
        Difficulty.HARD   -> HardAi()
    }
}