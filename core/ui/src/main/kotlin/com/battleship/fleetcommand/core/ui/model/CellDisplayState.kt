// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/model/CellDisplayState.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/model/CellDisplayState.kt
package com.battleship.fleetcommand.core.ui.model

enum class CellDisplayState {
    WATER,
    SHIP,
    HIT,
    MISS,
    SUNK,
}

val CellDisplayState.isShot: Boolean
    get() = this == CellDisplayState.HIT || this == CellDisplayState.MISS || this == CellDisplayState.SUNK