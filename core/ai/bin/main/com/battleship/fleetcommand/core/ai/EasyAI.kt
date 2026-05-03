// FILE: core/ai/src/main/kotlin/com/battleship/fleetcommand/core/ai/EasyAi.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.ship.ShipId

/**
 * Easy AI — fires at a uniformly random unshot cell every turn.
 *
 * Characteristics:
 * - No memory between shots beyond what the board already encodes.
 * - Never fires the same cell twice (unshot cell list excludes Hit/Miss/Sunk).
 * - Pure Kotlin — zero Android imports.
 *
 * Difficulty feel: a beginner who picks squares at random.
 */
class EasyAi : AiStrategy {

    private val random = kotlin.random.Random.Default

    override fun selectShot(opponentBoard: Board): Coord {
        val candidates = opponentBoard.unshotCoords()
        require(candidates.isNotEmpty()) {
            "EasyAi.selectShot called with no remaining unshot cells — game should have ended."
        }
        return candidates[random.nextInt(candidates.size)]
    }

    // EasyAi ignores shot results — no state to update.
    override fun onShotResult(
        coord: Coord,
        result: FireResult,
        sunkShipId: ShipId?,
        opponentBoard: Board
    ) = Unit

    override fun reset() = Unit  // Stateless — nothing to clear.
}