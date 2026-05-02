// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/engine/GameEvent.kt
package com.battleship.fleetcommand.core.domain.engine

import com.battleship.fleetcommand.core.domain.board.Coord
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement

/**
 * Every user action or system signal that can drive a state transition.
 * Follows Section 24 sealed class pattern — subclass names are nouns, never verbs.
 *
 * Consumed exclusively by GameStateMachine.transition().
 */
sealed class GameEvent {

    // ── Placement phase ───────────────────────────────────────────────────

    /** A single ship has been placed (may still be a partial fleet). */
    data class ShipPlaced(val placement: ShipPlacement) : GameEvent()

    /** All 5 ships are now validly placed on the board. */
    data object AllShipsPlaced : GameEvent()

    /** Player tapped "Confirm Fleet" — placement is locked in. */
    data object PlacementConfirmed : GameEvent()

    // ── Online-only signals ───────────────────────────────────────────────

    /** Opponent connected to the lobby (but not yet ready). */
    data object OpponentJoined : GameEvent()

    /** Opponent confirmed their ship placement — both sides ready. */
    data object OpponentReady : GameEvent()

    // ── Battle phase ──────────────────────────────────────────────────────

    /** The active player tapped a cell to fire at it. */
    data class CellFired(val coord: Coord) : GameEvent()

    /**
     * The result of a fired shot has been resolved.
     * Carries the coord, outcome, and which player fired.
     */
    data class ShotResolved(
        val coord: Coord,
        val result: FireResult,
        val firedBy: PlayerSlot
    ) : GameEvent()

    /** All animations for the previous shot have finished playing. */
    data object AnimationComplete : GameEvent()

    /** The game has ended — [winner] is the victorious player. */
    data class GameEnded(val winner: PlayerSlot) : GameEvent()

    // ── Meta actions ──────────────────────────────────────────────────────

    /** The active player resigned — opponent wins immediately. */
    data object Resign : GameEvent()

    /** Player requested a full game restart back to Setup. */
    data object Restart : GameEvent()
}

/**
 * The raw outcome of a single shot, used inside [GameEvent.ShotResolved].
 * Kept as a simple enum — richer domain types live in the repository layer.
 */
enum class FireResult { HIT, MISS, SUNK }