// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/player/PlayerSlot.kt
package com.battleship.fleetcommand.core.domain.player

/**
 * Identifies which of the two player seats a player occupies.
 * Used throughout the state machine, game engine, and UI layer.
 */
enum class PlayerSlot { ONE, TWO }