// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/Coord.kt
package com.battleship.fleetcommand.core.domain

/**
 * Zero-allocation value class representing a cell on the 10×10 board.
 * Index is row-major: index = row * BOARD_SIZE + col
 */
@JvmInline
value class Coord(val index: Int) {

    fun rowOf(): Int = index / GameConstants.BOARD_SIZE
    fun colOf(): Int = index % GameConstants.BOARD_SIZE

    fun isValid(): Boolean = index in 0 until (GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE)

    /** Returns the 4 orthogonally adjacent coords (N/S/E/W) that are within bounds. */
    fun adjacentCoords(): List<Coord> = buildList {
        val r = rowOf()
        val c = colOf()
        val dim = GameConstants.BOARD_SIZE
        if (r > 0)       add(Coord((r - 1) * dim + c))       // North
        if (r < dim - 1) add(Coord((r + 1) * dim + c))       // South
        if (c > 0)       add(Coord(r * dim + (c - 1)))       // West
        if (c < dim - 1) add(Coord(r * dim + (c + 1)))       // East
    }

    /** Returns the 4 diagonal coords that are within bounds. */
    fun diagonalCoords(): List<Coord> = buildList {
        val r = rowOf()
        val c = colOf()
        val dim = GameConstants.BOARD_SIZE
        if (r > 0 && c > 0)             add(Coord.fromRowCol(r - 1, c - 1))
        if (r > 0 && c < dim - 1)       add(Coord.fromRowCol(r - 1, c + 1))
        if (r < dim - 1 && c > 0)       add(Coord.fromRowCol(r + 1, c - 1))
        if (r < dim - 1 && c < dim - 1) add(Coord.fromRowCol(r + 1, c + 1))
    }

    /** Converts to display label, e.g. A1, J10. */
    fun toDisplayLabel(): String {
        val row = ('A' + rowOf()).toString()
        val col = (colOf() + 1).toString()
        return "$row$col"
    }

    override fun toString(): String = "Coord(${toDisplayLabel()})"

    companion object {
        fun fromRowCol(row: Int, col: Int): Coord =
            Coord(row * GameConstants.BOARD_SIZE + col)

        /**
         * Parses a display label like "A1" or "J10".
         * Returns null if the label is malformed or out of bounds.
         */
        fun fromDisplayLabel(label: String): Coord? {
            if (label.length < 2) return null
            val row = label[0].uppercaseChar() - 'A'
            val col = label.drop(1).toIntOrNull()?.minus(1) ?: return null
            val dim = GameConstants.BOARD_SIZE
            if (row !in 0 until dim || col !in 0 until dim) return null
            return fromRowCol(row, col)
        }
    }
}

// ── Zero-allocation board iteration helpers ────────────────────────────────

/** Iterates all 100 board cells without boxing. */
inline fun iterateAllCoords(action: (Coord) -> Unit) {
    val total = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE
    var i = 0
    while (i < total) {
        action(Coord(i))
        i++
    }
}

/** Iterates all cells with explicit row × col — zero allocation. */
inline fun iterateRowCol(action: (row: Int, col: Int, Coord) -> Unit) {
    val dim = GameConstants.BOARD_SIZE
    var row = 0
    while (row < dim) {
        var col = 0
        while (col < dim) {
            action(row, col, Coord.fromRowCol(row, col))
            col++
        }
        row++
    }
}