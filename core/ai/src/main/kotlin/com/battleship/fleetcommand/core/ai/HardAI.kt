// FILE: core/ai/src/main/kotlin/com/battleship/fleetcommand/core/ai/HardAi.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.ship.ShipId

/**
 * Hard AI — Probability Density Heat Map.
 *
 * Algorithm (recalculated from scratch on every shot):
 *
 * **Step 1 — BUILD MATRIX**
 * For each remaining (unsunk) ship, iterate every possible horizontal and vertical placement
 * on the board. A placement is valid if every cell in the span is NOT a Miss or Sunk cell —
 * i.e., the ship could still legally fit there. For each valid placement, increment the
 * heat score of every cell in that span by 1.0f.
 *
 * **Step 2 — HIT OVERLAY**
 * For every Hit cell on the board, multiply the heat score of each adjacent unshot Water cell
 * by [HIT_HEAT_FACTOR] (3.0). This strongly biases the AI toward cells adjacent to confirmed
 * hits — the hunt behaviour emerges naturally from the probability math.
 *
 * **Step 3 — ZERO OUT SHOT CELLS**
 * Any cell already fired at (Miss, Hit, or Sunk) is zeroed out so the AI will never
 * select it.
 *
 * **Step 4 — SELECT**
 * Pick the unshot cell with the highest heat score.
 * Tie-break: prefer the cell with the most adjacent Hit neighbours
 * (tighter focus toward the known hit cluster).
 * If the heat map is fully zero (no ship placement fits anywhere — endgame near-full board),
 * fall back to the first remaining unshot Water cell so the simulation always completes.
 *
 * Complexity: 5 ships × 100 positions × 2 orientations = ~1 000 iterations per call.
 * Benchmarks at < 1ms on any modern device, well inside the 16ms frame budget.
 *
 * Never unfair: the AI only uses information visible on the fog-of-war board (Hit/Miss/Sunk).
 * It does NOT cheat by reading ship positions.
 *
 * Pure Kotlin — zero Android imports.
 */
class HardAi : AiStrategy {

    // Pre-allocated FloatArray — reused each turn to avoid GC pressure.
    // Size = BOARD_SIZE * BOARD_SIZE = 100 cells.
    private val heatMap = FloatArray(GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE)

    /** Factor applied to cells adjacent to a confirmed Hit. */
    private val HIT_HEAT_FACTOR = 3.0f

    // ── AiStrategy ───────────────────────────────────────────────────────────

    override fun selectShot(opponentBoard: Board): Coord {
        rebuildHeatMap(opponentBoard)
        return selectHighestProbabilityCell(opponentBoard)
    }

    /**
     * Hard AI does not maintain mutable state between turns beyond what is encoded in the board.
     * Full heat map recalculation on [selectShot] makes incremental callbacks unnecessary.
     */
    override fun onShotResult(
        coord: Coord,
        result: FireResult,
        sunkShipId: ShipId?,
        opponentBoard: Board
    ) = Unit  // Stateless — rebuildHeatMap() reads everything it needs from the board.

    override fun reset() {
        heatMap.fill(0f)
    }

    // ── Heat Map Construction ─────────────────────────────────────────────────

    /**
     * Fully reconstructs [heatMap] from the current [board] state.
     * Called at the start of every [selectShot] call.
     */
    internal fun rebuildHeatMap(board: Board) {
        // Zero out — full recalc every time.
        heatMap.fill(0f)

        val remainingSizes = board.remainingShipSizes()
        val totalCells = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE

        // ── Step 1: BUILD MATRIX ────────────────────────────────────────────
        // For each remaining ship size, count how many valid placements cover each cell.
        for (shipSize in remainingSizes) {
            var row = 0
            while (row < GameConstants.BOARD_SIZE) {
                var col = 0
                while (col < GameConstants.BOARD_SIZE) {
                    // Horizontal
                    if (board.canFitHorizontal(row, col, shipSize)) {
                        for (i in 0 until shipSize) {
                            heatMap[Coord.fromRowCol(row, col + i).index] += 1f
                        }
                    }
                    // Vertical
                    if (board.canFitVertical(row, col, shipSize)) {
                        for (i in 0 until shipSize) {
                            heatMap[Coord.fromRowCol(row + i, col).index] += 1f
                        }
                    }
                    col++
                }
                row++
            }
        }

        // ── Step 2: HIT OVERLAY ─────────────────────────────────────────────
        // Multiply adjacent-to-hit unshot cells by HIT_HEAT_FACTOR.
        var i = 0
        while (i < totalCells) {
            val coord = Coord(i)
            if (board.cellAt(coord) == CellState.Hit) {
                for (adj in coord.adjacentCoords()) {
                    if (board.cellAt(adj) == CellState.Water) {
                        heatMap[adj.index] *= HIT_HEAT_FACTOR
                    }
                }
            }
            i++
        }

        // ── Step 3: ZERO OUT SHOT CELLS ─────────────────────────────────────
        i = 0
        while (i < totalCells) {
            val coord = Coord(i)
            val state = board.cellAt(coord)
            if (state == CellState.Miss || state == CellState.Hit || state is CellState.Sunk) {
                heatMap[i] = 0f
            }
            i++
        }
    }

    /**
     * Exposes the internal heat map for testing (read-only copy).
     */
    internal fun heatMapSnapshot(): FloatArray = heatMap.copyOf()

    // ── Cell Selection ────────────────────────────────────────────────────────

    /**
     * Selects the unshot cell with the highest heat score.
     * Tie-break: the candidate with the most adjacent Hit cells wins
     * (focus toward the known hit cluster).
     *
     * Fallback: if the heat map is entirely zero — which happens when the board is so full of
     * Miss cells that no remaining ship placement fits anywhere — select the first unshot Water
     * cell. This ensures the AI always completes a full game without throwing.
     *
     * @throws IllegalArgumentException if no unshot cell exists at all (board fully exhausted).
     */
    private fun selectHighestProbabilityCell(board: Board): Coord {
        var bestCoord: Coord? = null
        var bestScore = -1f
        var bestHitNeighbours = -1

        var fallback: Coord? = null
        val totalCells = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE

        var i = 0
        while (i < totalCells) {
            val coord = Coord(i)
            if (board.isUnshot(coord)) {
                // Record first unshot cell as fallback regardless of heat score.
                if (fallback == null) fallback = coord

                val score = heatMap[i]
                if (score > 0f) {
                    val hitNeighbours = coord.adjacentCoords()
                        .count { board.cellAt(it) == CellState.Hit }
                    if (score > bestScore ||
                        (score == bestScore && hitNeighbours > bestHitNeighbours)
                    ) {
                        bestScore = score
                        bestCoord = coord
                        bestHitNeighbours = hitNeighbours
                    }
                }
            }
            i++
        }

        // Primary: highest heat cell. Fallback: any remaining unshot cell (heat map exhausted).
        return bestCoord
            ?: fallback
            ?: throw IllegalArgumentException(
                "HardAi.selectHighestProbabilityCell: no unshot cell remains. " +
                "selectShot called on a fully exhausted board."
            )
    }
}