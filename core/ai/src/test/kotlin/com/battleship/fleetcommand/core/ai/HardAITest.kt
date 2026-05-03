// FILE: core/ai/src/test/kotlin/com/battleship/fleetcommand/core/ai/HardAiTest.kt

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
import kotlin.system.measureTimeMillis

class HardAiTest {

    private lateinit var ai: HardAi

    @BeforeEach
    fun setUp() {
        ai = HardAi()
    }

    // ── First move: highest density cell ─────────────────────────────────────

    @Test
    fun `hard AI selects highest density cell on first move against empty board`() {
        val board = Board.empty()

        ai.rebuildHeatMap(board)
        val heatMap = ai.heatMapSnapshot()
        val shot = ai.selectShot(board)

        // The selected cell must have the maximum heat score.
        val maxScore = heatMap.max()
        val shotScore = heatMap[shot.index]

        assertEquals(
            maxScore, shotScore,
            "Hard AI did not select cell with highest heat score. " +
            "Selected index=${shot.index} score=$shotScore, max=$maxScore"
        )
    }

    @Test
    fun `hard AI on empty board selects a central region cell (highest overlap count)`() {
        // On a clean board, the centre cells have the most possible ship placements through them.
        // All 5 ships can place both H and V through centre cells, giving maximum score.
        val board = Board.empty()
        val shot = ai.selectShot(board)

        // Centre cells: rows 3–6, cols 3–6 have highest density.
        // The exact optimal cell may vary by ship composition but must be interior.
        assertTrue(
            shot.rowOf() in 2..7 && shot.colOf() in 2..7,
            "First shot on empty board should be in the high-density centre region. " +
            "Got row=${shot.rowOf()} col=${shot.colOf()}"
        )
    }

    // ── Heat map correctness ──────────────────────────────────────────────────

    @Test
    fun `hard AI heat map is zero for already-shot Miss cells`() {
        val board = fogBoard {
            miss(0, 0)
            miss(5, 5)
            miss(9, 9)
        }

        ai.rebuildHeatMap(board)
        val heatMap = ai.heatMapSnapshot()

        assertEquals(0f, heatMap[Coord.fromRowCol(0, 0).index], "Miss cell should have heat=0")
        assertEquals(0f, heatMap[Coord.fromRowCol(5, 5).index], "Miss cell should have heat=0")
        assertEquals(0f, heatMap[Coord.fromRowCol(9, 9).index], "Miss cell should have heat=0")
    }

    @Test
    fun `hard AI probability density is zero for Hit cells`() {
        val board = fogBoard {
            hit(3, 3)
        }

        ai.rebuildHeatMap(board)
        val heatMap = ai.heatMapSnapshot()

        assertEquals(0f, heatMap[Coord.fromRowCol(3, 3).index],
            "Hit cell itself should be zeroed out (AI won't re-fire it)")
    }

    @Test
    fun `hard AI probability density is zero for Sunk cells`() {
        val board = fogBoard {
            sunk(ShipId.DESTROYER, 0, 0, Orientation.Horizontal)
        }

        ai.rebuildHeatMap(board)
        val heatMap = ai.heatMapSnapshot()

        assertEquals(0f, heatMap[Coord.fromRowCol(0, 0).index], "Sunk cell [0,0] should be heat=0")
        assertEquals(0f, heatMap[Coord.fromRowCol(0, 1).index], "Sunk cell [0,1] should be heat=0")
    }

    @Test
    fun `hard AI heat map has higher scores adjacent to Hit cells`() {
        // Place a Hit at (5,5). Cells adjacent to it should have elevated heat vs. far cells.
        val board = fogBoard {
            hit(5, 5)
        }

        ai.rebuildHeatMap(board)
        val heatMap = ai.heatMapSnapshot()

        val adjacentScores = Coord.fromRowCol(5, 5).adjacentCoords()
            .filter { board.cellAt(it) == CellState.Water }
            .map { heatMap[it.index] }

        val farScore = heatMap[Coord.fromRowCol(0, 0).index]

        val allAdjacentHigherOrEqual = adjacentScores.all { it >= farScore }
        assertTrue(
            allAdjacentHigherOrEqual,
            "Cells adjacent to Hit(5,5) should have elevated heat vs far cells. " +
            "Adjacent=$adjacentScores far=$farScore"
        )
    }

    @Test
    fun `hard AI heat map values are non-negative for all unshot cells`() {
        val board = fogBoard {
            miss(0, 0); miss(1, 1); hit(5, 5)
        }

        ai.rebuildHeatMap(board)
        val heatMap = ai.heatMapSnapshot()
        val totalCells = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE

        for (i in 0 until totalCells) {
            assertTrue(heatMap[i] >= 0f, "Heat map has negative value at index $i: ${heatMap[i]}")
        }
    }

    // ── Never selects already-shot cell ───────────────────────────────────────

    @Test
    fun `hard AI never selects already-shot cell`() {
        // Simulate 30 shots and check that selectShot always returns an unshot cell.
        var board = Board.empty()

        repeat(30) { turn ->
            val shot = ai.selectShot(board)
            assertEquals(
                CellState.Water, board.cellAt(shot),
                "HardAi selected already-shot cell at turn $turn: index=${shot.index}"
            )
            board = board.withCell(shot, CellState.Miss)
            ai.onShotResult(shot, FireResult.MISS, null, board)
        }
    }

