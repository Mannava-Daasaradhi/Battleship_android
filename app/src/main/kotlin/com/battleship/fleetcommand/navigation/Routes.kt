package com.battleship.fleetcommand.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation routes — Section 12
@Serializable object MainMenuRoute
@Serializable object ModeSelectRoute
@Serializable data class DifficultyRoute(val mode: String)
@Serializable data class PlayerNamesRoute(val mode: String)
@Serializable data class ShipPlacementRoute(val mode: String, val playerSlot: Int = 0)
@Serializable data class BattleRoute(val gameId: String)
@Serializable object HandOffRoute
@Serializable object GameOverRoute
@Serializable object OnlineLobbyRoute
@Serializable data class WaitingForOpponentRoute(val gameId: String)
@Serializable object StatisticsRoute
@Serializable object SettingsRoute