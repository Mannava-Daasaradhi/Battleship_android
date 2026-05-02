// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/board/CellState.kt
package com.battleship.fleetcommand.core.domain.board

import com.battleship.fleetcommand.core.domain.ship.Orientation
import com.battleship.fleetcommand.core.domain.ship.ShipId

/**
 * Represents the visible state of a single cell on the grid.
 *
 * NOTE: The IntArray in Board stores ordinals (0–4).
 * Ship placement details (orientation, shipId) live in List<ShipPlacement> on BoardState.
 * The IntArray only encodes: 0=Water, 1=OccupiedByShip, 2=Hit, 3=Miss, 4=Sunk.
 */
sealed class CellState(val ordinal: Int) {

    data object Water : CellState(0)

    /** Cell occupied by a ship (used during placement view only). */
    data class Ship(val shipId: ShipId, val orientation: Orientation) : CellState(1)

    /** Cell was fired at and hit a ship. */
    data object Hit : CellState(2)

    /** Cell was fired at and missed. */
    data object Miss : CellState(3)

    /** Cell belongs to a ship that has been fully sunk. */
    data class Sunk(val shipId: ShipId) : CellState(4)

    companion object {
        /**
         * Fast ordinal-based lookup for IntArray deserialization.
         * Ship/Sunk carry metadata separately — ordinal only encodes presence.
         */
        fun fromOrdinal(ordinal: Int): CellState = when (ordinal) {
            0 -> Water
            1 -> Water  // Ship metadata stored in ShipPlacement list
            2 -> Hit
            3 -> Miss
            4 -> Water  // Sunk metadata from ship state
            else -> Water
        }
    }
}