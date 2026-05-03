// FILE: core/domain/src/test/kotlin/com/battleship/fleetcommand/core/domain/CoordTest.kt
package com.battleship.fleetcommand.core.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CoordTest {

    @Test
    fun `fromRowCol roundtrip preserves row and col`() {
        val coord = Coord.fromRowCol(3, 7)
        assertEquals(3, coord.rowOf())
        assertEquals(7, coord.colOf())
    }

    @Test
    fun `index zero is row 0 col 0`() {
        val coord = Coord(0)
        assertEquals(0, coord.rowOf())
        assertEquals(0, coord.colOf())
    }

    @Test
    fun `index 99 is row 9 col 9`() {
        val coord = Coord(99)
        assertEquals(9, coord.rowOf())
        assertEquals(9, coord.colOf())
    }

    @Test
    fun `isValid returns true for all 100 cells`() {
        for (i in 0 until 100) assertTrue(Coord(i).isValid(), "index $i should be valid")
    }

    @Test
    fun `isValid returns false for negative index`() {
        assertFalse(Coord(-1).isValid())
    }

    @Test
    fun `isValid returns false for index 100`() {
        assertFalse(Coord(100).isValid())
    }

    @Test
    fun `adjacentCoords for corner A1 has exactly 2 neighbours`() {
        assertEquals(2, Coord.fromRowCol(0, 0).adjacentCoords().size)
    }

    @Test
    fun `adjacentCoords for centre cell has exactly 4 neighbours`() {
        assertEquals(4, Coord.fromRowCol(5, 5).adjacentCoords().size)
    }

    @Test
    fun `adjacentCoords for edge cell has exactly 3 neighbours`() {
        assertEquals(3, Coord.fromRowCol(0, 5).adjacentCoords().size)
    }

    @Test
    fun `diagonalCoords for corner has exactly 1 diagonal`() {
        assertEquals(1, Coord.fromRowCol(0, 0).diagonalCoords().size)
    }

    @Test
    fun `diagonalCoords for centre has exactly 4 diagonals`() {
        assertEquals(4, Coord.fromRowCol(5, 5).diagonalCoords().size)
    }

    @Test
    fun `toDisplayLabel returns correct label for A1`() {
        assertEquals("A1", Coord.fromRowCol(0, 0).toDisplayLabel())
    }

    @Test
    fun `toDisplayLabel returns correct label for J10`() {
        assertEquals("J10", Coord.fromRowCol(9, 9).toDisplayLabel())
    }

    @Test
    fun `fromDisplayLabel parses A1 correctly`() {
        val coord = Coord.fromDisplayLabel("A1")
        assertNotNull(coord)
        assertEquals(0, coord!!.rowOf())
        assertEquals(0, coord.colOf())
    }

    @Test
    fun `fromDisplayLabel parses J10 correctly`() {
        val coord = Coord.fromDisplayLabel("J10")
        assertNotNull(coord)
        assertEquals(9, coord!!.rowOf())
        assertEquals(9, coord.colOf())
    }

    @Test
    fun `fromDisplayLabel returns null for invalid input`() {
        assertNull(Coord.fromDisplayLabel("Z99"))
        assertNull(Coord.fromDisplayLabel(""))
        assertNull(Coord.fromDisplayLabel("A"))
    }

    @Test
    fun `iterateAllCoords visits exactly 100 cells`() {
        var count = 0
        iterateAllCoords { count++ }
        assertEquals(100, count)
    }

    @Test
    fun `iterateRowCol visits every row-col combination once`() {
        val visited = mutableSetOf<Pair<Int, Int>>()
        iterateRowCol { row, col, _ -> visited.add(row to col) }
        assertEquals(100, visited.size)
    }
}