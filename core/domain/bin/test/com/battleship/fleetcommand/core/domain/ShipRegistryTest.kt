// FILE: core/domain/src/test/kotlin/com/battleship/fleetcommand/core/domain/ShipRegistryTest.kt
package com.battleship.fleetcommand.core.domain

import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShipRegistryTest {

    @Test
    fun `ALL contains exactly 5 ships`() {
        assertEquals(5, ShipRegistry.ALL.size)
    }

    @Test
    fun `Carrier has size 5`() {
        assertEquals(5, ShipRegistry.sizeOf(ShipId.CARRIER))
    }

    @Test
    fun `Battleship has size 4`() {
        assertEquals(4, ShipRegistry.sizeOf(ShipId.BATTLESHIP))
    }

    @Test
    fun `Cruiser has size 3`() {
        assertEquals(3, ShipRegistry.sizeOf(ShipId.CRUISER))
    }

    @Test
    fun `Submarine has size 3`() {
        assertEquals(3, ShipRegistry.sizeOf(ShipId.SUBMARINE))
    }

    @Test
    fun `Destroyer has size 2`() {
        assertEquals(2, ShipRegistry.sizeOf(ShipId.DESTROYER))
    }

    @Test
    fun `TOTAL_SHIP_CELLS equals 17`() {
        assertEquals(17, ShipRegistry.TOTAL_SHIP_CELLS)
    }

    @Test
    fun `byId returns correct definition for each ship`() {
        ShipId.entries.forEach { id ->
            assertEquals(id, ShipRegistry.byId(id).id)
        }
    }

    @Test
    fun `all ship IDs are unique`() {
        val ids = ShipRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}