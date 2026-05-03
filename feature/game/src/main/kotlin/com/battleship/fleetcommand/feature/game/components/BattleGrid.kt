// ============================================================
// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/components/BattleGrid.kt
// ============================================================
// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/components/BattleGrid.kt
package com.battleship.fleetcommand.feature.game.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.ui.model.BoardViewState

/**
 * Feature-specific BattleGrid wrapping core GameGrid with aim reticle and
 * opponent shimmer overlay. Section 8.2. Full implementation Phase 5.
 */
@Composable
fun BattleGrid(
    board: BoardViewState,
    isInteractive: Boolean,
    isOpponentThinking: Boolean,
    onCellTapped: (Coord) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.battleship.fleetcommand.core.ui.components.GameGrid(
        board = board,
        showShips = false,
        onCellTapped = if (isInteractive) { cell -> onCellTapped(cell.coord) } else null,
        modifier = modifier,
    )
}