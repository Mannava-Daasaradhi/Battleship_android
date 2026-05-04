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
    // FIX: player names passed forward so PlacementViewModel can store them correctly
    val player1Name: String = "Player 1",
    val player2Name: String = "Player 2",
)
@Serializable data class BattleRoute(val gameId: String)
@Serializable data class HandOffRoute(
    val gameId: String = "",
    val mode: String = "",
    val isP1HandOff: Boolean = false,
    // FIX: "SETUP" = initial placement handoff, "BATTLE" = mid-game turn handoff.
    // Without this distinction, P2's post-placement handoff incorrectly tries to
    // popBackStack() to BattleScreen which was never pushed onto the stack yet.
    val phase: String = "SETUP",
)
@Serializable data class GameOverRoute(val gameId: String, val winner: String)
@Serializable object OnlineLobbyRoute
@Serializable data class WaitingForOpponentRoute(val gameId: String)
@Serializable object StatisticsRoute
@Serializable object SettingsRoute