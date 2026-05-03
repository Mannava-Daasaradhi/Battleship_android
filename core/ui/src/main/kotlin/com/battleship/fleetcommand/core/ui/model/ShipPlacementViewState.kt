// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/model/ShipPlacementViewState.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/model/ShipPlacementViewState.kt
package com.battleship.fleetcommand.core.ui.model

import androidx.compose.runtime.Immutable
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.ship.ShipId

@Immutable
data class ShipPlacementViewState(
    val shipId: ShipId,
    val headCoord: Coord,
    val orientation: Orientation,
    val size: Int,
    val isSunk: Boolean = false,
)