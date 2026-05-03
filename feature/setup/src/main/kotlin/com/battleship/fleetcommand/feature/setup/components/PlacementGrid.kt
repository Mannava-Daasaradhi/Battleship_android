// ============================================================
// feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/components/PlacementGrid.kt
// ============================================================
// FILE: feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/components/PlacementGrid.kt
package com.battleship.fleetcommand.feature.setup.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.ui.components.GameGrid
import com.battleship.fleetcommand.core.ui.model.BoardViewState

/** Placement grid with highlight overlay. Wraps GameGrid. Section 8.1. */
@Composable
fun PlacementGrid(board: BoardViewState, onCellTapped: (Coord) -> Unit, modifier: Modifier = Modifier) {
    GameGrid(board = board, showShips = true, onCellTapped = { cell -> onCellTapped(cell.coord) }, modifier = modifier)
}