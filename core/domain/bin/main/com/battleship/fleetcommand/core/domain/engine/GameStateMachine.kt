// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/engine/GameStateMachine.kt
package com.battleship.fleetcommand.core.domain.engine

import com.battleship.fleetcommand.core.domain.player.PlayerSlot

/**
 * Pure state machine for Battleship game flow.
 *
 * Rules (from Section 4.3):
 *  - transition() applies the event and returns the new state.
 *  - Illegal transitions are no-ops: state is unchanged, warning is logged.
 *  - No Android imports. No coroutines. No DI.
 *  - Timber replaced with pluggable [Logger] interface (NOTE 6).
 */
class GameStateMachine(
    private val logger: Logger = DefaultLogger
) {

    // ── Logger contract ───────────────────────────────────────────────────

    fun interface Logger {
        fun warn(message: String)
    }

    private object DefaultLogger : Logger {
        override fun warn(message: String) {
            println("GameStateMachine WARNING: $message")
        }
    }

    // ── State ─────────────────────────────────────────────────────────────

    private var _state: GameState = GameState.Setup

    val state: GameState get() = _state

    // ── Transition ────────────────────────────────────────────────────────

    /**
     * Attempts to advance the state machine with [event].
     * Returns the resulting [GameState] — unchanged if the transition is illegal.
     */
    fun transition(event: GameEvent): GameState {
        val next = computeNextState(_state, event)
        if (next == null) {
            logger.warn("Illegal transition: state=$_state event=$event — ignoring")
            return _state
        }
        _state = next
        return _state
    }

    /** Resets the machine to [GameState.Setup] without logging a warning. */
    fun reset() {
        _state = GameState.Setup
    }

    // ── Transition table (Section 4.3) ────────────────────────────────────

    private fun computeNextState(current: GameState, event: GameEvent): GameState? =
        when {

            // ── Placement phase ───────────────────────────────────────────

            current is GameState.Setup &&
                event is GameEvent.ShipPlaced ->
                GameState.Setup                          // partial placement, stay

            current is GameState.Setup &&
                event is GameEvent.AllShipsPlaced ->
                GameState.PlacementValid

            current is GameState.PlacementValid &&
                event is GameEvent.ShipPlaced ->
                GameState.Setup                          // modification — back to partial

            current is GameState.PlacementValid &&
                event is GameEvent.PlacementConfirmed ->
                GameState.WaitingForOpponent             // online & local both go here;
                                                         // caller decides when to skip to Battle

            // ── Online waiting ────────────────────────────────────────────

            current is GameState.WaitingForOpponent &&
                event is GameEvent.OpponentJoined ->
                GameState.WaitingForOpponent             // joined but not ready yet

            current is GameState.WaitingForOpponent &&
                event is GameEvent.OpponentReady ->
                GameState.Battle

            // ── Battle start ──────────────────────────────────────────────

            current is GameState.Battle ->
                GameState.PlayerTurn(PlayerSlot.ONE)     // auto-advance on any event

            // ── Turn cycling ──────────────────────────────────────────────

            current is GameState.PlayerTurn &&
                event is GameEvent.CellFired ->
                GameState.Animating

            current is GameState.OpponentTurn &&
                event is GameEvent.ShotResolved ->
                GameState.Animating

            // ── Animation resolution ──────────────────────────────────────

            current is GameState.Animating &&
                event is GameEvent.GameEnded ->
                GameState.GameOver(event.winner)

            current is GameState.Animating &&
                event is GameEvent.AnimationComplete ->
                null  // caller must decide: next PlayerTurn / OpponentTurn or GameOver
                      // returning null here would log a warning — callers use GameEnded first

            // ── Game over ─────────────────────────────────────────────────

            current is GameState.GameOver &&
                event is GameEvent.Restart ->
                GameState.Setup

            // ── Resign (valid from any active state) ──────────────────────

            event is GameEvent.Resign -> {
                val winner = when (current) {
                    is GameState.PlayerTurn ->
                        if (current.player == PlayerSlot.ONE) PlayerSlot.TWO else PlayerSlot.ONE
                    is GameState.OpponentTurn ->
                        current.player   // the opponent (non-resigning) player wins
                    else ->
                        PlayerSlot.TWO   // safe fallback
                }
                GameState.GameOver(winner)
            }

            // ── Illegal transitions — all return null → no-op + warning ───

            // Battle + ShipPlaced → illegal
            // GameOver + CellFired → illegal
            // Animating + CellFired → illegal (input lock)
            // WaitingForOpponent + CellFired → illegal
            // Setup + CellFired → illegal

            else -> null
        }
}