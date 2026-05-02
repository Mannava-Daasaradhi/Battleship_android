package com.battleship.fleetcommand.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NavyDarkColorScheme = darkColorScheme(
    primary          = NavyPrimary,
    onPrimary        = NavyOnPrimary,
    secondary        = NavySecondary,
    onSecondary      = NavyOnSecondary,
    background       = NavyBackground,
    onBackground     = NavyOnBackground,
    surface          = NavySurface,
    onSurface        = NavyOnSurface,
    error            = NavyError,
)

@Composable
fun BattleshipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NavyDarkColorScheme,
        typography  = BattleshipTypography,
        shapes      = BattleshipShapes,
        content     = content,
    )
}