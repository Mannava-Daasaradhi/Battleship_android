// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/board/Board.kt
package com.battleship.fleetcommand.core.domain.board

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.iterateAllCoords
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement

data class Board(
    val cells: IntArray = IntArray(GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE) { CellState.Water.ordinal },
    val ships: List<ShipPlacement> = emptyList()
) {
    companion object {
        /** Board dimension — 10. Use instead of magic literals everywhere. */
        const val DIMENSION = GameConstants.BOARD_SIZE
        fun empty(): Board = Board()
    }

    /**
     * Returns the [CellState] at [coord].
     *
     * FIX: CellState.Ship and CellState.Sunk carry parameters (shipId, orientation) that cannot
     * be round-tripped through a plain Int ordinal. Ordinal 1 (Ship) and ordinal 4 (Sunk) are
     * therefore resolved by looking up the ship placement in the [ships] list. The previous
     * implementation delegated to CellState.fromOrdinal which unconditionally returned Water for
     * both ordinals, causing withShip / isSunk / toFogOfWar to behave incorrectly.
     */
    fun cellAt(coord: Coord): CellState = when (cells[coord.index]) {
        CellState.Water.ordinal -> CellState.Water
        1 /* Ship ordinal */    -> shipAt(coord)
                                       ?.let { CellState.Ship(it.shipId, it.orientation) }
                                       ?: CellState.Water
        CellState.Hit.ordinal   -> CellState.Hit
        CellState.Miss.ordinal  -> CellState.Miss
        4 /* Sunk ordinal */    -> shipAt(coord)
                                       ?.let { CellState.Sunk(it.shipId) }
                                       ?: CellState.Water
        else                    -> CellState.Water
    }

    fun isHit(coord: Coord): Boolean = cellAt(coord) == CellState.Hit
    fun isMiss(coord: Coord): Boolean = cellAt(coord) == CellState.Miss
    fun isShot(coord: Coord): Boolean = cellAt(coord).let {
        it == CellState.Hit || it == CellState.Miss || it is CellState.Sunk
    }
    fun isOccupied(coord: Coord): Boolean = cellAt(coord) is CellState.Ship

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
            updatedCells[coord.index] = CellState.Ship(placement.shipId, placement.orientation).ordinal
        }
        return copy(cells = updatedCells, ships = ships + placement)
    }

    fun placementFor(shipId: ShipId): ShipPlacement? =
        ships.firstOrNull { it.shipId == shipId }

    fun occupiedCoords(shipId: ShipId): List<Coord> =
        placementFor(shipId)?.occupiedCoords() ?: emptyList()

    fun isSunk(shipId: ShipId): Boolean {
        val coords = occupiedCoords(shipId)
        if (coords.isEmpty()) return false
        return coords.all { cellAt(it) == CellState.Hit || cellAt(it) is CellState.Sunk }
    }

    /**
     * Returns true only when at least one ship has been placed AND every ship is sunk.
     *
     * FIX: The previous implementation used ships.all { ... } which returns true vacuously on an
     * empty list, causing an empty board to incorrectly report allShipsSunk() == true.
     */
    fun allShipsSunk(): Boolean = ships.isNotEmpty() && ships.all { isSunk(it.shipId) }

    fun shipAt(coord: Coord): ShipPlacement? =
        ships.firstOrNull { placement ->
            placement.occupiedCoords().any { it.index == coord.index }
        }

    /**
     * Visits every cell on the board.
     *
     * FIX: uses cellAt() so Ship/Sunk cells are correctly reconstructed from the ships list,
     * rather than calling CellState.fromOrdinal() which cannot reconstruct parameterised states.
     */
    inline fun forEachCell(action: (Coord, CellState) -> Unit) {
        var i = 0
        while (i < GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE) {
            val coord = Coord(i)
            action(coord, cellAt(coord))
            i++
        }
    }

    fun unshotCoords(): List<Coord> = buildList {
        iterateAllCoords { coord -> if (!isShot(coord)) add(coord) }
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

    /**
     * Returns a copy of this board with all Ship cells replaced by Water and the ships list
     * cleared — suitable for sending to the opponent (fog-of-war view).
     *
     * FIX: uses cellAt() to detect Ship cells correctly instead of fromOrdinal() which returned
     * Water for Ship ordinal 1, making the fog-of-war conversion a no-op.
     */
    fun toFogOfWar(): Board {
        val fogCells = cells.copyOf()
        iterateAllCoords { coord ->
            if (cellAt(coord) is CellState.Ship) {
                fogCells[coord.index] = CellState.Water.ordinal
            }
        }
        return copy(cells = fogCells, ships = emptyList())
    }

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
}