package com.battleship.fleetcommand.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun BattleshipNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = MainMenuRoute) {
        // Screens wired in Phase 3 — stubs keep the build green
        composable<MainMenuRoute>     { /* MainMenuScreen() */ }
        composable<ModeSelectRoute>   { /* ModeSelectScreen() */ }
        composable<DifficultyRoute>   { /* DifficultyScreen() */ }
        composable<PlayerNamesRoute>  { /* PlayerNamesScreen() */ }
        composable<ShipPlacementRoute>{ /* ShipPlacementScreen() */ }
        composable<BattleRoute>       { /* BattleScreen() */ }
        composable<HandOffRoute>      { /* HandOffScreen() */ }
        composable<GameOverRoute>     { /* GameOverScreen() */ }
        composable<OnlineLobbyRoute>  { /* OnlineLobbyScreen() */ }
        composable<StatisticsRoute>   { /* StatisticsScreen() */ }
        composable<SettingsRoute>     { /* SettingsScreen() */ }
    }
}