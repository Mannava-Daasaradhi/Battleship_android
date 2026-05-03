// core/multiplayer/src/test/kotlin/com/battleship/fleetcommand/core/multiplayer/FirebaseMatchRepositoryTest.kt

package com.battleship.fleetcommand.core.multiplayer

import app.cash.turbine.test
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.multiplayer.GameCreationResult
import com.battleship.fleetcommand.core.domain.multiplayer.JoinResult
import com.battleship.fleetcommand.core.testing.FakeFirebaseDatabase
import com.battleship.fleetcommand.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class FirebaseMatchRepositoryTest {

    private lateinit var fake: FakeFirebaseDatabase

    @BeforeEach
    fun setUp() {
        fake = FakeFirebaseDatabase()
        fake.myUid = "uid-host"
    }

    // ── createGame ────────────────────────────────────────────────────────────

    @Test
    fun `createGame writes correct meta structure`() = runTest {
        fake.createGame("Alice").test {
            val result = awaitItem()
            assertTrue(result is GameCreationResult.Success)
            val success = result as GameCreationResult.Success
            assertTrue(success.roomCode.length == 6)
            assertTrue(success.roomCode.all { it.isLetterOrDigit() })

            val game = fake.getGame(success.gameId)
            assertNotNull(game)
            assertEquals("uid-host", game!!["hostUid"])
            assertNull(game["guestUid"])
            assertEquals("waiting", game["status"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── joinGame ──────────────────────────────────────────────────────────────

    @Test
    fun `joinGame fails when room code not found`() = runTest {
        fake.joinGame("XXXXXX", "Bob").test {
            val result = awaitItem()
            assertEquals(JoinResult.NotFound, result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `joinGame fails when game already started`() = runTest {
        // Host creates game
        var gameId = ""
        fake.createGame("Alice").test {
            val r = awaitItem() as GameCreationResult.Success
            gameId = r.gameId
            cancelAndIgnoreRemainingEvents()
        }

        // Advance status so it's no longer waiting
        fake.advanceToStatus(gameId, "battle")

        // Retrieve room code from game store
        val roomCode = (fake.getGame(gameId)!!["roomCode"] as String)

        fake.myUid = "uid-guest"
        fake.joinGame(roomCode, "Bob").test {
            val result = awaitItem()
            assertEquals(JoinResult.GameAlreadyStarted, result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── submitShipPlacement ───────────────────────────────────────────────────

    @Test
    fun `submitShipPlacement writes ships and ready=true`() = runTest {
        var gameId = ""
        fake.createGame("Alice").test {
            gameId = (awaitItem() as GameCreationResult.Success).gameId
            cancelAndIgnoreRemainingEvents()
        }

        val result = fake.submitShipPlacement(gameId, emptyList())
        assertTrue(result.isSuccess)

        val game = fake.getGame(gameId)!!
        @Suppress("UNCHECKED_CAST")
        val players = game["players"] as Map<String, Any>
        // ready flag set via FakeFirebaseDatabase.submitShipPlacement
        assertNotNull(players["uid-host"])
    }

    // ── fireShot ──────────────────────────────────────────────────────────────

    @Test
    fun `fireShot writes correct shot structure`() = runTest {
        var gameId = ""
        fake.createGame("Alice").test {
            gameId = (awaitItem() as GameCreationResult.Success).gameId
            cancelAndIgnoreRemainingEvents()
        }
        fake.advanceToStatus(gameId, "battle")

        val coord = Coord.fromRowCol(3, 7)
        val result = fake.fireShot(gameId, coord)
        assertTrue(result.isSuccess)

        val game = fake.getGame(gameId)!!
        @Suppress("UNCHECKED_CAST")
        val shotsMap = game["shots"] as Map<String, List<Any>>
        assertTrue(shotsMap.containsKey("uid-host"))
    }

    // ── observeOpponentShots ──────────────────────────────────────────────────

    @Test
    fun `observeOpponentShots emits only opponent shots`() = runTest {
        var gameId = ""
        fake.createGame("Alice").test {
            gameId = (awaitItem() as GameCreationResult.Success).gameId
            cancelAndIgnoreRemainingEvents()
        }

        fake.observeOpponentShots(gameId).test {
            // Simulate opponent shot
            fake.simulateOpponentShot(gameId, Coord.fromRowCol(5, 5))
            val shot = awaitItem()
            assertFalse(shot.shooterUid == "uid-host", "Should not be my own shot")
            assertEquals(5, shot.row)
            assertEquals(5, shot.col)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── claimVictory ──────────────────────────────────────────────────────────

    @Test
    fun `claimVictory writes winner uid`() = runTest {
        var gameId = ""
        fake.createGame("Alice").test {
            gameId = (awaitItem() as GameCreationResult.Success).gameId
            cancelAndIgnoreRemainingEvents()
        }
        fake.advanceToStatus(gameId, "battle")

        val result = fake.claimVictory(gameId)
        assertTrue(result.isSuccess)

        val game = fake.getGame(gameId)!!
        assertEquals("uid-host", game["winner"])
        assertEquals("finished", game["status"])
    }
}