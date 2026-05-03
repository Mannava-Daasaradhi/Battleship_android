// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/components/GameGrid.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/components/GameGrid.kt
package com.battleship.fleetcommand.core.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.ui.model.BoardViewState
import com.battleship.fleetcommand.core.ui.model.CellViewState
import kotlinx.collections.immutable.ImmutableList

/**
 * Reusable 10×10 grid composable per Section 11 / Section 8.
 * key(coord.index) ensures only changed cells recompose (Section 9.2).
 */
@Composable
fun GameGrid(
    board: BoardViewState,
    showShips: Boolean,
    onCellTapped: ((CellViewState) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cellSizeDp = ((maxWidth - 32.dp) / GameConstants.BOARD_SIZE)

        LazyVerticalGrid(
            columns = GridCells.Fixed(GameConstants.BOARD_SIZE),
            userScrollEnabled = false,
        ) {
            items(
                count = board.cells.size,
                key = { index -> board.cells[index].coord.index },
            ) { index ->
                val cell = board.cells[index]
                GameCell(
                    cell = cell,
                    cellSizeDp = cellSizeDp,
                    showShip = showShips,
                    onTap = if (onCellTapped != null) ({ onCellTapped(cell) }) else null,
                )
            }
        }
    }
}