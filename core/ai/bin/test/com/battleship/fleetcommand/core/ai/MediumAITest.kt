// FILE: core/ai/src/test/kotlin/com/battleship/fleetcommand/core/ai/MediumAiTest.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.ship.ShipId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MediumAiTest {

    private lateinit var ai: MediumAi

    @BeforeEach
    fun setUp() {
        ai = MediumAi()
    }

    // ── Phase transitions ─────────────────────────────────────────────────────

    @Test
    fun `medium AI starts in HUNT phase`() {
        assertEquals(MediumAi.HuntPhase.HUNT, ai.currentPhase())
    }

    @Test
    fun `medium AI switches to TARGET phase on hit`() {
        val board = Board.empty()
        val hitCoord = Coord.fromRowCol(5, 5)

        ai.onShotResult(hitCoord, FireResult.HIT, null, board)

        assertEquals(MediumAi.HuntPhase.TARGET, ai.currentPhase())
    }

    @Test
    fun `medium AI remains in TARGET phase on consecutive hits`() {
        val board = Board.empty()

        ai.onShotResult(Coord.fromRowCol(5, 5), FireResult.HIT, null, board)
        ai.onShotResult(Coord.fromRowCol(5, 6), FireResult.HIT, null, board)

        assertEquals(MediumAi.HuntPhase.TARGET, ai.currentPhase())
    }

    @Test
    fun `medium AI resets to HUNT phase on ship sunk`() {
        val board = Board.empty()

        ai.onShotResult(Coord.fromRowCol(5, 5), FireResult.HIT, null, board)
        assertEquals(MediumAi.HuntPhase.TARGET, ai.currentPhase())

        ai.onShotResult(Coord.fromRowCol(5, 6), FireResult.SUNK, ShipId.DESTROYER, board)
        assertEquals(MediumAi.HuntPhase.HUNT, ai.currentPhase())
    }

    // ── Axis locking ──────────────────────────────────────────────────────────

    @Test
    fun `medium AI locks HORIZONTAL axis after two collinear horizontal hits`() {
        val board = Board.empty()

        // First hit at (3, 4)
        ai.onShotResult(Coord.fromRowCol(3, 4), FireResult.HIT, null, board)
        assertNull(ai.currentAxis(), "Axis should not be locked after only one hit")

        // Second hit at (3, 5) — same row, adjacent column → horizontal
        ai.onShotResult(Coord.fromRowCol(3, 5), FireResult.HIT, null, board)
        assertEquals(MediumAi.Axis.HORIZONTAL, ai.currentAxis())
    }

    @Test
    fun `medium AI locks VERTICAL axis after two collinear vertical hits`() {
        val board = Board.empty()

        ai.onShotResult(Coord.fromRowCol(2, 3), FireResult.HIT, null, board)
        assertNull(ai.currentAxis())

        ai.onShotResult(Coord.fromRowCol(3, 3), FireResult.HIT, null, board)
        assertEquals(MediumAi.Axis.VERTICAL, ai.currentAxis())
    }

    @Test
    fun `medium AI clears axis lock on ship sunk`() {
        val board = Board.empty()

        ai.onShotResult(Coord.fromRowCol(5, 5), FireResult.HIT, null, board)
        ai.onShotResult(Coord.fromRowCol(5, 6), FireResult.HIT, null, board)
        assertEquals(MediumAi.Axis.HORIZONTAL, ai.currentAxis())

        ai.onShotResult(Coord.fromRowCol(5, 7), FireResult.SUNK, ShipId.CRUISER, board)
        assertNull(ai.currentAxis())
    }

    // ── TARGET mode shot selection ─────────────────────────────────────────────

    @Test
    fun `medium AI fires adjacent to hit cell in TARGET mode`() {
        // Hit at (5,5). Next shot must be adjacent (one of: 4,5 / 6,5 / 5,4 / 5,6)
        var board = Board.empty()
        val hitCoord = Coord.fromRowCol(5, 5)

        ai.onShotResult(hitCoord, FireResult.HIT, null, board)
        board = board.withCell(hitCoord, CellState.Hit)

        val nextShot = ai.selectShot(board)

        val adjacents = hitCoord.adjacentCoords().map { it.index }.toSet()
        assertTrue(
            nextShot.index in adjacents,
            "After a hit at (5,5), AI should fire adjacent. Got: row=${nextShot.rowOf()} col=${nextShot.colOf()}"
        )
    }

    @Test
    fun `medium AI fires in locked axis direction after two collinear hits`() {
        // Two hits: (5,5) and (5,6) → lock HORIZONTAL, next must be (5,7) or (5,4)
        var board = Board.empty()

        val hit1 = Coord.fromRowCol(5, 5)
        val hit2 = Coord.fromRowCol(5, 6)

        ai.onShotResult(hit1, FireResult.HIT, null, board)
        board = board.withCell(hit1, CellState.Hit)

        ai.onShotResult(hit2, FireResult.HIT, null, board)
        board = board.withCell(hit2, CellState.Hit)

        val nextShot = ai.selectShot(board)

        // Must be on same row, and column must be 7 (continuing forward) or 4 (behind origin).
        assertEquals(5, nextShot.rowOf(), "AI should stay on the locked horizontal row")
        assertTrue(
            nextShot.colOf() == 7 || nextShot.colOf() == 4,
            "AI should continue or reverse along horizontal axis. Got col=${nextShot.colOf()}"
        )
    }

    // ── Direction reversal ────────────────────────────────────────────────────

    @Test
    fun `medium AI reverses direction after miss in TARGET phase with locked axis`() {
        // Setup: hit (5,5), hit (5,6) → HORIZONTAL locked, direction = +1 → next is (5,7).
        // Miss at (5,7) → reverse → next should be (5,4).
        var board = Board.empty()

        val hit1 = Coord.fromRowCol(5, 5)
        val hit2 = Coord.fromRowCol(5, 6)
        val missCoord = Coord.fromRowCol(5, 7)

        ai.onShotResult(hit1, FireResult.HIT, null, board)
        board = board.withCell(hit1, CellState.Hit)

        ai.onShotResult(hit2, FireResult.HIT, null, board)
        board = board.withCell(hit2, CellState.Hit)

        // Select and confirm (5,7) is next in line
        val beforeMiss = ai.selectShot(board)
        assertEquals(7, beforeMiss.colOf(), "Expected forward shot at col 7 before reversal")

        // Report miss at (5,7)
        ai.onShotResult(missCoord, FireResult.MISS, null, board)
        board = board.withCell(missCoord, CellState.Miss)

        // Next shot must reverse: try (5,4) — to the left of the hit origin (5,5)
        val afterReversal = ai.selectShot(board)
        assertEquals(5, afterReversal.rowOf(), "Should still be on row 5")
        assertTrue(
            afterReversal.colOf() < 5,
            "After reversal AI should fire left of origin (col < 5). Got col=${afterReversal.colOf()}"
        )
    }

    // ── HUNT mode behaviour ───────────────────────────────────────────────────

    @Test
    fun `medium AI returns to HUNT mode after target stack exhausted`() {
        // If we tell the AI about a sunk ship (clears state), it goes back to HUNT.
        val board = Board.empty()
        ai.onShotResult(Coord.fromRowCol(0, 0), FireResult.HIT, null, board)
        assertEquals(MediumAi.HuntPhase.TARGET, ai.currentPhase())

        ai.onShotResult(Coord.fromRowCol(0, 1), FireResult.SUNK, ShipId.DESTROYER, board)
        assertEquals(MediumAi.HuntPhase.HUNT, ai.currentPhase())

        // Should fire a random unshot cell now
        val shot = ai.selectShot(board)
        assertTrue(shot.isValid())
    }

    @Test
    fun `medium AI never fires already-shot cell in HUNT mode`() {
        var board = Board.empty()
        // Mark all cells on parity=0 and parity=1 alternately, leave only odd parity cells
        for (r in 0 until GameConstants.BOARD_SIZE) {
            for (c in 0 until GameConstants.BOARD_SIZE) {
                if ((r + c) % 2 == 0) {
                    board = board.withCell(Coord.fromRowCol(r, c), CellState.Miss)
                }
            }
        }

        repeat(50) {
            val shot = ai.selectShot(board)
            assertEquals(CellState.Water, board.cellAt(shot), "AI selected an already-shot cell")
        }
    }

    @Test
    fun `medium AI never fires already-shot cell in TARGET mode`() {
        var board = Board.empty()
        val hitCoord = Coord.fromRowCol(5, 5)

        ai.onShotResult(hitCoord, FireResult.HIT, null, board)
        board = board.withCell(hitCoord, CellState.Hit)

        // Mark all adjacent cells as already-shot except one
        val adjacents = hitCoord.adjacentCoords()
        adjacents.dropLast(1).forEach { adj ->
            board = board.withCell(adj, CellState.Miss)
        }

        val nextShot = ai.selectShot(board)
        assertEquals(CellState.Water, board.cellAt(nextShot), "AI selected an already-shot adjacent cell")
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `medium AI reset clears all state and returns to HUNT`() {
        val board = Board.empty()

        ai.onShotResult(Coord.fromRowCol(3, 3), FireResult.HIT, null, board)
        ai.onShotResult(Coord.fromRowCol(3, 4), FireResult.HIT, null, board)
        assertEquals(MediumAi.HuntPhase.TARGET, ai.currentPhase())
        assertEquals(MediumAi.Axis.HORIZONTAL, ai.currentAxis())

        ai.reset()

        assertEquals(MediumAi.HuntPhase.HUNT, ai.currentPhase())
        assertNull(ai.currentAxis())

        // Should still produce a valid shot after reset
        assertTrue(ai.selectShot(board).isValid())
    }

    // ── Boundary edge cases ───────────────────────────────────────────────────

    @Test
    fun `medium AI handles hit on board edge without going out of bounds`() {
        var board = Board.empty()
        val edgeHit = Coord.fromRowCol(0, 0)  // top-left corner

        ai.onShotResult(edgeHit, FireResult.HIT, null, board)
        board = board.withCell(edgeHit, CellState.Hit)

        val nextShot = ai.selectShot(board)

        assertTrue(nextShot.isValid(), "AI selected invalid coord from edge hit")
        val totalCells = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE
        assertTrue(nextShot.index in 0 until totalCells)
    }

    @Test
    fun `medium AI handles hit on bottom-right corner`() {
        var board = Board.empty()
        val cornerHit = Coord.fromRowCol(9, 9)

        ai.onShotResult(cornerHit, FireResult.HIT, null, board)
        board = board.withCell(cornerHit, CellState.Hit)

        val nextShot = ai.selectShot(board)
        assertTrue(nextShot.isValid())
    }
}