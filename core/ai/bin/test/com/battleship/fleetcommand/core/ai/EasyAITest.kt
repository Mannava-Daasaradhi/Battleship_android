// FILE: core/ai/src/test/kotlin/com/battleship/fleetcommand/core/ai/EasyAiTest.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EasyAiTest {

    private lateinit var ai: EasyAi

    @BeforeEach
    fun setUp() {
        ai = EasyAi()
    }

    // ── Correctness ───────────────────────────────────────────────────────────

    @Test
    fun `easy AI never fires same cell twice across a simulated full game`() {
        var board = Board.empty()
        val firedCoords = mutableSetOf<Int>()
        val totalCells = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE

        // Simulate 100 shots (all cells)
        repeat(totalCells) {
            val shot = ai.selectShot(board)

            assertFalse(
                firedCoords.contains(shot.index),
                "EasyAi fired already-shot cell at index=${shot.index} on shot #${it + 1}"
            )

            firedCoords.add(shot.index)
            // Mark the cell as Miss so AI sees it as already shot
            board = board.withCell(shot, CellState.Miss)
            ai.onShotResult(shot, FireResult.MISS, null, board)
        }

        assertEquals(totalCells, firedCoords.size, "Expected exactly 100 unique cells fired")
    }

    @Test
    fun `easy AI fires within board bounds`() {
        val board = Board.empty()
        val totalCells = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE
        repeat(200) {
            val shot = ai.selectShot(board)
            assertTrue(shot.isValid(), "Shot coord ${shot.index} is out of bounds")
            assertTrue(shot.index in 0 until totalCells)
            assertTrue(shot.rowOf() in 0 until GameConstants.BOARD_SIZE)
            assertTrue(shot.colOf() in 0 until GameConstants.BOARD_SIZE)
        }
    }

    @Test
    fun `easy AI selects only from unshot cells when board is partially filled`() {
        // Mark 90 cells as Miss — only 10 remain
        var board = Board.empty()
        val alreadyShot = (0 until 90).map { Coord(it) }
        for (coord in alreadyShot) {
            board = board.withCell(coord, CellState.Miss)
        }

        repeat(50) {
            val shot = ai.selectShot(board)
            assertTrue(
                shot.index >= 90,
                "EasyAi selected an already-shot cell (index=${shot.index})"
            )
        }
    }

    @Test
    fun `easy AI selects the only remaining cell when one cell left`() {
        val board = boardWithOneCellLeft(row = 7, col = 3)
        val shot = ai.selectShot(board)
        assertEquals(Coord.fromRowCol(7, 3), shot)
    }

    @Test
    fun `easy AI shows statistical randomness across cells`() {
        // With 1000 shots on an empty board, we expect significant spread.
        val board = Board.empty()
        val totalCells = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE
        val hitCounts = IntArray(totalCells)

        repeat(1000) {
            val shot = ai.selectShot(board) // board stays empty — all cells eligible
            hitCounts[shot.index]++
        }

        val nonZeroCells = hitCounts.count { it > 0 }
        // With 1000 draws from 100 cells, expect nearly all cells hit at least once.
        assertTrue(
            nonZeroCells >= 80,
            "EasyAi is not sufficiently random — only $nonZeroCells/100 cells selected"
        )
    }

    // ── State / Reset ─────────────────────────────────────────────────────────

    @Test
    fun `easy AI reset is a no-op and does not affect subsequent behaviour`() {
        val board = Board.empty()
        ai.reset()
        val shot = ai.selectShot(board)
        assertTrue(shot.isValid())
    }

    @Test
    fun `easy AI onShotResult does not throw for any FireResult`() {
        val board = Board.empty()
        val coord = Coord.fromRowCol(0, 0)
        assertDoesNotThrow { ai.onShotResult(coord, FireResult.MISS, null, board) }
        assertDoesNotThrow { ai.onShotResult(coord, FireResult.HIT, null, board) }
        assertDoesNotThrow { ai.onShotResult(coord, FireResult.SUNK, null, board) }
    }

    @Test
    fun `easy AI throws when no unshot cells remain`() {
        // Mark all 100 cells as Miss
        var board = Board.empty()
        val totalCells = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE
        for (i in 0 until totalCells) {
            board = board.withCell(Coord(i), CellState.Miss)
        }
        assertThrows<IllegalArgumentException> { ai.selectShot(board) }
    }
}