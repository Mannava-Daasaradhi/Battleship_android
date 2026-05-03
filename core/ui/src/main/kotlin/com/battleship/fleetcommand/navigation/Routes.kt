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
)
@Serializable data class BattleRoute(val gameId: String)
@Serializable object HandOffRoute
@Serializable data class GameOverRoute(val gameId: String, val winner: String)
@Serializable object OnlineLobbyRoute
@Serializable data class WaitingForOpponentRoute(val gameId: String)
@Serializable object StatisticsRoute
@Serializable object SettingsRoute