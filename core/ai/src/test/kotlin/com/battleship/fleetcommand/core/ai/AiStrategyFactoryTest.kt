// FILE: core/ai/src/test/kotlin/com/battleship/fleetcommand/core/ai/AiStrategyFactoryTest.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.player.Difficulty
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AiStrategyFactoryTest {

    @Test
    fun `factory creates EasyAi for EASY difficulty`() {
        val strategy = AiStrategyFactory.create(Difficulty.EASY)
        assertInstanceOf(EasyAi::class.java, strategy)
    }

    @Test
    fun `factory creates MediumAi for MEDIUM difficulty`() {
        val strategy = AiStrategyFactory.create(Difficulty.MEDIUM)
        assertInstanceOf(MediumAi::class.java, strategy)
    }

    @Test
    fun `factory creates HardAi for HARD difficulty`() {
        val strategy = AiStrategyFactory.create(Difficulty.HARD)
        assertInstanceOf(HardAi::class.java, strategy)
    }

    @Test
    fun `factory returns a fresh instance on each call - no shared state`() {
        val a = AiStrategyFactory.create(Difficulty.MEDIUM)
        val b = AiStrategyFactory.create(Difficulty.MEDIUM)
        assertNotSame(a, b, "Factory must return distinct instances to avoid cross-game state leakage")
    }

    @Test
    fun `all created strategies implement AiStrategy interface`() {
        for (difficulty in Difficulty.entries) {
            val strategy = AiStrategyFactory.create(difficulty)
            assertInstanceOf(AiStrategy::class.java, strategy,
                "Strategy for $difficulty must implement AiStrategy")
        }
    }

    @Test
    fun `all created strategies can immediately select a shot on an empty board`() {
        val board = fogBoard()
        for (difficulty in Difficulty.entries) {
            val strategy = AiStrategyFactory.create(difficulty)
            val shot = strategy.selectShot(board)
            assertTrue(shot.isValid(), "Strategy for $difficulty produced invalid coord on first shot")
        }
    }

    @Test
    fun `all created strategies reset without throwing`() {
        for (difficulty in Difficulty.entries) {
            val strategy = AiStrategyFactory.create(difficulty)
            assertDoesNotThrow { strategy.reset() }
        }
    }
}