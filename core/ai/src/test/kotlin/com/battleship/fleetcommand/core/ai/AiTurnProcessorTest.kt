// FILE: core/ai/src/test/kotlin/com/battleship/fleetcommand/core/ai/AiTurnProcessorTest.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the bridge between the battle layer and the [AiStrategy] tiers. A regression here is
 * exactly the "all difficulties behave the same" bug: the processor must reconstruct the
 * fog-of-war board from (player placements + AI shots) so the real strategy actually drives play.
 */
class AiTurnProcessorTest {

    // Destroyer (size 2) lying horizontally at (5,5)-(5,6).
    private val destroyer = ShipPlacement(ShipId.DESTROYER, Coord.fromRowCol(5, 5), Orientation.Horizontal)
    private val playerFleet = listOf(destroyer)

    @Test
    fun `forDifficulty preserves the requested difficulty`() {
        Difficulty.entries.forEach { d ->
            assertEquals(d, AiTurnProcessor.forDifficulty(d).difficulty)
        }
    }

    @Test
    fun `every tier returns only unshot cells`() {
        Difficulty.entries.forEach { d ->
            val processor = AiTurnProcessor.forDifficulty(d)
            val shots = mutableSetOf<Coord>()
            repeat(40) {
                val next = processor.nextShot(playerFleet, shots)
                assertFalse(next in shots, "$d returned an already-shot cell: $next")
                shots.add(next)
                // Feed a plausible result so stateful tiers keep advancing.
                val result = if (playerFleet.any { next in it.occupiedCoords() }) FireResult.HIT else FireResult.MISS
                processor.recordResult(next, result, null, playerFleet, shots)
            }
            assertEquals(40, shots.size, "$d repeated a cell over 40 shots")
        }
    }

    @Test
    fun `medium tier targets adjacent to a confirmed hit`() {
        val processor = AiTurnProcessor.forDifficulty(Difficulty.MEDIUM)
        val hit = Coord.fromRowCol(5, 5) // a real destroyer cell
        val shots = mutableSetOf(hit)

        // Record the hit (ship not yet sunk — its second cell is unshot).
        processor.recordResult(hit, FireResult.HIT, null, playerFleet, shots)

        val next = processor.nextShot(playerFleet, shots)
        assertTrue(
            next in hit.adjacentCoords(),
            "Medium AI should hunt adjacent to a hit, but fired $next (neighbours=${hit.adjacentCoords()})",
        )
    }

    @Test
    fun `hard tier never fires into a sunk ship's cells`() {
        val processor = AiTurnProcessor.forDifficulty(Difficulty.HARD)
        // Sink the destroyer outright: both of its cells are in the AI's shot set.
        val sunkCells = destroyer.occupiedCoords().toMutableSet()
        processor.recordResult(sunkCells.last(), FireResult.SUNK, ShipId.DESTROYER, playerFleet, sunkCells)

        repeat(20) {
            val next = processor.nextShot(playerFleet, sunkCells)
            assertFalse(next in destroyer.occupiedCoords(), "Hard AI fired into a sunk ship at $next")
            sunkCells.add(next)
            processor.recordResult(next, FireResult.MISS, null, playerFleet, sunkCells)
        }
    }
}
