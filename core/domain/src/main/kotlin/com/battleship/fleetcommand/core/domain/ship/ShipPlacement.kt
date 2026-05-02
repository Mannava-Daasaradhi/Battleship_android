// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/ship/ShipPlacement.kt
package com.battleship.fleetcommand.core.domain.ship

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.Orientation

data class ShipPlacement(
    val shipId: ShipId,
    val headCoord: Coord,
    val orientation: Orientation
) {
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

    fun isWithinBounds(): Boolean = occupiedCoords().all { it.isValid() }
}