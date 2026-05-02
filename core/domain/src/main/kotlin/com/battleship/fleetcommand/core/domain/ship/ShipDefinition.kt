// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/ship/ShipDefinition.kt

package com.battleship.fleetcommand.core.domain.ship

/**
 * Immutable descriptor for a ship type.
 * drawableRes is 0 here — resolved in :core:ui, never in :core:domain.
 * Section 4.2 spec.
 */
data class ShipDefinition(
    val id: ShipId,
    val name: String,
    val size: Int,
    val drawableRes: Int = 0  // resolved in :core:ui
)