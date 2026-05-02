// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/ship/ShipPlacement.kt

package com.battleship.fleetcommand.core.domain.ship

import com.battleship.fleetcommand.core.domain.Coord

/**
 * Represents a ship placed on the board at a specific position and orientation.
 * headCoord is the top-left cell (row=0,col=0 corner of the ship).
 * Section 4.2 spec.
 */
data class ShipPlacement(
    val shipId: ShipId,
    val headCoord: Coord,
    val orientation: Orientation
) {

    /**
     * Returns all board coordinates this ship occupies.
     * Horizontal → expands along columns (same row).
     * Vertical   → expands along rows (same column).
     */
    fun occupiedCoords(): List<Coord> {
        val size = ShipRegistry.sizeOf(shipId)
        return buildList {
            repeat(size) { i ->
                val coord = when (orientation) {
                    is Orientation.Horizontal ->
                        Coord.fromRowCol(headCoord.rowOf(), headCoord.colOf() + i)
                    is Orientation.Vertical ->
                        Coord.fromRowCol(headCoord.rowOf() + i, headCoord.colOf())
                }
                add(coord)
            }
        }
    }

    /**
     * Returns true if all occupied coords are within the 10×10 grid.
     */
    fun isWithinBounds(): Boolean = occupiedCoords().all { it.isValid() }
}