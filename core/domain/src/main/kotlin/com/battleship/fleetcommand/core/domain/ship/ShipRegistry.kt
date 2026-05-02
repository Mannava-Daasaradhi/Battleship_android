// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/ship/ShipRegistry.kt

package com.battleship.fleetcommand.core.domain.ship

/**
 * Single source of truth for all ship definitions and sizes.
 * Section 4.2: 5 ships — Carrier(5), Battleship(4), Cruiser(3), Submarine(3), Destroyer(2).
 * Total occupied cells = 17.
 */
object ShipRegistry {

    val ALL: List<ShipDefinition> = listOf(
        ShipDefinition(id = ShipId.CARRIER,    name = "Carrier",    size = 5),
        ShipDefinition(id = ShipId.BATTLESHIP, name = "Battleship", size = 4),
        ShipDefinition(id = ShipId.CRUISER,    name = "Cruiser",    size = 3),
        ShipDefinition(id = ShipId.SUBMARINE,  name = "Submarine",  size = 3),
        ShipDefinition(id = ShipId.DESTROYER,  name = "Destroyer",  size = 2),
    )

    /** Total board cells occupied when all ships are placed = 17. */
    val TOTAL_SHIP_CELLS: Int = ALL.sumOf { it.size }

    /** Lookup by ShipId — throws if id not found (should never happen with sealed enum). */
    fun byId(id: ShipId): ShipDefinition = ALL.first { it.id == id }

    /** Convenience: size of a specific ship. */
    fun sizeOf(id: ShipId): Int = byId(id).size
}