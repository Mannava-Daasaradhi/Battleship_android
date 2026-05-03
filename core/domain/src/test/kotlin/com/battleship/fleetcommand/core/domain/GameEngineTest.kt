// FILE: core/domain/src/test/kotlin/com/battleship/fleetcommand/core/domain/GameEngineTest.kt
package com.battleship.fleetcommand.core.domain

import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.engine.GameEngine
import com.battleship.fleetcommand.core.domain.engine.ShotOutcome
import com.battleship.fleetcommand.core.domain.ship.AdjacencyMode
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameEngineTest {

    private val engine = GameEngine()

    private fun placement(shipId: ShipId, row: Int, col: Int, orientation: Orientation = Orientation.Horizontal) =
        ShipPlacement(shipId, Coord.fromRowCol(row, col), orientation)

    private fun allPlacements(): List<ShipPlacement> = listOf(
        placement(ShipId.CARRIER,    0, 0),
        placement(ShipId.BATTLESHIP, 2, 0),
        placement(ShipId.CRUISER,    4, 0),
        placement(ShipId.SUBMARINE,  6, 0),
        placement(ShipId.DESTROYER,  8, 0),
    )

    // ── Placement ────────────────────────────────────────────────────────────

    @Test
    fun `placement validation rejects out-of-bounds ship`() {
        val p = placement(ShipId.CARRIER, 0, 7)
        assertTrue(engine.validatePlacement(p, emptyList()).isFailure)
    }

    @Test
    fun `placement validation rejects overlapping ships`() {
        val existing = placement(ShipId.DESTROYER, 3, 3)
        val overlap  = placement(ShipId.CRUISER,   3, 3)
        assertTrue(engine.validatePlacement(overlap, listOf(existing)).isFailure)
    }

    @Test
    fun `placement validation STRICT mode rejects adjacent ships`() {
        val existing = placement(ShipId.DESTROYER, 3, 3)
        val adjacent = placement(ShipId.CRUISER,   3, 5)
        assertTrue(engine.validatePlacement(adjacent, listOf(existing), AdjacencyMode.STRICT).isFailure)
    }

    @Test
    fun `placement validation RELAXED mode allows adjacent ships`() {
        val existing = placement(ShipId.DESTROYER, 3, 3)
        val adjacent = placement(ShipId.CRUISER,   3, 5)
        assertTrue(engine.validatePlacement(adjacent, listOf(existing), AdjacencyMode.RELAXED).isSuccess)
    }

    @Test
    fun `isPlacementComplete returns true when all 5 ships placed`() {
        assertTrue(engine.isPlacementComplete(allPlacements()))
    }

    @Test
    fun `isPlacementComplete returns false when ships missing`() {
        assertFalse(engine.isPlacementComplete(allPlacements().take(3)))
    }

    // ── Shot resolution ───────────────────────────────────────────────────────

    @Test
    fun `fireShot returns Miss for empty water`() {
        val result = engine.fireShot(Coord.fromRowCol(9, 9), allPlacements(), emptySet())
        assertEquals(ShotOutcome.Miss, result.getOrThrow())
    }

    @Test
    fun `fireShot returns Hit when cell occupied by ship`() {
        val placements = allPlacements()
        val outcome = engine.fireShot(Coord.fromRowCol(0, 0), placements, emptySet()).getOrThrow()
        assertTrue(outcome is ShotOutcome.Hit)
        assertEquals(ShipId.CARRIER, (outcome as ShotOutcome.Hit).shipId)
    }

    @Test
    fun `fireShot returns Sunk when all ship cells hit`() {
        val placements = allPlacements()
        // Destroyer at row 8, cols 0-1
        val shotHistory = setOf(Coord.fromRowCol(8, 0))
        val outcome = engine.fireShot(Coord.fromRowCol(8, 1), placements, shotHistory).getOrThrow()
        assertTrue(outcome is ShotOutcome.Sunk)
        assertEquals(ShipId.DESTROYER, (outcome as ShotOutcome.Sunk).shipId)
    }

    @Test
    fun `fireShot fails on duplicate coord`() {
        val coord = Coord.fromRowCol(0, 0)
        assertTrue(engine.fireShot(coord, allPlacements(), setOf(coord)).isFailure)
    }

    @Test
    fun `ShotOutcome toFireResult maps correctly`() {
        assertEquals(FireResult.MISS, ShotOutcome.Miss.toFireResult())
        assertEquals(FireResult.HIT,  ShotOutcome.Hit(ShipId.CARRIER).toFireResult())
        assertEquals(FireResult.SUNK, ShotOutcome.Sunk(ShipId.CARRIER).toFireResult())
    }

    // ── Game-over detection ───────────────────────────────────────────────────

    @Test
    fun `win detection triggers when all 17 ship cells are hit`() {
        val placements = allPlacements()
        val allCoords = placements.flatMap { it.occupiedCoords() }.toSet()
        assertEquals(ShipRegistry.TOTAL_SHIP_CELLS, allCoords.size)
        assertTrue(engine.isGameOver(placements, allCoords))
    }

    @Test
    fun `isGameOver returns false when one cell remains`() {
        val placements = allPlacements()
        val allCoords = placements.flatMap { it.occupiedCoords() }.toSet()
        assertFalse(engine.isGameOver(placements, allCoords.drop(1).toSet()))
    }

    @Test
    fun `sunkenShips returns correct set after all hits`() {
        val placements = allPlacements()
        val destroyerCoords = placements.first { it.shipId == ShipId.DESTROYER }.occupiedCoords().toSet()
        val sunk = engine.sunkenShips(placements, destroyerCoords)
        assertEquals(setOf(ShipId.DESTROYER), sunk)
    }

    // ── Auto-placement ────────────────────────────────────────────────────────

    @Test
    fun `autoPlace returns exactly 5 placements`() {
        assertEquals(5, engine.autoPlace().size)
    }

    @Test
    fun `autoPlace placements are all within bounds`() {
        engine.autoPlace().forEach { p ->
            assertTrue(p.isWithinBounds(), "$p should be within bounds")
        }
    }

    @Test
    fun `autoPlace placements do not overlap`() {
        val placements = engine.autoPlace()
        val allCoords = placements.flatMap { it.occupiedCoords() }
        assertEquals(allCoords.size, allCoords.toSet().size)
    }

    @Test
    fun `autoPlace covers all 5 ship IDs`() {
        val ids = engine.autoPlace().map { it.shipId }.toSet()
        assertEquals(ShipId.entries.toSet(), ids)
    }
}