// FILE: core/domain/src/test/kotlin/com/battleship/fleetcommand/core/domain/BoardTest.kt
package com.battleship.fleetcommand.core.domain

import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BoardTest {

    private fun placement(shipId: ShipId, row: Int, col: Int, orientation: Orientation = Orientation.Horizontal) =
        ShipPlacement(shipId, Coord.fromRowCol(row, col), orientation)

    @Test
    fun `empty board has all Water cells`() {
        val board = Board.empty()
        iterateAllCoords { coord ->
            assertEquals(CellState.Water, board.cellAt(coord))
        }
    }

    @Test
    fun `withCell is immutable - original unchanged`() {
        val original = Board.empty()
        val coord = Coord.fromRowCol(3, 3)
        val updated = original.withCell(coord, CellState.Hit)
        assertEquals(CellState.Water, original.cellAt(coord))
        assertEquals(CellState.Hit, updated.cellAt(coord))
    }

    @Test
    fun `withShip marks occupied coords as Ship cells`() {
        val p = placement(ShipId.DESTROYER, 0, 0)
        val board = Board.empty().withShip(p)
        assertEquals(CellState.Ship(ShipId.DESTROYER, Orientation.Horizontal), board.cellAt(Coord.fromRowCol(0, 0)))
        assertEquals(CellState.Ship(ShipId.DESTROYER, Orientation.Horizontal), board.cellAt(Coord.fromRowCol(0, 1)))
        assertEquals(CellState.Water, board.cellAt(Coord.fromRowCol(0, 2)))
    }

    @Test
    fun `withShip adds placement to ships list`() {
        val p = placement(ShipId.CARRIER, 0, 0)
        val board = Board.empty().withShip(p)
        assertEquals(1, board.ships.size)
        assertEquals(ShipId.CARRIER, board.ships.first().shipId)
    }

    @Test
    fun `DIMENSION constant equals 10`() {
        assertEquals(10, Board.DIMENSION)
    }

    @Test
    fun `allShipsSunk returns false when ships not placed`() {
        assertFalse(Board.empty().allShipsSunk())
    }

    @Test
    fun `isShot returns true for Hit and Miss cells`() {
        val board = Board.empty()
            .withCell(Coord.fromRowCol(0, 0), CellState.Hit)
            .withCell(Coord.fromRowCol(0, 1), CellState.Miss)
        assertTrue(board.isShot(Coord.fromRowCol(0, 0)))
        assertTrue(board.isShot(Coord.fromRowCol(0, 1)))
        assertFalse(board.isShot(Coord.fromRowCol(0, 2)))
    }

    @Test
    fun `shotCount returns correct count`() {
        val board = Board.empty()
            .withCell(Coord.fromRowCol(0, 0), CellState.Hit)
            .withCell(Coord.fromRowCol(0, 1), CellState.Miss)
            .withCell(Coord.fromRowCol(0, 2), CellState.Miss)
        assertEquals(3, board.shotCount())
    }

    @Test
    fun `toFogOfWar hides Ship cells as Water`() {
        val p = placement(ShipId.DESTROYER, 0, 0)
        val board = Board.empty().withShip(p)
        val fog = board.toFogOfWar()
        assertEquals(CellState.Water, fog.cellAt(Coord.fromRowCol(0, 0)))
        assertEquals(CellState.Water, fog.cellAt(Coord.fromRowCol(0, 1)))
    }

    @Test
    fun `toFogOfWar preserves Hit and Miss cells`() {
        val board = Board.empty()
            .withCell(Coord.fromRowCol(1, 1), CellState.Hit)
            .withCell(Coord.fromRowCol(1, 2), CellState.Miss)
        val fog = board.toFogOfWar()
        assertEquals(CellState.Hit, fog.cellAt(Coord.fromRowCol(1, 1)))
        assertEquals(CellState.Miss, fog.cellAt(Coord.fromRowCol(1, 2)))
    }

    @Test
    fun `forEachCell visits exactly 100 cells`() {
        var count = 0
        Board.empty().forEachCell { _, _ -> count++ }
        assertEquals(100, count)
    }

    @Test
    fun `equals returns true for two empty boards`() {
        assertEquals(Board.empty(), Board.empty())
    }

    @Test
    fun `equals returns false after different withCell`() {
        val a = Board.empty().withCell(Coord.fromRowCol(0, 0), CellState.Hit)
        val b = Board.empty().withCell(Coord.fromRowCol(0, 1), CellState.Hit)
        assertNotEquals(a, b)
    }
}