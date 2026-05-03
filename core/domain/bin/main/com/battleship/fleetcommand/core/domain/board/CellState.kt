// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/board/CellState.kt
package com.battleship.fleetcommand.core.domain.board

import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.ship.ShipId

sealed class CellState(val ordinal: Int) {

    data object Water : CellState(0)
    data class Ship(val shipId: ShipId, val orientation: Orientation) : CellState(1)
    data object Hit : CellState(2)
    data object Miss : CellState(3)
    data class Sunk(val shipId: ShipId) : CellState(4)

    companion object {
        fun fromOrdinal(ordinal: Int): CellState = when (ordinal) {
            0 -> Water
            1 -> Water
            2 -> Hit
            3 -> Miss
            4 -> Water
            else -> Water
        }
    }
}