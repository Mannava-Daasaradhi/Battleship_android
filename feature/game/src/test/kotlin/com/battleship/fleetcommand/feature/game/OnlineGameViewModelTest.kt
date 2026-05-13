// FILE: feature/game/src/test/kotlin/com/battleship/fleetcommand/feature/game/OnlineGameViewModelTest.kt
package com.battleship.fleetcommand.feature.game

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.engine.GameEngine
import com.battleship.fleetcommand.core.domain.engine.ShotOutcome
import com.battleship.fleetcommand.core.domain.multiplayer.GameCreationResult
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.testing.FakeFirebaseDatabase
import com.battleship.fleetcommand.core.testing.FakeGameRepository
import com.battleship.fleetcommand.core.testing.MainDispatcherRule
import com.battleship.fleetcommand.core.ui.haptic.HapticManager
import com.battleship.fleetcommand.core.ui.model.CellDisplayState
import com.battleship.fleetcommand.feature.game.online.OnlineGameViewModel
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Unit tests for [OnlineGameViewModel].
 *
 * Covers all three confirmed device bugs:
 *   Bug 1 — YOUR FLEET grid shows no ships (myPlacements never populated from Room)
 *   Bug 2 — Hits never register (fireShot resolves against emptyList → always MISS)
 *   Bug 3 — Row 10 clipped (Compose layout fix in OnlineBattleScreen; data layer
 *            verified here: both boards always have exactly 100 cells with no gaps)
 *
 * Test infrastructure (pure JVM — zero Android context):
 *   [FakeGameRepository]   — in-memory Room substitute
 *   [FakeFirebaseDatabase] — in-memory Firebase Realtime DB substitute
 *   [MainDispatcherRule]   — replaces Dispatchers.Main with a TestDispatcher
 *   mockk(relaxed = true)  — no-op HapticManager
 */
@ExtendWith(MainDispatcherRule::class)
class OnlineGameViewModelTest {

    // ── UIDs ──────────────────────────────────────────────────────────────────
    private val myUid = "uid-me"
    private val opUid = "uid-opponent"

    // ── Standard 5-ship fleet ─────────────────────────────────────────────────
    // Non-overlapping rows. Row 9 / col 9 is always empty (useful for MISS tests).
    // Carrier(5)    → row 0, cols 0–4
    // Battleship(4) → row 2, cols 0–3
    // Cruiser(3)    → row 4, cols 0–2
    // Submarine(3)  → row 6, cols 0–2
    // Destroyer(2)  → row 8, cols 0–1
    // Total ship cells = 17
    private val fullFleet: List<ShipPlacement> = listOf(
        ShipPlacement(ShipId.CARRIER,    Coord.fromRowCol(0, 0), Orientation.Horizontal),
        ShipPlacement(ShipId.BATTLESHIP, Coord.fromRowCol(2, 0), Orientation.Horizontal),
        ShipPlacement(ShipId.CRUISER,    Coord.fromRowCol(4, 0), Orientation.Horizontal),
        ShipPlacement(ShipId.SUBMARINE,  Coord.fromRowCol(6, 0), Orientation.Horizontal),
        ShipPlacement(ShipId.DESTROYER,  Coord.fromRowCol(8, 0), Orientation.Horizontal),
    )

    // ── Fakes ─────────────────────────────────────────────────────────────────
    private lateinit var fakeFirebase: FakeFirebaseDatabase
    private lateinit var fakeRoom:     FakeGameRepository
    private lateinit var haptic:       HapticManager

