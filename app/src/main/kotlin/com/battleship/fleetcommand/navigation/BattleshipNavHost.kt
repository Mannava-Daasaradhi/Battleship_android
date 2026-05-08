// FILE: app/src/main/kotlin/com/battleship/fleetcommand/navigation/BattleshipNavHost.kt
package com.battleship.fleetcommand.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.battleship.fleetcommand.feature.game.battle.BattleScreen
import com.battleship.fleetcommand.feature.game.gameover.GameOverScreen
import com.battleship.fleetcommand.feature.game.handoff.HandOffScreen
import com.battleship.fleetcommand.feature.game.online.OnlineBattleScreen
import com.battleship.fleetcommand.feature.lobby.OnlineLobbyScreen
import com.battleship.fleetcommand.feature.lobby.WaitingForOpponentScreen
import com.battleship.fleetcommand.feature.menu.DifficultyScreen
import com.battleship.fleetcommand.feature.menu.MainMenuScreen
import com.battleship.fleetcommand.feature.menu.ModeSelectScreen
import com.battleship.fleetcommand.feature.menu.PlayerNamesScreen
import com.battleship.fleetcommand.feature.settings.SettingsScreen
import com.battleship.fleetcommand.feature.setup.ShipPlacementScreen
import com.battleship.fleetcommand.feature.stats.StatisticsScreen

@Composable
fun BattleshipNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = MainMenuRoute,
        modifier         = modifier,
        enterTransition  = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 } },
        exitTransition   = { fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 4 } },
        popEnterTransition  = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 4 } },
        popExitTransition   = { fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it / 4 } },
    ) {

        composable<MainMenuRoute>(
            enterTransition = { fadeIn(tween(300)) },
            exitTransition  = { fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 4 } },
        ) {
            MainMenuScreen(navController = navController, viewModel = hiltViewModel())
        }

        composable<ModeSelectRoute> {
            ModeSelectScreen(navController = navController, viewModel = hiltViewModel())
        }

        composable<DifficultyRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DifficultyRoute>()
            DifficultyScreen(navController = navController, viewModel = hiltViewModel(), route = route)
        }

        composable<PlayerNamesRoute> {
            PlayerNamesScreen(navController = navController, viewModel = hiltViewModel())
        }

        composable<ShipPlacementRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ShipPlacementRoute>()
            ShipPlacementScreen(navController = navController, viewModel = hiltViewModel(), route = route)
        }

        composable<HandOffRoute>(
            enterTransition     = { fadeIn(tween(600)) },
            exitTransition      = { fadeOut(tween(600)) },
            popEnterTransition  = { fadeIn(tween(600)) },
            popExitTransition   = { fadeOut(tween(600)) },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<HandOffRoute>()
            HandOffScreen(navController = navController, viewModel = hiltViewModel(), route = route)
        }

        // ── AI and Pass-&-Play battles ─────────────────────────────────────────
        composable<BattleRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BattleRoute>()
            BattleScreen(navController = navController, viewModel = hiltViewModel(), route = route)
        }

        // ── Online (Firebase) battles — dedicated route + dedicated ViewModel ──
        // OnlineBattleRoute carries gameId AND myUid so OnlineGameViewModel needs
        // no async auth call to initialise. BattleRoute/BattleViewModel is NOT used
        // for online games — they have no Firebase awareness.
        composable<OnlineBattleRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<OnlineBattleRoute>()
            OnlineBattleScreen(
                navController = navController,
                viewModel     = hiltViewModel(),
                route         = route,
            )
        }

        composable<OnlineLobbyRoute> {
            OnlineLobbyScreen(navController = navController, viewModel = hiltViewModel())
        }

        composable<WaitingForOpponentRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<WaitingForOpponentRoute>()
            WaitingForOpponentScreen(
                navController = navController,
                viewModel     = hiltViewModel(),
                gameId        = route.gameId,
                roomCode      = route.roomCode,
            )
        }

        composable<GameOverRoute>(
            enterTransition = {
                scaleIn(
                    initialScale  = 0.8f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                ) + fadeIn(spring())
            },
            exitTransition = {
                fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 }
            },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<GameOverRoute>()
            GameOverScreen(navController = navController, viewModel = hiltViewModel(), route = route)
        }

        composable<StatisticsRoute> {
            StatisticsScreen(navController = navController, viewModel = hiltViewModel())
        }

        composable<SettingsRoute> {
            SettingsScreen(navController = navController, viewModel = hiltViewModel())
        }
    }
}