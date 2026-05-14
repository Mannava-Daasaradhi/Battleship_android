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
import io.mockk.verify
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
 * Covers confirmed bugs and sunk-ship feature:
 *   Bug 1 — YOUR FLEET grid shows no ships (myPlacements never populated from Room)
 *   Bug 2 — Hits never register (fireShot resolves against emptyList → always MISS)
 *   Bug 3 — Row 10 clipped (Compose layout fix; data layer verified here: both boards
 *            always have exactly 100 cells with no gaps)
 *   Feature — Sunk ship detection: SUNK cells turn orange on both boards;
 *              haptic fires once per sunk event for both attacker and defender.
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

    private fun savedState(gameId: String, uid: String = myUid): SavedStateHandle =
        SavedStateHandle(mapOf("gameId" to gameId, "myUid" to uid))

    private fun buildVm(gameId: String, uid: String = myUid): OnlineGameViewModel =
        OnlineGameViewModel(
            repository       = fakeFirebase,
            gameRepository   = fakeRoom,
            gameEngine       = GameEngine(),
            savedStateHandle = savedState(gameId, uid),
            hapticManager    = haptic,
        )

    private suspend fun setupBattleGame(): Pair<String, OnlineGameViewModel> {
        fakeFirebase.myUid = myUid
        var createdGameId = ""
        fakeFirebase.createGame("Me").collect { result ->
            if (result is GameCreationResult.Success) createdGameId = result.gameId
        }
        check(createdGameId.isNotBlank()) { "createGame returned blank id" }

        fakeFirebase.myUid = opUid
        fakeFirebase.joinGame("DUMMY", "Opponent").collect { /* consume */ }

        fakeFirebase.myUid = myUid
        fakeFirebase.submitShipPlacement(createdGameId, fullFleet)
        fakeFirebase.myUid = opUid
        fakeFirebase.submitShipPlacement(createdGameId, fullFleet)

        fakeRoom.saveBoardState(createdGameId, PlayerSlot.ONE, fullFleet)

        fakeFirebase.myUid = myUid
        val vm = buildVm(createdGameId, myUid)
        return createdGameId to vm
    }

    // =========================================================================
    // BUG 1 — YOUR FLEET grid shows no ships
    // =========================================================================

    @Nested
    inner class Bug1_YourFleetGridShowsNoShips {

        @Test
        fun `myBoard shows SHIP cells when Room has fleet saved before VM init`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            val shipCells = vm.uiState.value.myBoard.cells
                .filter { it.state == CellDisplayState.SHIP }

            assertTrue(shipCells.isNotEmpty(),
                "Expected SHIP cells in myBoard but found none.")
        }

        @Test
        fun `myBoard has exactly 17 SHIP cells for full standard fleet`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            val count = vm.uiState.value.myBoard.cells
                .count { it.state == CellDisplayState.SHIP }

            assertEquals(17, count,
                "Carrier(5)+Battleship(4)+Cruiser(3)+Submarine(3)+Destroyer(2)=17. Got $count")
        }

        @Test
        fun `myBoard is all WATER when Room has no saved placements`() = runTest {
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
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, listOf(
                ShipPlacement(ShipId.DESTROYER, Coord.fromRowCol(9, 8), Orientation.Horizontal)
            ))
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
    // =========================================================================

    @Nested
    inner class Bug2_HitsNeverRegister {

        @Test
        fun `opponent shot on Carrier cell resolves to HIT in myBoard`() = runTest {
            val (createdGameId, vm) = setupBattleGame()
            advanceUntilIdle()

            fakeFirebase.simulateOpponentShot(createdGameId, Coord.fromRowCol(0, 0))
            advanceUntilIdle()

            val cell = vm.uiState.value.myBoard.cells
                .first { it.coord == Coord.fromRowCol(0, 0) }

            assertTrue(
                cell.state == CellDisplayState.HIT || cell.state == CellDisplayState.SUNK,
                "Cell (0,0) is on the Carrier — expected HIT or SUNK. Got ${cell.state}."
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

            fakeFirebase.simulateOpponentShot(createdGameId, Coord.fromRowCol(0, 0))
            fakeFirebase.simulateOpponentShot(createdGameId, Coord.fromRowCol(0, 1))
            fakeFirebase.simulateOpponentShot(createdGameId, Coord.fromRowCol(9, 9))
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
            val engine = GameEngine()
            val outcome = engine.fireShot(
                coord       = Coord.fromRowCol(0, 0),
                placements  = emptyList(),
                shotHistory = emptySet()
            ).getOrNull()

            assertTrue(outcome is ShotOutcome.Miss,
                "With emptyList placements every shot is Miss — root cause of Bug 2")
        }
    }

    // =========================================================================
    // BUG 3 — Row 10 cut off on both grids
    // =========================================================================

    @Nested
    inner class Bug3_Row10GridClipped {

        @Test
        fun `myBoard emits exactly 100 cells`() = runTest {
            fakeRoom.saveBoardState("game-1", PlayerSlot.ONE, fullFleet)
            val vm = buildVm("game-1")
            advanceUntilIdle()

            assertEquals(100, vm.uiState.value.myBoard.cells.size,
                "myBoard must have 100 cells")
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
                "Row 10 (indices 90–99) must have all 10 cells")
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
    // =========================================================================

    @Nested
    inner class PlacementVmToOnlineVmIntegration {

        @Test
        fun `ships written by PlacementViewModel (Part A) appear in myBoard via OnlineGameViewModel (Part B)`() =
            runTest {
                val firebaseGameId = "firebase-abc-456"
                fakeRoom.saveBoardState(firebaseGameId, PlayerSlot.ONE, fullFleet)
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

            val vmB = buildVm("game-B")
            advanceUntilIdle()

            val count = vmB.uiState.value.myBoard.cells
                .count { it.state == CellDisplayState.SHIP }

            assertEquals(0, count,
                "VM for game-B must not see placements saved under game-A")
        }
    }

    // =========================================================================
    // SUNK SHIP DETECTION — Enemy waters board (attacker side)
    //
    // When I fire shots and the opponent's ship is fully sunk, all cells of that
    // ship must change from HIT (red) → SUNK (orange) on the enemy waters board.
    // The Destroyer occupies row 8, cols 0–1 (size 2).
    // =========================================================================

    @Nested
    inner class SunkShipDetection_EnemyWatersBoard {

        /**
         * Helper: simulates MY shot being written to Firebase (as attacker),
         * then the defender resolves and writes the result back.
         * This mirrors the real async round-trip:
         *   1. I call fireShot → shot written to Firebase with result=null
         *   2. Defender's VM resolves → writeShotResult called with result + shipId
         */
        private suspend fun simulateMyShot(
            gameId: String,
            coord: Coord,
            result: com.battleship.fleetcommand.core.domain.engine.FireResult,
            shipId: String? = null,
        ) {
            // Write my shot as the attacker (myUid)
            fakeFirebase.myUid = myUid
            fakeFirebase.fireShot(gameId, coord)
            // Simulate defender resolving it (writes result + shipId)
            val shotIndex = fakeFirebase.getGame(gameId)
                ?.let { @Suppress("UNCHECKED_CAST") (it["shots"] as? Map<String, List<*>>)?.get(myUid)?.size?.minus(1) }
                ?: 0
            fakeFirebase.writeShotResult(gameId, myUid, shotIndex, result, shipId)
        }

        @Test
        fun `partial hits on Destroyer show HIT (red) not SUNK`() = runTest {
            val (gameId, vm) = setupBattleGame()
            advanceUntilIdle()

            // Destroyer is at row 8, col 0 and col 1. Fire only col 0 → HIT, not SUNK
            simulateMyShot(gameId, Coord.fromRowCol(8, 0),
                com.battleship.fleetcommand.core.domain.engine.FireResult.HIT,
                ShipId.DESTROYER.name)
            advanceUntilIdle()

            val cell = vm.uiState.value.opponentBoard.cells
                .first { it.coord == Coord.fromRowCol(8, 0) }

            assertEquals(CellDisplayState.HIT, cell.state,
                "Single hit on Destroyer must show HIT not SUNK (ship not fully sunk yet)")
        }

        @Test
        fun `all Destroyer cells turn SUNK after both cells are hit`() = runTest {
            val (gameId, vm) = setupBattleGame()
            advanceUntilIdle()

            // Hit col 0 (HIT)
            simulateMyShot(gameId, Coord.fromRowCol(8, 0),
                com.battleship.fleetcommand.core.domain.engine.FireResult.HIT,
                ShipId.DESTROYER.name)
            // Hit col 1 (SUNK — this shot sinks the Destroyer)
            simulateMyShot(gameId, Coord.fromRowCol(8, 1),
                com.battleship.fleetcommand.core.domain.engine.FireResult.SUNK,
                ShipId.DESTROYER.name)
            advanceUntilIdle()

            val board = vm.uiState.value.opponentBoard
            val sunkCells = board.cells.filter { it.state == CellDisplayState.SUNK }

            assertEquals(2, sunkCells.size,
                "Both Destroyer cells (row 8, col 0 and 1) must be SUNK (orange). " +
                "Got: ${sunkCells.map { it.coord }}")
        }

        @Test
        fun `sinking Destroyer does not change cells of other ships`() = runTest {
            val (gameId, vm) = setupBattleGame()
            advanceUntilIdle()

            // Sink the Destroyer
            simulateMyShot(gameId, Coord.fromRowCol(8, 0),
                com.battleship.fleetcommand.core.domain.engine.FireResult.HIT,
                ShipId.DESTROYER.name)
            simulateMyShot(gameId, Coord.fromRowCol(8, 1),
                com.battleship.fleetcommand.core.domain.engine.FireResult.SUNK,
                ShipId.DESTROYER.name)
            advanceUntilIdle()

            val board = vm.uiState.value.opponentBoard
            // No cell should show HIT still (unless we also hit another ship partially)
            // and the total sunk count should be exactly 2 (Destroyer cells only)
            val sunkCount = board.cells.count { it.state == CellDisplayState.SUNK }
            assertEquals(2, sunkCount,
                "Only the 2 Destroyer cells should be SUNK; got $sunkCount SUNK cells")
        }

        @Test
        fun `miss cell stays MISS after adjacent ship is sunk`() = runTest {
            val (gameId, vm) = setupBattleGame()
            advanceUntilIdle()

            // Miss first
            simulateMyShot(gameId, Coord.fromRowCol(9, 9),
                com.battleship.fleetcommand.core.domain.engine.FireResult.MISS, null)
            // Then sink Destroyer
            simulateMyShot(gameId, Coord.fromRowCol(8, 0),
                com.battleship.fleetcommand.core.domain.engine.FireResult.HIT,
                ShipId.DESTROYER.name)
            simulateMyShot(gameId, Coord.fromRowCol(8, 1),
                com.battleship.fleetcommand.core.domain.engine.FireResult.SUNK,
                ShipId.DESTROYER.name)
            advanceUntilIdle()

            val missCell = vm.uiState.value.opponentBoard.cells
                .first { it.coord == Coord.fromRowCol(9, 9) }

            assertEquals(CellDisplayState.MISS, missCell.state,
                "Miss cell must stay MISS after a ship is sunk nearby")
        }
    }

    // =========================================================================
    // SUNK SHIP DETECTION — MY FLEET board (defender side)
    //
    // When the opponent sinks one of my ships, all cells of that ship must
    // show SUNK (orange) on MY FLEET board.
    // =========================================================================

    @Nested
    inner class SunkShipDetection_MyFleetBoard {

        @Test
        fun `all Destroyer cells show SUNK on my fleet after opponent sinks it`() = runTest {
            val (gameId, vm) = setupBattleGame()
            advanceUntilIdle()

            // Opponent fires at my Destroyer (row 8, cols 0–1)
            fakeFirebase.simulateOpponentShot(gameId, Coord.fromRowCol(8, 0))
            advanceUntilIdle()
            fakeFirebase.simulateOpponentShot(gameId, Coord.fromRowCol(8, 1))
            advanceUntilIdle()

            val board = vm.uiState.value.myBoard
            val cell0 = board.cells.first { it.coord == Coord.fromRowCol(8, 0) }
            val cell1 = board.cells.first { it.coord == Coord.fromRowCol(8, 1) }

            assertTrue(
                cell0.state == CellDisplayState.SUNK && cell1.state == CellDisplayState.SUNK,
                "Both Destroyer cells must be SUNK on my fleet board. " +
                "Got: (8,0)=${cell0.state}, (8,1)=${cell1.state}"
            )
        }

        @Test
        fun `partial opponent hits show HIT not SUNK on my fleet`() = runTest {
            val (gameId, vm) = setupBattleGame()
            advanceUntilIdle()

            // Hit only one cell of Destroyer — should be HIT not SUNK
            fakeFirebase.simulateOpponentShot(gameId, Coord.fromRowCol(8, 0))
            advanceUntilIdle()

            val cell = vm.uiState.value.myBoard.cells
                .first { it.coord == Coord.fromRowCol(8, 0) }

            assertEquals(CellDisplayState.HIT, cell.state,
                "Single hit on Destroyer should be HIT, not SUNK (ship not fully sunk)")
        }

        @Test
        fun `sunk Destroyer is marked in ownShips as sunk=true`() = runTest {
            val (gameId, vm) = setupBattleGame()
            advanceUntilIdle()

            fakeFirebase.simulateOpponentShot(gameId, Coord.fromRowCol(8, 0))
            advanceUntilIdle()
            fakeFirebase.simulateOpponentShot(gameId, Coord.fromRowCol(8, 1))
            advanceUntilIdle()

            val destroyerView = vm.uiState.value.myBoard.ownShips
                .firstOrNull { it.shipId == ShipId.DESTROYER }

            assertTrue(destroyerView != null, "Destroyer must be in ownShips")
            assertTrue(destroyerView!!.isSunk,
                "Destroyer ShipPlacementViewState.isSunk must be true after sinking")
        }
    }

    // =========================================================================
    // HAPTIC — Sunk events fire SHIP_SUNK haptic exactly once per ship
    // =========================================================================

    @Nested
    inner class HapticSunkEvents {

        @Test
        fun `defender receives SHIP_SUNK haptic exactly once when Destroyer is sunk`() = runTest {
            val (gameId, _) = setupBattleGame()
            advanceUntilIdle()

            // Opponent fires two shots that sink my Destroyer
            fakeFirebase.simulateOpponentShot(gameId, Coord.fromRowCol(8, 0))
            advanceUntilIdle()
            fakeFirebase.simulateOpponentShot(gameId, Coord.fromRowCol(8, 1))
            advanceUntilIdle()

            verify(exactly = 1) {
                haptic.perform(com.battleship.fleetcommand.core.ui.haptic.HapticEvent.SHIP_SUNK)
            }
        }

        @Test
        fun `defender SHIP_SUNK haptic does not re-fire on subsequent Firebase updates`() = runTest {
            val (gameId, _) = setupBattleGame()
            advanceUntilIdle()

            fakeFirebase.simulateOpponentShot(gameId, Coord.fromRowCol(8, 0))
            advanceUntilIdle()
            fakeFirebase.simulateOpponentShot(gameId, Coord.fromRowCol(8, 1))
            advanceUntilIdle()

            // Trigger another game state update (e.g., opponent connectivity change)
            fakeFirebase.simulateOpponentDisconnect(gameId)
            advanceUntilIdle()

            // Should still be exactly 1, not 2
            verify(exactly = 1) {
                haptic.perform(com.battleship.fleetcommand.core.ui.haptic.HapticEvent.SHIP_SUNK)
            }
        }
    }
}