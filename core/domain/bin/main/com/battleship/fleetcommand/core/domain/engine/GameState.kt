// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/engine/GameState.kt
package com.battleship.fleetcommand.core.domain.engine

import com.battleship.fleetcommand.core.domain.player.PlayerSlot

/**
 * Represents every possible state the game state machine can be in.
 * Follows the sealed class naming pattern from Section 24:
 * subclass names are nouns/adjectives — never verbs.
 *
 * Transition table lives in GameStateMachine.kt.
 */
sealed class GameState {

    /** Ship placement phase — not all ships placed yet. */
    data object Setup : GameState()

    /** All ships placed and valid — awaiting player confirmation. */
    data object PlacementValid : GameState()

    /**
     * Online only — both players confirmed placement;
     * waiting for opponent to also confirm.
     */
    data object WaitingForOpponent : GameState()

    /**
     * Battle has begun — transitional state that auto-advances
     * to PlayerTurn(ONE) on the next transition tick.
     */
    data object Battle : GameState()

    /** The specified player may fire a shot. */
    data class PlayerTurn(val player: PlayerSlot) : GameState()

    /** The specified player's opponent is taking their shot. Grid locked. */
    data class OpponentTurn(val player: PlayerSlot) : GameState()

    /**
     * A shot has been fired — animations are playing.
     * Input is locked until AnimationComplete event is received.
     */
    data object Animating : GameState()

    /** Game is finished. [winner] is the player who won. */
    data class GameOver(val winner: PlayerSlot) : GameState()
}