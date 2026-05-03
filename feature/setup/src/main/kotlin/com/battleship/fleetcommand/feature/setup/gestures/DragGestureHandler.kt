// ============================================================
// feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/gestures/DragGestureHandler.kt
// ============================================================
// FILE: feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/gestures/DragGestureHandler.kt
package com.battleship.fleetcommand.feature.setup.gestures

import androidx.compose.ui.geometry.Offset
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.GameConstants

/** Converts a raw drag offset to a grid Coord. Section 8.1. */
fun quantiseToGrid(dragOffset: Offset, cellSizePx: Float): Coord? {
    val row = (dragOffset.y / cellSizePx).toInt()
    val col = (dragOffset.x / cellSizePx).toInt()
    if (row !in 0 until GameConstants.BOARD_SIZE || col !in 0 until GameConstants.BOARD_SIZE) return null
    return Coord.fromRowCol(row, col)
}