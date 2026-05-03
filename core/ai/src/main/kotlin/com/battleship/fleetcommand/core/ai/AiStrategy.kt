// FILE: core/ai/src/main/kotlin/com/battleship/fleetcommand/core/ai/AiStrategy.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry

/**
 * Contract for all AI difficulty implementations.
 *
 * All implementations are pure Kotlin — zero Android imports.
 * AI computation is dispatched to Dispatchers.Default by the caller (AiTurnProcessor).
 */
interface AiStrategy {

    /**
     * Select the next cell to fire at on the opponent's board.
     *
     * @param opponentBoard The AI's knowledge of the opponent's board (fog-of-war view).
     *                      Cells contain Hit, Miss, Sunk, or Water (unknown/unshot).
     * @return A [Coord] that has not yet been fired at.
     */
    fun selectShot(opponentBoard: Board): Coord

    /**
     * Notify the AI of the result of the last shot it selected.
     * Called immediately after [selectShot] resolves.
     *
     * Default implementation is a no-op — Easy AI does not need state.
     *
     * @param coord   The cell that was fired at.
     * @param result  The outcome: HIT, MISS, or SUNK.
     * @param sunkShipId The [ShipId] of the ship that was sunk, or null if result is not SUNK.
     */
    fun onShotResult(
        coord: Coord,
        result: FireResult,
        sunkShipId: ShipId? = null,
        opponentBoard: Board
    ) {
        // no-op by default
    }

    /**
     * Reset all internal AI state.
     * Must be called when a new game starts to ensure no cross-game memory leakage.
     */
    fun reset()
}

// ── Board extension helpers used across AI implementations ─────────────────

/**
 * Returns all [Coord]s that have not yet been fired at (state == Water on fog view).
 */
fun Board.unshotCoords(): List<Coord> {
    val result = mutableListOf<Coord>()
    var i = 0
    while (i < GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE) {
        val coord = Coord(i)
        if (cellAt(coord) == CellState.Water) result.add(coord)
        i++
    }
    return result
}

/**
 * Returns true if [coord] has not yet been fired at.
 */
fun Board.isUnshot(coord: Coord): Boolean = cellAt(coord) == CellState.Water

/**
 * Returns the sizes of ships that have not yet been sunk, derived from [ShipRegistry].
 * Sunk cells carry [CellState.Sunk] — we count how many ships have at least one Sunk cell
 * and subtract from the full registry. Note: the fog-of-war board only exposes
 * Water/Hit/Miss/Sunk — the AI deduces remaining ships from these signals.
 *
 * Strategy: start with all ships, remove any whose ShipId appears in a Sunk cell.
 */
fun Board.remainingShipSizes(): List<Int> {
    val sunkIds = mutableSetOf<ShipId>()
    var i = 0
    while (i < GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE) {
        val state = cellAt(Coord(i))
        if (state is CellState.Sunk) sunkIds.add(state.shipId)
        i++
    }
    return ShipRegistry.ALL
        .filter { it.id !in sunkIds }
        .map { it.size }
}

/**
 * Returns true if a ship of [size] could fit horizontally starting at ([row],[col])
 * given the current board knowledge (no Miss or Sunk cells in the span).
 */
fun Board.canFitHorizontal(row: Int, col: Int, size: Int): Boolean {
    if (col + size > GameConstants.BOARD_SIZE) return false
    for (i in 0 until size) {
        val state = cellAt(Coord.fromRowCol(row, col + i))
        if (state == CellState.Miss || state is CellState.Sunk) return false
    }
    return true
}

/**
 * Returns true if a ship of [size] could fit vertically starting at ([row],[col]).
 */
fun Board.canFitVertical(row: Int, col: Int, size: Int): Boolean {
    if (row + size > GameConstants.BOARD_SIZE) return false
    for (i in 0 until size) {
        val state = cellAt(Coord.fromRowCol(row + i, col))
        if (state == CellState.Miss || state is CellState.Sunk) return false
    }
    return true
}