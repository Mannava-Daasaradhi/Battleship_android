// FILE: core/ai/src/main/kotlin/com/battleship/fleetcommand/core/ai/AiTurnProcessor.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement

/**
 * Bridges the pure [AiStrategy] difficulty tiers to the battle layer.
 *
 * The strategies reason over a fog-of-war [Board] (Water = unshot, Hit, Miss, Sunk), but the
 * battle layer only tracks the human's real ship placements plus the set of cells the AI has
 * already fired at. This processor reconstructs the fog-of-war Board from those two inputs on
 * every call, so the strategies see exactly what a real opponent would know — never the hidden
 * ship positions.
 *
 * Pure Kotlin — zero Android imports. Caller is responsible for dispatching to Dispatchers.Default.
 */
class AiTurnProcessor private constructor(
    val difficulty: Difficulty,
    private val strategy: AiStrategy,
) {

    /** Choose the next cell to fire at, given the AI's current knowledge. */
    fun nextShot(playerPlacements: List<ShipPlacement>, aiShots: Set<Coord>): Coord =
        strategy.selectShot(buildKnowledgeBoard(playerPlacements, aiShots))

    /**
     * Feed the resolved shot result back so stateful tiers (Medium hunt/target, Hard heat map)
     * can adapt. Easy ignores it. [aiShots] MUST already include [coord].
     */
    fun recordResult(
        coord: Coord,
        result: FireResult,
        sunkShipId: ShipId?,
        playerPlacements: List<ShipPlacement>,
        aiShots: Set<Coord>,
    ) {
        strategy.onShotResult(coord, result, sunkShipId, buildKnowledgeBoard(playerPlacements, aiShots))
    }

    /** Clears all internal AI state — call when starting a fresh game with the same processor. */
    fun reset() = strategy.reset()

    /**
     * Reconstructs the AI's fog-of-war view of the player's board.
     *
     * A cell the AI has fired at becomes Sunk (if its whole ship is destroyed), Hit (struck a
     * still-floating ship), or Miss (empty water). Sunk placements are added to the board's ship
     * list so [Board.cellAt] can resolve the Sunk ordinal back to its [ShipId] — which
     * [Board.remainingShipSizes] relies on. Unfired cells stay Water (unknown).
     */
    private fun buildKnowledgeBoard(
        playerPlacements: List<ShipPlacement>,
        aiShots: Set<Coord>,
    ): Board {
        val sunkPlacements = playerPlacements.filter { placement ->
            placement.occupiedCoords().all { it in aiShots }
        }
        val sunkCoords = sunkPlacements.flatMapTo(HashSet()) { it.occupiedCoords() }
        val shipCoords = playerPlacements.flatMapTo(HashSet()) { it.occupiedCoords() }

        val updates = aiShots.map { shot ->
            val state: CellState = when {
                shot in sunkCoords ->
                    CellState.Sunk(sunkPlacements.first { shot in it.occupiedCoords() }.shipId)
                shot in shipCoords -> CellState.Hit
                else -> CellState.Miss
            }
            shot to state
        }
        return Board(ships = sunkPlacements).withCells(updates)
    }

    companion object {
        /** Build a processor backed by the correct strategy for [difficulty]. */
        fun forDifficulty(difficulty: Difficulty): AiTurnProcessor =
            AiTurnProcessor(difficulty, AiStrategyFactory.create(difficulty))
    }
}
