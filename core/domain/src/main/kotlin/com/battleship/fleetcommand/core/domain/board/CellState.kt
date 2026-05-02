// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/CellState.kt
package com.battleship.fleetcommand.core.domain

/**
 * The visible state of a single board cell.
 *
 * Section 24 pattern: sealed class with noun/adjective subclasses.
 *
 * Design note: The IntArray in Board stores an ordinal for fast lookup.
 * Ship placement metadata (orientation, shipId) is stored in the separate
 * List<Ship> on BoardState — the cell array only needs presence ordinals.
 *
 * Ordinals: 0=Water, 1=OccupiedByShip, 2=Hit, 3=Miss, 4=Sunk
 */
sealed class CellState(val ordinal: Int) {

    /** Unshot, empty water. */
    data object Water : CellState(0)

    /** Occupied by a ship — carries the ship's type for reference. */
    data class Ship(val shipType: ShipType) : CellState(1)

    /** Shot that hit a ship cell. */
    data object Hit : CellState(2)

    /** Shot that hit empty water. */
    data object Miss : CellState(3)

    /** Ship that has been fully sunk — carries the ship's type. */
    data class Sunk(val shipType: ShipType) : CellState(4)

    companion object {
        /**
         * Fast ordinal lookup. Note that Ship and Sunk variants carry type metadata
         * that cannot be recovered from ordinal alone — use Board's ship list for that.
         * This produces a Water stand-in for ordinal 1 and 4 when no type context exists.
         */
        fun fromOrdinal(ordinal: Int): CellState = when (ordinal) {
            0 -> Water
            1 -> Water // Ship details stored in BoardState.ships
            2 -> Hit
            3 -> Miss
            4 -> Water // Sunk details stored in BoardState.ships
            else -> Water
        }
    }
}