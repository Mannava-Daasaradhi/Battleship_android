// FILE: core/ai/src/test/kotlin/com/battleship/fleetcommand/core/ai/BoardTestBuilder.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry

/**
 * DSL builder for creating test [Board] instances representing fog-of-war knowledge.
 * Mimics what the AI would see on the opponent's board.
 *
 * Usage:
 * ```kotlin
 * val board = fogBoard {
 *     hit(0, 0)
 *     miss(1, 1)
 *     sunk(ShipId.DESTROYER, 2, 0, Orientation.Horizontal)
 * }
 * ```
 */
class BoardTestBuilder {
    private var board = Board.empty()

    fun hit(row: Int, col: Int) {
        board = board.withCell(Coord.fromRowCol(row, col), CellState.Hit)
    }

    fun miss(row: Int, col: Int) {
        board = board.withCell(Coord.fromRowCol(row, col), CellState.Miss)
    }

    /**
     * Marks a ship as fully sunk on the board.
     *
     * Calls withShip() first to register the placement into Board.ships so that
     * Board.cellAt() can resolve ordinal 4 (Sunk) via shipAt() — without this,
     * all Sunk cells are returned as Water, breaking heat map correctness.
     * Then overwrites each Ship cell (ordinal 1) → Sunk cell (ordinal 4).
     */
    fun sunk(shipId: ShipId, headRow: Int, headCol: Int, orientation: Orientation) {
        val placement = ShipPlacement(
            shipId = shipId,
            headCoord = Coord.fromRowCol(headRow, headCol),
            orientation = orientation
        )
        // Register into ships list so Board.shipAt() resolves ordinal 4 correctly.
        board = board.withShip(placement)
        // Overwrite Ship cells (ordinal 1) → Sunk cells (ordinal 4).
        placement.occupiedCoords().forEach { coord ->
            board = board.withCell(coord, CellState.Sunk(shipId))
        }
    }

    fun build(): Board = board
}

fun fogBoard(block: BoardTestBuilder.() -> Unit = {}): Board =
    BoardTestBuilder().apply(block).build()

/** Marks given cells as Miss and returns the resulting board. */
fun Board.withShotsAt(vararg coords: Pair<Int, Int>): Board {
    var b = this
    for ((row, col) in coords) {
        b = b.withCell(Coord.fromRowCol(row, col), CellState.Miss)
    }
    return b
}

/** Returns a board with all cells except the given coord marked as Miss (i.e. one cell left). */
fun boardWithOneCellLeft(row: Int, col: Int): Board {
    var board = Board.empty()
    for (r in 0 until Board.DIMENSION) {
        for (c in 0 until Board.DIMENSION) {
            if (r != row || c != col) {
                board = board.withCell(Coord.fromRowCol(r, c), CellState.Miss)
            }
        }
    }
    return board
}