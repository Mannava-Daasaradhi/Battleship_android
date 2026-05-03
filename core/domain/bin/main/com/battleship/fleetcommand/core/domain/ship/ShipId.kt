// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/ship/ShipId.kt

package com.battleship.fleetcommand.core.domain.ship

/**
 * Unique identifier for each ship type.
 * Section 24: enum entries in SCREAMING_SNAKE_CASE.
 */
enum class ShipId {
    CARRIER,
    BATTLESHIP,
    CRUISER,
    SUBMARINE,
    DESTROYER
}