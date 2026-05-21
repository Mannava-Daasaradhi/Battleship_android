package com.battleship.fleetcommand.core.ui.model

import androidx.compose.runtime.Immutable
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.ship.ShipId

@Immutable
data class CellViewState(
    val coord: Coord,
    val state: CellDisplayState,
    val shipId: ShipId? = null,            // which ship occupies this cell (null = water/fog)
    val shipCellIndex: Int = -1,           // 0 = bow, size-1 = stern, -1 = N/A
    val shipSize: Int = 0,                 // total cells in the ship
    val shipOrientation: Orientation? = null, // ship direction for shaped rendering
    val isHighlighted: Boolean = false,
    val highlightValid: Boolean = false,
)
