// FILE: core/domain/src/test/kotlin/com/battleship/fleetcommand/core/domain/PlacementValidatorTest.kt
package com.battleship.fleetcommand.core.domain

import com.battleship.fleetcommand.core.domain.ship.AdjacencyMode
import com.battleship.fleetcommand.core.domain.ship.PlacementError
import com.battleship.fleetcommand.core.domain.ship.PlacementValidator
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlacementValidatorTest {

    private fun placement(shipId: ShipId, row: Int, col: Int, orientation: Orientation = Orientation.Horizontal) =
        ShipPlacement(shipId, Coord.fromRowCol(row, col), orientation)

    @Test
    fun `valid placement on empty board passes`() {
        val p = placement(ShipId.DESTROYER, 0, 0)
        assertTrue(PlacementValidator.isValid(p, emptyList()))
    }

    @Test
    fun `placement validation rejects out-of-bounds ship`() {
        val p = placement(ShipId.CARRIER, 0, 7) // size 5, col 7-11 → out of bounds
        val errors = PlacementValidator.validate(p, emptyList())
        assertTrue(errors.any { it is PlacementError.OutOfBounds })
    }

    @Test
    fun `placement validation rejects vertical ship out of bounds`() {
        val p = placement(ShipId.CARRIER, 7, 0, Orientation.Vertical) // size 5, row 7-11
        val errors = PlacementValidator.validate(p, emptyList())
        assertTrue(errors.any { it is PlacementError.OutOfBounds })
    }

    @Test
    fun `placement validation rejects overlapping ships`() {
        val existing = placement(ShipId.DESTROYER, 3, 3)
        val overlap = placement(ShipId.CRUISER, 3, 3)
        val errors = PlacementValidator.validate(overlap, listOf(existing))
        assertTrue(errors.any { it is PlacementError.OverlapsExistingShip })
    }

    @Test
    fun `OverlapsExistingShip carries the conflicting ship id`() {
        val existing = placement(ShipId.DESTROYER, 3, 3)
        val overlap = placement(ShipId.CRUISER, 3, 3)
        val errors = PlacementValidator.validate(overlap, listOf(existing))
        val overlapError = errors.filterIsInstance<PlacementError.OverlapsExistingShip>().first()
        assertEquals(ShipId.DESTROYER, overlapError.conflictingShipId)
    }

    @Test
    fun `STRICT mode rejects adjacent ships`() {
        val existing = placement(ShipId.DESTROYER, 3, 3)      // occupies (3,3),(3,4)
        val adjacent = placement(ShipId.CRUISER, 3, 5)        // starts at (3,5) — touching
        val errors = PlacementValidator.validate(adjacent, listOf(existing), AdjacencyMode.STRICT)
        assertTrue(errors.any { it is PlacementError.AdjacentToExistingShip })
    }

    @Test
    fun `STRICT mode rejects diagonal ships`() {
        val existing = placement(ShipId.DESTROYER, 3, 3)
        val diagonal = placement(ShipId.CRUISER, 4, 5)        // diagonal to (3,4)
        val errors = PlacementValidator.validate(diagonal, listOf(existing), AdjacencyMode.STRICT)
        assertTrue(errors.any { it is PlacementError.AdjacentToExistingShip })
    }

    @Test
    fun `RELAXED mode allows adjacent ships`() {
        val existing = placement(ShipId.DESTROYER, 3, 3)
        val adjacent = placement(ShipId.CRUISER, 3, 5)
        assertTrue(PlacementValidator.isValid(adjacent, listOf(existing), AdjacencyMode.RELAXED))
    }

    @Test
    fun `out of bounds returns immediately without checking overlap`() {
        val outOfBounds = placement(ShipId.CARRIER, 0, 9) // size 5 → cols 9-13
        val errors = PlacementValidator.validate(outOfBounds, emptyList())
        assertEquals(1, errors.size)
        assertTrue(errors.first() is PlacementError.OutOfBounds)
    }

    @Test
    fun `two non-overlapping non-adjacent ships both valid`() {
        val a = placement(ShipId.DESTROYER, 0, 0)
        val b = placement(ShipId.CRUISER, 5, 5)
        assertTrue(PlacementValidator.isValid(b, listOf(a), AdjacencyMode.STRICT))
    }
}