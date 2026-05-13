package com.battleship.fleetcommand.core.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.ui.model.BoardViewState
import com.battleship.fleetcommand.core.ui.model.CellViewState

/**
 * Reusable 10×10 grid composable per Section 11 / Section 8.
 * Uses standard Column/Row instead of LazyVerticalGrid to prevent 
 * infinite height constraint crashes when placed inside a verticalScroll container.
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

        // Split the 100 cells into 10 rows of 10 cells
        val rows = board.cells.chunked(GameConstants.BOARD_SIZE)

        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEach { rowCells ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowCells.forEach { cell ->
                        // key ensures only changed cells recompose (Section 9.2)
                        key(cell.coord.index) {
                            GameCell(
                                cell = cell,
                                cellSizeDp = cellSizeDp,
                                showShip = showShips,
                                onTap = if (onCellTapped != null) { { onCellTapped(cell) } } else null,
                            )
                        }
                    }
                }
            }
        }
    }
}