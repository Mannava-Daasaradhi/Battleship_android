// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/board/Board.kt

package com.battleship.fleetcommand.core.domain.board

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.iterateAllCoords
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry

/**
 * Immutable 10×10 board.
 *
 * Internal representation:
 *   - [cells]     IntArray(100) storing CellState ordinals for Water/Hit/Miss/Sunk presence.
 *   - [ships]     List<ShipPlacement> storing full ship metadata (position, orientation).
 *
 * All mutating operations return a new Board — never mutate in place.
 * Section 4.1 spec.
 */
data class Board(
    val cells: IntArray = IntArray(GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE) { CellState.Water.ordinal },
    val ships: List<ShipPlacement> = emptyList()
) {

    // ── Cell queries ──────────────────────────────────────────────────────

    fun cellAt(coord: Coord): CellState = CellState.fromOrdinal(cells[coord.index])

    fun isHit(coord: Coord): Boolean = cellAt(coord) == CellState.Hit

    fun isMiss(coord: Coord): Boolean = cellAt(coord) == CellState.Miss

    fun isShot(coord: Coord): Boolean = cellAt(coord).let {
        it == CellState.Hit || it == CellState.Miss || it is CellState.Sunk
    }

    fun isOccupied(coord: Coord): Boolean = cellAt(coord) is CellState.Ship

    // ── Immutable update helpers ──────────────────────────────────────────

    fun withCell(coord: Coord, state: CellState): Board {
        val copy = cells.copyOf()
        copy[coord.index] = state.ordinal
        return copy(cells = copy)
    }

    fun withCells(updates: List<Pair<Coord, CellState>>): Board {
        val copy = cells.copyOf()
        updates.forEach { (coord, state) -> copy[coord.index] = state.ordinal }
        return copy(cells = copy)
    }

    fun withShip(placement: ShipPlacement): Board {
        val updatedCells = cells.copyOf()
        placement.occupiedCoords().forEach { coord ->
            updatedCells[coord.index] = CellState.Ship(placement.shipId).ordinal
        }
        return copy(cells = updatedCells, ships = ships + placement)
    }

    // ── Ship queries ──────────────────────────────────────────────────────

    fun placementFor(shipId: ShipId): ShipPlacement? =
        ships.firstOrNull { it.shipId == shipId }

    fun occupiedCoords(shipId: ShipId): List<Coord> =
        placementFor(shipId)?.occupiedCoords() ?: emptyList()

    fun isSunk(shipId: ShipId): Boolean {
        val coords = occupiedCoords(shipId)
        if (coords.isEmpty()) return false
        return coords.all { cellAt(it) == CellState.Hit || cellAt(it) is CellState.Sunk }
    }

    fun allShipsSunk(): Boolean = ships.all { isSunk(it.shipId) }

    fun shipAt(coord: Coord): ShipPlacement? =
        ships.firstOrNull { placement ->
            placement.occupiedCoords().any { it.index == coord.index }
        }

    // ── Board-wide iteration ──────────────────────────────────────────────

    inline fun forEachCell(action: (Coord, CellState) -> Unit) {
        var i = 0
        while (i < GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE) {
            action(Coord(i), CellState.fromOrdinal(cells[i]))
            i++
        }
    }

    fun unshotCoords(): List<Coord> = buildList {
        iterateAllCoords { coord ->
            if (!isShot(coord)) add(coord)
        }
    }

    fun shotCount(): Int {
        var count = 0
        iterateAllCoords { coord -> if (isShot(coord)) count++ }
        return count
    }

    fun hitCount(): Int {
        var count = 0
        iterateAllCoords { coord -> if (isHit(coord)) count++ }
        return count
    }

    fun remainingShipCells(): Int =
        ShipRegistry.TOTAL_SHIP_CELLS - hitCount()

    // ── Fog-of-war projection ─────────────────────────────────────────────

    /**
     * Returns a copy of this board with ship positions hidden —
     * used to show the opponent's board to the attacking player.
     * Ship cells appear as Water; only Hit/Miss/Sunk are visible.
     */
    fun toFogOfWar(): Board {
        val fogCells = cells.copyOf()
        iterateAllCoords { coord ->
            if (CellState.fromOrdinal(fogCells[coord.index]) is CellState.Ship) {
                fogCells[coord.index] = CellState.Water.ordinal
            }
        }
        return copy(cells = fogCells, ships = emptyList())
    }

    // ── equals / hashCode — needed because IntArray doesn't auto-compare ──

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Board) return false
        return cells.contentEquals(other.cells) && ships == other.ships
    }

    override fun hashCode(): Int {
        var result = cells.contentHashCode()
        result = 31 * result + ships.hashCode()
        return result
    }

    // ── Factory ───────────────────────────────────────────────────────────

    companion object {
        fun empty(): Board = Board()
    }
}