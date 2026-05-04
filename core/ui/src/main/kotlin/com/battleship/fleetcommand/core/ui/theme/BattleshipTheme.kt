package com.battleship.fleetcommand.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NavyDarkColorScheme = darkColorScheme(
    primary            = NavyPrimary,
    onPrimary          = NavyOnPrimary,
    primaryContainer   = NavySurfaceVariant,
    onPrimaryContainer = NavyOnSurface,
    secondary          = NavySecondary,
    onSecondary        = NavyOnSecondary,
    tertiary           = GoldAccent,        // Gold for highlights, badges, CTAs
    onTertiary         = GoldOnAccent,
    background         = NavyBackground,
    onBackground       = NavyOnBackground,
    surface            = NavySurface,
    onSurface          = NavyOnSurface,
    surfaceVariant     = NavySurfaceVariant,
    onSurfaceVariant   = NavyOnSurface,
    error              = NavyError,
    outline            = GridLine,
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