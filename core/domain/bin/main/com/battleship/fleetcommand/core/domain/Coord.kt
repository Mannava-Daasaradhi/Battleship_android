// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/Coord.kt

package com.battleship.fleetcommand.core.domain

@JvmInline
value class Coord(val index: Int) {

    fun rowOf(): Int = index / GameConstants.BOARD_SIZE
    fun colOf(): Int = index % GameConstants.BOARD_SIZE

    fun isValid(): Boolean = index in 0 until (GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE)

    fun adjacentCoords(): List<Coord> = buildList {
        val r = rowOf()
        val c = colOf()
        if (r > 0) add(Coord((r - 1) * GameConstants.BOARD_SIZE + c))                          // North
        if (r < GameConstants.BOARD_SIZE - 1) add(Coord((r + 1) * GameConstants.BOARD_SIZE + c)) // South
        if (c > 0) add(Coord(r * GameConstants.BOARD_SIZE + (c - 1)))                           // West
        if (c < GameConstants.BOARD_SIZE - 1) add(Coord(r * GameConstants.BOARD_SIZE + (c + 1))) // East
    }

    fun diagonalCoords(): List<Coord> = buildList {
        val r = rowOf()
        val c = colOf()
        if (r > 0 && c > 0) add(fromRowCol(r - 1, c - 1))
        if (r > 0 && c < GameConstants.BOARD_SIZE - 1) add(fromRowCol(r - 1, c + 1))
        if (r < GameConstants.BOARD_SIZE - 1 && c > 0) add(fromRowCol(r + 1, c - 1))
        if (r < GameConstants.BOARD_SIZE - 1 && c < GameConstants.BOARD_SIZE - 1) add(fromRowCol(r + 1, c + 1))
    }

    fun allSurroundingCoords(): List<Coord> = adjacentCoords() + diagonalCoords()

    fun toDisplayLabel(): String {
        val row = ('A' + rowOf()).toString()
        val col = (colOf() + 1).toString()
        return "$row$col" // e.g. "A1", "J10"
    }

    companion object {
        fun fromRowCol(row: Int, col: Int): Coord =
            Coord(row * GameConstants.BOARD_SIZE + col)

        fun fromDisplayLabel(label: String): Coord? {
            if (label.length < 2) return null
            val row = label[0].uppercaseChar() - 'A'
            val col = label.drop(1).toIntOrNull()?.minus(1) ?: return null
            if (row !in 0 until GameConstants.BOARD_SIZE || col !in 0 until GameConstants.BOARD_SIZE) return null
            return fromRowCol(row, col)
        }
    }
}

// ── Zero-allocation iteration helpers ─────────────────────────────────────

inline fun iterateAllCoords(action: (Coord) -> Unit) {
    var i = 0
    while (i < GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE) {
        action(Coord(i))
        i++
    }
}

inline fun iterateRowCol(action: (row: Int, col: Int, Coord) -> Unit) {
    var row = 0
    while (row < GameConstants.BOARD_SIZE) {
        var col = 0
        while (col < GameConstants.BOARD_SIZE) {
            action(row, col, Coord.fromRowCol(row, col))
            col++
        }
        row++
    }
}