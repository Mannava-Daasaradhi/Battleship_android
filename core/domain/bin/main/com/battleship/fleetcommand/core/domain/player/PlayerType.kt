// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/player/PlayerType.kt
package com.battleship.fleetcommand.core.domain.player

/**
 * Distinguishes a human player from an AI player.
 * Section 5.2 spec — sealed class with data subclasses, noun names (Section 24).
 */
sealed class PlayerType {
    data class Human(val name: String, val playerSlot: PlayerSlot) : PlayerType()
    data class AI(val difficulty: Difficulty) : PlayerType()
}