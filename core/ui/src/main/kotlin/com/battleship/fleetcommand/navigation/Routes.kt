package com.battleship.fleetcommand.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation routes — Section 12
@Serializable object MainMenuRoute
@Serializable object ModeSelectRoute
@Serializable data class DifficultyRoute(val mode: String)
@Serializable data class PlayerNamesRoute(val mode: String)
@Serializable data class ShipPlacementRoute(
    val mode: String,
    val playerSlot: Int = 0,
    val gameId: String = "",
    val player1Name: String = "Player 1",
    val player2Name: String = "Player 2",
)
@Serializable data class BattleRoute(val gameId: String)

/**
 * Dedicated route for online (Firebase) battles.
 * Carries the Firebase gameId and the local player's UID so OnlineGameViewModel
 * can initialise without an async auth call.
 */
@Serializable data class OnlineBattleRoute(
    val gameId: String,
    val myUid: String,
)

@Serializable data class HandOffRoute(
    val gameId: String = "",
    val mode: String = "",
    val isP1HandOff: Boolean = false,
    val phase: String = "SETUP",
)
@Serializable data class GameOverRoute(val gameId: String, val winner: String)
@Serializable object OnlineLobbyRoute
@Serializable data class WaitingForOpponentRoute(
    val gameId: String,
    val roomCode: String = "",   // added — host shares this with opponent
)
@Serializable object StatisticsRoute
@Serializable object SettingsRoute