// FILE: core/testing/src/main/kotlin/com/battleship/fleetcommand/core/testing/BoardBuilder.kt
package com.battleship.fleetcommand.core.testing

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement

/**
 * Test builder for [Board]. Fluent API for constructing board states in tests.
 * Usage:
 *   val board = BoardBuilder()
 *       .withShip(ShipId.DESTROYER, row = 0, col = 0)
 *       .withHit(row = 0, col = 0)
 *       .build()
 */
class BoardBuilder {

    private var board = Board.empty()

    fun withShip(
        shipId: ShipId,
        row: Int,
        col: Int,
        orientation: Orientation = Orientation.Horizontal
    ): BoardBuilder {
        val placement = ShipPlacement(shipId, Coord.fromRowCol(row, col), orientation)
        board = board.withShip(placement)
        return this
    }

    fun withHit(row: Int, col: Int): BoardBuilder {
        board = board.withCell(Coord.fromRowCol(row, col), CellState.Hit)
        return this
    }

    fun withMiss(row: Int, col: Int): BoardBuilder {
        board = board.withCell(Coord.fromRowCol(row, col), CellState.Miss)
        return this
    }

    fun withSunk(shipId: ShipId, row: Int, col: Int): BoardBuilder {
        board = board.withCell(Coord.fromRowCol(row, col), CellState.Sunk(shipId))
        return this
    }

    fun build(): Board = board

    companion object {
        /** Returns a board with the standard 5-ship fleet placed in non-overlapping rows. */
        fun withFullFleet(): Board = BoardBuilder()
            .withShip(ShipId.CARRIER,    row = 0, col = 0)
            .withShip(ShipId.BATTLESHIP, row = 2, col = 0)
            .withShip(ShipId.CRUISER,    row = 4, col = 0)
            .withShip(ShipId.SUBMARINE,  row = 6, col = 0)
            .withShip(ShipId.DESTROYER,  row = 8, col = 0)
            .build()
    }
}