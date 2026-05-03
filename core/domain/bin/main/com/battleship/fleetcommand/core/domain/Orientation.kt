// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/Orientation.kt
package com.battleship.fleetcommand.core.domain

/**
 * Ship orientation on the board.
 * Section 24: sealed class with data object subclasses; extension for toggling.
 */
sealed class Orientation {
    data object Horizontal : Orientation()
    data object Vertical : Orientation()
}

/** Toggles between Horizontal and Vertical. */
fun Orientation.toggle(): Orientation = when (this) {
    is Orientation.Horizontal -> Orientation.Vertical
    is Orientation.Vertical   -> Orientation.Horizontal
}