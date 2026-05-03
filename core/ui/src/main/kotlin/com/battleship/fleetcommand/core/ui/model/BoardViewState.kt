// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/model/BoardViewState.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/model/BoardViewState.kt
package com.battleship.fleetcommand.core.ui.model

import androidx.compose.runtime.Immutable
import com.battleship.fleetcommand.core.domain.GameConstants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class BoardViewState(
    val cells: ImmutableList<CellViewState> = persistentListOf(),
    val ownShips: ImmutableList<ShipPlacementViewState> = persistentListOf(),
    val dimensions: Int = GameConstants.BOARD_SIZE,
) {
    companion object {
        fun empty(): BoardViewState = BoardViewState()
    }
}