    @BeforeEach
    fun setUp() {
        fakeFirebase = FakeFirebaseDatabase().also { it.myUid = myUid }
        fakeRoom     = FakeGameRepository()
        haptic       = mockk(relaxed = true)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a [SavedStateHandle] pre-populated with the keys that
     * [OnlineGameViewModel] reads via `savedStateHandle.toRoute<OnlineBattleRoute>()`.
     * Navigation serialises @Serializable route args by property name.
     */
    private fun savedState(gameId: String, uid: String = myUid): SavedStateHandle =
        SavedStateHandle(mapOf("gameId" to gameId, "myUid" to uid))

    /** Creates the VM wired to the current fakes. */
    private fun buildVm(gameId: String, uid: String = myUid): OnlineGameViewModel =
        OnlineGameViewModel(
            repository       = fakeFirebase,
            gameRepository   = fakeRoom,
            gameEngine       = GameEngine(),
            savedStateHandle = savedState(gameId, uid),
            hapticManager    = haptic,
        )

    /**
     * Full end-to-end game setup:
     * 1. Host creates game → real gameId assigned by the fake.
     * 2. Guest joins.
     * 3. Both submit placements → status advances to "battle".
     * 4. MY placements written to Room (mirrors PlacementViewModel Part A fix).
     *
     * Returns Pair(createdGameId, host ViewModel ready for assertions).
     */
    private suspend fun setupBattleGame(): Pair<String, OnlineGameViewModel> {
        fakeFirebase.myUid = myUid
        var createdGameId = ""
        fakeFirebase.createGame("Me").collect { result ->
            if (result is GameCreationResult.Success) createdGameId = result.gameId
        }
        check(createdGameId.isNotBlank()) { "createGame returned blank id" }

        // Guest joins (dummy room code accepted by fake)
        fakeFirebase.myUid = opUid
        fakeFirebase.joinGame("DUMMY", "Opponent").collect { /* consume */ }

        // Both submit — fake advances status to "battle" once both are ready
        fakeFirebase.myUid = myUid
        fakeFirebase.submitShipPlacement(createdGameId, fullFleet)
        fakeFirebase.myUid = opUid
        fakeFirebase.submitShipPlacement(createdGameId, fullFleet)

        // Part A fix: PlacementViewModel saves to Room before navigating
        fakeRoom.saveBoardState(createdGameId, PlayerSlot.ONE, fullFleet)

        fakeFirebase.myUid = myUid
        val vm = buildVm(createdGameId, myUid)
        return createdGameId to vm
    }

    // =========================================================================
    // BUG 1 — YOUR FLEET grid shows no ships
    //
    // Root cause: myPlacements stayed emptyList() — no GameRepository injection,
    //             no loadMyPlacements() call in init{}.
    // Fix (Part B): inject GameRepository; init{} calls loadMyPlacements() which
    //               reads the state that PlacementViewModel wrote (Part A).
    // =========================================================================

    @Nested
    inner class Bug1_YourFleetGridShowsNoShips {

        @Test
        fun `myBoard shows SHIP cells when Room has fleet saved before VM init`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()   // let loadMyPlacements() coroutine complete

            val shipCells = vm.uiState.value.myBoard.cells
                .filter { it.state == CellDisplayState.SHIP }

            assertTrue(shipCells.isNotEmpty(),
                "Expected SHIP cells in myBoard but found none. " +
                "loadMyPlacements() is not populating myPlacements from Room.")
        }

        @Test
        fun `myBoard has exactly 17 SHIP cells for full standard fleet`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            val count = vm.uiState.value.myBoard.cells
                .count { it.state == CellDisplayState.SHIP }

            assertEquals(17, count,
                "Carrier(5)+Battleship(4)+Cruiser(3)+Submarine(3)+Destroyer(2)=17. " +
                "Got $count — 0 means Bug 1 is still present.")
        }

        @Test
        fun `myBoard is all WATER when Room has no saved placements`() = runTest {
            // No saveBoardState call — confirms graceful empty-state handling
            val vm = buildVm("game-no-fleet")
            advanceUntilIdle()

            val shipCells = vm.uiState.value.myBoard.cells
                .filter { it.state == CellDisplayState.SHIP }

            assertTrue(shipCells.isEmpty(),
                "With nothing in Room, board should show all WATER")
        }

        @Test
        fun `ownShips list contains all 5 ship types from saved fleet`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            val shipIds  = vm.uiState.value.myBoard.ownShips.map { it.shipId }.toSet()
            val expected = fullFleet.map { it.shipId }.toSet()

