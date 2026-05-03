// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/model/CellViewState.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/model/CellViewState.kt
package com.battleship.fleetcommand.core.ui.model

import androidx.compose.runtime.Immutable
import com.battleship.fleetcommand.core.domain.Coord

@Immutable
data class CellViewState(
    val coord: Coord,
    val state: CellDisplayState,
    val isHighlighted: Boolean = false,
    val highlightValid: Boolean = false,
)