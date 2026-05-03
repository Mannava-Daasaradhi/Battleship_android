// feature/lobby/src/test/kotlin/com/battleship/fleetcommand/feature/lobby/LobbyViewModelTest.kt

package com.battleship.fleetcommand.feature.lobby

import app.cash.turbine.test
import com.battleship.fleetcommand.core.testing.FakeFirebaseDatabase
import com.battleship.fleetcommand.core.testing.FakePreferencesRepository
import com.battleship.fleetcommand.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class LobbyViewModelTest {

    private lateinit var fakeRepo: FakeFirebaseDatabase
    private lateinit var fakePrefs: FakePreferencesRepository
    private lateinit var viewModel: LobbyViewModel

    @BeforeEach
    fun setUp() {
        fakeRepo  = FakeFirebaseDatabase()
        fakePrefs = FakePreferencesRepository(defaultPlayerName = "TestPlayer")
        viewModel = LobbyViewModel(
            repository            = fakeRepo,
            preferencesRepository = fakePrefs
        )
    }

    // ── host game ─────────────────────────────────────────────────────────────

    @Test
    fun `host game emits NavigateToWaiting on success`() = runTest {
        viewModel.effects.test {
            viewModel.onEvent(LobbyUiEvent.HostGame)
            val effect = awaitItem()
            assertTrue(effect is LobbyUiEffect.NavigateToWaiting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── join game ─────────────────────────────────────────────────────────────

    @Test
    fun `join game emits NavigateToWaiting on success`() = runTest {
        // First create a game to get a valid room code
        var roomCode = ""
        fakeRepo.createGame("Host").collect { result ->
            roomCode = (result as com.battleship.fleetcommand.core.domain.multiplayer.GameCreationResult.Success).roomCode
        }

        // Switch to guest uid for join
        fakeRepo.myUid = "uid-guest"
        val guestPrefs = FakePreferencesRepository(defaultPlayerName = "GuestPlayer")
        val guestVm = LobbyViewModel(fakeRepo, guestPrefs)

        guestVm.onEvent(LobbyUiEvent.RoomCodeInputChanged(roomCode))
        guestVm.effects.test {
            guestVm.onEvent(LobbyUiEvent.ConfirmJoin)
            val effect = awaitItem()
            assertTrue(effect is LobbyUiEffect.NavigateToWaiting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `join game shows error when room not found`() = runTest {
        viewModel.onEvent(LobbyUiEvent.RoomCodeInputChanged("XXXXXX"))
        viewModel.effects.test {
            viewModel.onEvent(LobbyUiEvent.ConfirmJoin)
            val effect = awaitItem()
            assertTrue(effect is LobbyUiEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── room code normalisation ───────────────────────────────────────────────

    @Test
    fun `room code input is normalised to uppercase`() = runTest {
        viewModel.onEvent(LobbyUiEvent.RoomCodeInputChanged("abc123"))
        val state = viewModel.uiState.value
        assertEquals("ABC123", state.roomCodeInput)
    }

    // ── loading state ─────────────────────────────────────────────────────────

    @Test
    fun `loading state is shown while creating game`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial state

            viewModel.onEvent(LobbyUiEvent.HostGame)

            // First emission after event: mode switches to HOSTING, isLoading = true
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            assertEquals(LobbyMode.HOSTING, loadingState.mode)

            // After success: isLoading = false
            val doneState = awaitItem()
            assertFalse(doneState.isLoading)

            cancelAndIgnoreRemainingEvents()
        }
    }
}