            assertEquals(expected, shipIds,
                "ownShips must contain all 5 ShipIds from the saved placements")
        }

        @Test
        fun `PlayerSlot TWO placements are NOT loaded into myBoard (slot isolation)`() = runTest {
            // VM always reads PlayerSlot.ONE — TWO placements must be invisible to it
            fakeRoom.saveBoardState("game-1", PlayerSlot.TWO, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            val count = vm.uiState.value.myBoard.cells
                .count { it.state == CellDisplayState.SHIP }

            assertEquals(0, count,
                "Placements saved as PlayerSlot.TWO must not appear in myBoard")
        }

        @Test
        fun `latest write wins when saveBoardState called twice with same gameId`() = runTest {
            // First (stale) call
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, listOf(
                ShipPlacement(ShipId.DESTROYER, Coord.fromRowCol(9, 8), Orientation.Horizontal)
            ))
            // Second (final) call — full fleet
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)

            val vm = buildVm("game-1")
            advanceUntilIdle()

            val count = vm.uiState.value.myBoard.cells
                .count { it.state == CellDisplayState.SHIP }

            assertEquals(17, count, "VM should load the latest placements (17 cells)")
        }
    }

    // =========================================================================
    // BUG 2 — Hits never register (every shot resolves as MISS)
    //
    // Root cause: direct consequence of Bug 1.
    //   resolveNewOpponentShots() calls gameEngine.fireShot(coord, myPlacements, ...)
    //   With myPlacements=emptyList() the engine has no ships → every shot is MISS.
    // Fix: same as Bug 1. Populated myPlacements → engine resolves HIT/SUNK correctly.
    // =========================================================================

    @Nested
    inner class Bug2_HitsNeverRegister {

        @Test
        fun `opponent shot on Carrier cell resolves to HIT in myBoard`() = runTest {
            val (createdGameId, vm) = setupBattleGame()
            advanceUntilIdle()

            // Carrier head is at (0,0)
            fakeFirebase.simulateOpponentShot(createdGameId, Coord.fromRowCol(0, 0))
            advanceUntilIdle()

            val cell = vm.uiState.value.myBoard.cells
                .first { it.coord == Coord.fromRowCol(0, 0) }

            assertTrue(
                cell.state == CellDisplayState.HIT || cell.state == CellDisplayState.SUNK,
                "Cell (0,0) is on the Carrier — expected HIT or SUNK. " +
                "Got ${cell.state}. SHIP/WATER means myPlacements was empty (Bug 2)."
            )
        }

        @Test
        fun `opponent shot on empty cell (9,9) resolves to MISS`() = runTest {
            val (createdGameId, vm) = setupBattleGame()
            advanceUntilIdle()

            fakeFirebase.simulateOpponentShot(createdGameId, Coord.fromRowCol(9, 9))
            advanceUntilIdle()

            val cell = vm.uiState.value.myBoard.cells
                .first { it.coord == Coord.fromRowCol(9, 9) }

            assertEquals(CellDisplayState.MISS, cell.state,
                "Empty cell (9,9) should resolve as MISS")
        }

        @Test
        fun `MISS does not alter any ship cells (17 cells still occupied after miss)`() = runTest {
            val (createdGameId, vm) = setupBattleGame()
            advanceUntilIdle()

            fakeFirebase.simulateOpponentShot(createdGameId, Coord.fromRowCol(9, 9))
            advanceUntilIdle()

            val occupiedCount = vm.uiState.value.myBoard.cells.count {
                it.state == CellDisplayState.SHIP ||
                it.state == CellDisplayState.HIT  ||
                it.state == CellDisplayState.SUNK
            }

            assertEquals(17, occupiedCount,
                "MISS must not touch ship cells. Expected 17 occupied cells, got $occupiedCount.")
        }

        @Test
        fun `two hits and one miss all resolve independently`() = runTest {
            val (createdGameId, vm) = setupBattleGame()
            advanceUntilIdle()

            // Carrier occupies (0,0)–(0,4)
            fakeFirebase.simulateOpponentShot(createdGameId, Coord.fromRowCol(0, 0)) // HIT
            fakeFirebase.simulateOpponentShot(createdGameId, Coord.fromRowCol(0, 1)) // HIT
            fakeFirebase.simulateOpponentShot(createdGameId, Coord.fromRowCol(9, 9)) // MISS
            advanceUntilIdle()

            val board = vm.uiState.value.myBoard
            val hitOrSunk = board.cells.count {
                it.state == CellDisplayState.HIT || it.state == CellDisplayState.SUNK
            }
            val miss = board.cells.count { it.state == CellDisplayState.MISS }

            assertTrue(hitOrSunk >= 2, "Expected ≥2 HIT/SUNK cells, got $hitOrSunk")
            assertEquals(1, miss, "Expected exactly 1 MISS cell, got $miss")
        }

        @Test
        fun `engine root cause — fireShot on emptyList always returns Miss (documents Bug 2)`() {
            // Not a VM test — documents WHY Bug 2 happened.
            // GameEngine.fireShot with emptyList placements cannot match any ship.
            val engine = GameEngine()
            val outcome = engine.fireShot(
                coord        = Coord.fromRowCol(0, 0), // would be a Carrier HIT with real fleet
                placements   = emptyList(),             // the broken pre-fix state
                shotHistory  = emptySet()
            ).getOrNull()

            assertTrue(outcome is ShotOutcome.Miss,
                "With emptyList placements every shot is Miss — root cause of Bug 2")
        }
    }

    // =========================================================================
    // BUG 3 — Row 10 cut off on both grids
    //
    // Primary fix: Modifier.weight(1f) on both GameGrid composables in
    //              OnlineBattleScreen.kt (Compose layer — not testable here).
    // Data-layer guarantee: both BoardViewState lists must contain all 100 cells
    //              (indices 0–99). Truncation here would compound the visual cut-off.
    // =========================================================================

    @Nested
    inner class Bug3_Row10GridClipped {

        @Test
        fun `myBoard emits exactly 100 cells`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            assertEquals(100, vm.uiState.value.myBoard.cells.size,
                "myBoard must have 100 cells — fewer would compound the Row 10 clip (Bug 3)")
        }

        @Test
        fun `opponentBoard emits exactly 100 cells`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            assertEquals(100, vm.uiState.value.opponentBoard.cells.size,
                "opponentBoard must also have 100 cells")
        }

        @Test
        fun `myBoard cell indices are exactly 0–99 with no gaps`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            val indices = vm.uiState.value.myBoard.cells
                .map { it.coord.index }
                .sorted()

            assertEquals((0..99).toList(), indices,
                "Cell indices must be 0..99 with no gaps or duplicates")
        }

        @Test
        fun `row 10 cells (indices 90–99) all present in myBoard`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            val row10 = vm.uiState.value.myBoard.cells
                .filter { it.coord.index in 90..99 }

            assertEquals(10, row10.size,
                "Row 10 (indices 90–99) must have all 10 cells — " +
                "missing cells compound the visual clip confirmed on device (Bug 3)")
        }

        @Test
        fun `row 10 cells (indices 90–99) all present in opponentBoard`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            val row10 = vm.uiState.value.opponentBoard.cells
                .filter { it.coord.index in 90..99 }

            assertEquals(10, row10.size,
                "opponentBoard row 10 must also have all 10 cells")
        }
    }

    // =========================================================================
    // INITIAL STATE / SMOKE TESTS
    // =========================================================================

    @Nested
    inner class InitialState {

        @Test
        fun `isMyTurn is false before any Firebase game state arrives`() = runTest {
            val vm = buildVm("game-1")
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isMyTurn)
        }

        @Test
        fun `gameStatus is WAITING on init`() = runTest {
            val vm = buildVm("game-1")
            advanceUntilIdle()
            assertEquals(OnlineGameViewModel.GameStatus.WAITING, vm.uiState.value.gameStatus)
        }

        @Test
        fun `gameId and myUid from route are reflected in UiState`() = runTest {
            val vm = buildVm("my-game-id", "my-uid")
            advanceUntilIdle()
            assertEquals("my-game-id", vm.uiState.value.gameId)
            assertEquals("my-uid",     vm.uiState.value.myUid)
        }
    }

    // =========================================================================
    // RESIGN
    // =========================================================================

    @Nested
    inner class ResignGame {

        @Test
        fun `ResignGame emits NavigateToGameOver effect with winner = Opponent`() = runTest {
            val vm = buildVm("game-1")
            advanceUntilIdle()

            vm.effects.test {
                vm.onEvent(OnlineGameViewModel.UiEvent.ResignGame)

                val effect = awaitItem()

                assertTrue(effect is OnlineGameViewModel.UiEffect.NavigateToGameOver,
                    "Expected NavigateToGameOver, got $effect")
                assertEquals("Opponent",
                    (effect as OnlineGameViewModel.UiEffect.NavigateToGameOver).winner)

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // =========================================================================
    // INTEGRATION — PlacementViewModel Part A ↔ OnlineGameViewModel Part B
    //
    // Verifies the complete write→read handshake through FakeGameRepository,
    // mirroring the on-device flow:
    //   PlacementViewModel.confirmPlacement() [Part A] writes PlayerSlot.ONE to Room.
    //   OnlineGameViewModel.loadMyPlacements() [Part B] reads PlayerSlot.ONE from Room.
    // =========================================================================

    @Nested
    inner class PlacementVmToOnlineVmIntegration {

        @Test
        fun `ships written by PlacementViewModel (Part A) appear in myBoard via OnlineGameViewModel (Part B)`() =
            runTest {
                val firebaseGameId = "firebase-abc-456"

                // Part A: what PlacementViewModel.confirmPlacement() ONLINE does
                fakeRoom.saveBoardState(firebaseGameId, PlayerSlot.ONE, fullFleet)

                // Part B: OnlineGameViewModel reads them back in loadMyPlacements()
                val vm = buildVm(firebaseGameId)
                advanceUntilIdle()

                val count = vm.uiState.value.myBoard.cells
                    .count { it.state == CellDisplayState.SHIP }

                assertEquals(17, count,
                    "All 17 ship cells written in Part A must be readable in Part B")
            }

        @Test
        fun `different gameIds are fully isolated in Room`() = runTest {
            fakeRoom.saveBoardState("game-A", PlayerSlot.ONE, fullFleet)
            // game-B has nothing saved

            val vmB = buildVm("game-B")
            advanceUntilIdle()

            val count = vmB.uiState.value.myBoard.cells
                .count { it.state == CellDisplayState.SHIP }

            assertEquals(0, count,
                "VM for game-B must not see placements saved under game-A")
        }
    }
}