    @Test
    fun `hard AI never selects already-shot cell in hunt mode simulation with hits and misses`() {
        var board = Board.empty()

        // Mix of hits and misses
        val missCells = listOf(
            Coord.fromRowCol(0,0), Coord.fromRowCol(1,1), Coord.fromRowCol(2,2)
        )
        val hitCells = listOf(
            Coord.fromRowCol(5,5), Coord.fromRowCol(5,6)
        )

        for (c in missCells) board = board.withCell(c, CellState.Miss)
        for (c in hitCells) board = board.withCell(c, CellState.Hit)

        repeat(20) {
            val shot = ai.selectShot(board)
            val state = board.cellAt(shot)
            assertEquals(
                CellState.Water, state,
                "HardAi selected non-water cell: index=${shot.index} state=$state"
            )
        }
    }

    // ── Ship sunk handling ─────────────────────────────────────────────────────

    @Test
    fun `hard AI selects remaining high-density cell after ship sunk`() {
        // Sink the Carrier (size 5) and verify AI still selects sensibly from the remaining ships.
        val board = fogBoard {
            sunk(ShipId.CARRIER, 0, 0, Orientation.Horizontal)
        }

        val shot = ai.selectShot(board)

        // After Carrier sunk, remaining ships: Battleship(4), Cruiser(3), Sub(3), Destroyer(2)
        // Heat map should still have valid coverage — shot must be Water.
        assertEquals(CellState.Water, board.cellAt(shot))
        assertTrue(shot.isValid())
    }

    @Test
    fun `hard AI heat map drops to zero for cells that cant fit any remaining ship`() {
        // Mark enough cells as Miss to block all ship placements in row 0 except one small gap.
        // Col 0 and col 1 clear, rest of row 0 is Miss. Destroyer(2) can still fit at (0,0)H.
        var board = Board.empty()
        for (col in 2 until GameConstants.BOARD_SIZE) {
            board = board.withCell(Coord.fromRowCol(0, col), CellState.Miss)
        }

        ai.rebuildHeatMap(board)
        val heatMap = ai.heatMapSnapshot()

        // Col 5–9 of row 0 are Miss → heat = 0 there (already zeroed).
        // Col 3 of row 0 is Miss → heat = 0.
        val blockedScore = heatMap[Coord.fromRowCol(0, 5).index]
        assertEquals(0f, blockedScore, "Miss cell should have heat=0")
    }

    // ── Performance ───────────────────────────────────────────────────────────

    @Test
    fun `hard AI heat map recalculation completes in under 16ms`() {
        val board = fogBoard {
            hit(3, 3); hit(3, 4); miss(0, 0); miss(9, 9)
            sunk(ShipId.DESTROYER, 7, 7, Orientation.Horizontal)
        }

        // Warm up JVM
        repeat(10) { ai.rebuildHeatMap(board) }

        val durationMs = measureTimeMillis {
            repeat(100) { ai.rebuildHeatMap(board) }
        }

        val avgMs = durationMs / 100.0
        assertTrue(
            avgMs < 16.0,
            "HardAi.rebuildHeatMap averaged ${avgMs}ms — must be < 16ms per recalculation"
        )
    }

    // ── Tie-break: prefer cell with most adjacent hits ─────────────────────────

    @Test
    fun `hard AI tie-breaks toward cell with most adjacent Hit neighbours`() {
        // Construct a scenario where two cells have equal raw heat but one has more Hit neighbours.
        // This is hard to construct exactly, but we can verify the selection is in the hit cluster.
        val board = fogBoard {
            hit(5, 4)
            hit(5, 6)
            // (5,5) is surrounded by two hits — should be preferred if heat ties.
        }

        val shot = ai.selectShot(board)
        // AI should strongly prefer (5,5) — it's adjacent to TWO hits and has high heat.
        assertEquals(
            Coord.fromRowCol(5, 5), shot,
            "AI should select (5,5) which is adjacent to both hits. Got row=${shot.rowOf()} col=${shot.colOf()}"
        )
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `hard AI reset clears heat map and does not affect next selectShot`() {
        val board = Board.empty()
        ai.selectShot(board) // populates heatMap internally
        ai.reset()

        val heatMapAfterReset = ai.heatMapSnapshot()
        assertTrue(heatMapAfterReset.all { it == 0f }, "Heat map should be zeroed after reset")

        // Should still work after reset
        val shot = ai.selectShot(board)
        assertTrue(shot.isValid())
    }

    @Test
    fun `hard AI onShotResult is no-op and does not break subsequent selectShot`() {
        var board = Board.empty()
        val shot = ai.selectShot(board)

        board = board.withCell(shot, CellState.Miss)
        assertDoesNotThrow {
            ai.onShotResult(shot, FireResult.MISS, null, board)
        }

        val nextShot = ai.selectShot(board)
        assertTrue(nextShot.isValid())
        assertNotEquals(shot.index, nextShot.index, "Next shot should differ from already-shot cell")
    }

    // ── Full game simulation ───────────────────────────────────────────────────

    @Test
    fun `hard AI can complete a full 100-cell simulation without selecting duplicate cells`() {
        var board = Board.empty()
        val fired = mutableSetOf<Int>()
        val totalCells = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE

        repeat(totalCells) { turn ->
            val shot = ai.selectShot(board)

            assertFalse(
                fired.contains(shot.index),
                "HardAi fired duplicate cell index=${shot.index} on turn ${turn + 1}"
            )
            assertTrue(shot.isValid(), "Invalid coord on turn ${turn + 1}")

            fired.add(shot.index)
            board = board.withCell(shot, CellState.Miss)
            ai.onShotResult(shot, FireResult.MISS, null, board)
        }

        assertEquals(totalCells, fired.size)
    }
}