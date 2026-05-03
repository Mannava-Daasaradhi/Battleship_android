// FILE: core/domain/src/test/kotlin/com/battleship/fleetcommand/core/domain/GameStateMachineTest.kt
package com.battleship.fleetcommand.core.domain

import com.battleship.fleetcommand.core.domain.engine.*
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GameStateMachineTest {

    private lateinit var machine: GameStateMachine
    private val warnings = mutableListOf<String>()

    @BeforeEach
    fun setup() {
        warnings.clear()
        machine = GameStateMachine(logger = GameStateMachine.Logger { warnings.add(it) })
    }

    private fun dummyPlacement() = ShipPlacement(
        com.battleship.fleetcommand.core.domain.ship.ShipId.DESTROYER,
        Coord.fromRowCol(0, 0),
        Orientation.Horizontal
    )

    @Test
    fun `initial state is Setup`() {
        assertEquals(GameState.Setup, machine.state)
    }

    @Test
    fun `Setup + ShipPlaced stays in Setup`() {
        machine.transition(GameEvent.ShipPlaced(dummyPlacement()))
        assertEquals(GameState.Setup, machine.state)
    }

    @Test
    fun `Setup + AllShipsPlaced transitions to PlacementValid`() {
        machine.transition(GameEvent.AllShipsPlaced)
        assertEquals(GameState.PlacementValid, machine.state)
    }

    @Test
    fun `PlacementValid + ShipPlaced goes back to Setup`() {
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.ShipPlaced(dummyPlacement()))
        assertEquals(GameState.Setup, machine.state)
    }

    @Test
    fun `PlacementValid + PlacementConfirmed transitions to WaitingForOpponent`() {
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.PlacementConfirmed)
        assertEquals(GameState.WaitingForOpponent, machine.state)
    }

    @Test
    fun `WaitingForOpponent + OpponentReady transitions to Battle`() {
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.PlacementConfirmed)
        machine.transition(GameEvent.OpponentReady)
        assertEquals(GameState.Battle, machine.state)
    }

    @Test
    fun `Battle + any event auto-advances to PlayerTurn ONE`() {
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.PlacementConfirmed)
        machine.transition(GameEvent.OpponentReady)
        machine.transition(GameEvent.AllShipsPlaced) // any event
        assertEquals(GameState.PlayerTurn(PlayerSlot.ONE), machine.state)
    }

    @Test
    fun `PlayerTurn + CellFired transitions to Animating`() {
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.PlacementConfirmed)
        machine.transition(GameEvent.OpponentReady)
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.CellFired(Coord.fromRowCol(0, 0)))
        assertEquals(GameState.Animating, machine.state)
    }

    @Test
    fun `Animating + GameEnded transitions to GameOver`() {
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.PlacementConfirmed)
        machine.transition(GameEvent.OpponentReady)
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.CellFired(Coord.fromRowCol(0, 0)))
        machine.transition(GameEvent.GameEnded(PlayerSlot.ONE))
        assertEquals(GameState.GameOver(PlayerSlot.ONE), machine.state)
    }

    @Test
    fun `GameOver + Restart resets to Setup`() {
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.PlacementConfirmed)
        machine.transition(GameEvent.OpponentReady)
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.CellFired(Coord.fromRowCol(0, 0)))
        machine.transition(GameEvent.GameEnded(PlayerSlot.TWO))
        machine.transition(GameEvent.Restart)
        assertEquals(GameState.Setup, machine.state)
    }

    @Test
    fun `Resign from PlayerTurn gives victory to other player`() {
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.PlacementConfirmed)
        machine.transition(GameEvent.OpponentReady)
        machine.transition(GameEvent.AllShipsPlaced) // → PlayerTurn(ONE)
        machine.transition(GameEvent.Resign)
        assertEquals(GameState.GameOver(PlayerSlot.TWO), machine.state)
    }

    @Test
    fun `illegal transition is no-op and logs warning`() {
        // Setup + CellFired → illegal
        val stateBefore = machine.state
        machine.transition(GameEvent.CellFired(Coord.fromRowCol(0, 0)))
        assertEquals(stateBefore, machine.state)
        assertEquals(1, warnings.size)
    }

    @Test
    fun `GameOver + CellFired is illegal no-op`() {
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.PlacementConfirmed)
        machine.transition(GameEvent.OpponentReady)
        machine.transition(GameEvent.AllShipsPlaced)
        machine.transition(GameEvent.CellFired(Coord.fromRowCol(0, 0)))
        machine.transition(GameEvent.GameEnded(PlayerSlot.ONE))
        val stateBefore = machine.state
        machine.transition(GameEvent.CellFired(Coord.fromRowCol(1, 1)))
        assertEquals(stateBefore, machine.state)
        assertTrue(warnings.isNotEmpty())
    }

    @Test
    fun `reset returns machine to Setup without warning`() {
        machine.transition(GameEvent.AllShipsPlaced)
        machine.reset()
        assertEquals(GameState.Setup, machine.state)
        assertTrue(warnings.isEmpty())
    }
}