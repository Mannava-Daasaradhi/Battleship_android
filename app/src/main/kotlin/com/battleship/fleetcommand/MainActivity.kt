package com.battleship.fleetcommand

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.battleship.fleetcommand.core.ui.theme.BattleshipTheme
import com.battleship.fleetcommand.navigation.BattleshipNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BattleshipTheme {
                BattleshipNavHost()
            }
        }
    }
}