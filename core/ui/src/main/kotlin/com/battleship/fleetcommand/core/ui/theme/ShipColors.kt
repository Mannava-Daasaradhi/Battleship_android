package com.battleship.fleetcommand.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.battleship.fleetcommand.core.domain.ship.ShipId

/**
 * Per-ship color identity — each ship type has a unique hull colour and accent.
 * Hull is the main fill on the grid; accent is used for stripes, tray highlights,
 * and the selected-ship border.
 */
data class ShipColorPalette(
    val hull: Color,
    val accent: Color,
    val deck: Color,    // slightly darker hull variant for depth
)

object ShipColors {

    private val palettes = mapOf(
        ShipId.CARRIER to ShipColorPalette(
            hull   = Color(0xFF1B5E8C),
            accent = Color(0xFFD4AF37),  // gold — flagship prestige
            deck   = Color(0xFF1A4F7A),
        ),
        ShipId.BATTLESHIP to ShipColorPalette(
            hull   = Color(0xFF4A2A6B),
            accent = Color(0xFFB388FF),  // lavender
            deck   = Color(0xFF3D1F5C),
        ),
        ShipId.CRUISER to ShipColorPalette(
            hull   = Color(0xFF1B5B3A),
            accent = Color(0xFF69F0AE),  // mint
            deck   = Color(0xFF14472D),
        ),
        ShipId.SUBMARINE to ShipColorPalette(
            hull   = Color(0xFF5C3D2E),
            accent = Color(0xFFFFAB40),  // amber
            deck   = Color(0xFF4A3025),
        ),
        ShipId.DESTROYER to ShipColorPalette(
            hull   = Color(0xFF8C1B1B),
            accent = Color(0xFFFF5252),  // red
            deck   = Color(0xFF701616),
        ),
    )

    /** Returns the colour palette for a given ship. Never null for valid ShipIds. */
    fun forShip(shipId: ShipId): ShipColorPalette = palettes.getValue(shipId)

    /** Hull colour only — convenience for GameCell. Falls back to NavyPrimary. */
    fun hullColor(shipId: ShipId?): Color =
        if (shipId != null) palettes[shipId]?.hull ?: NavyPrimary else NavyPrimary

    /** Accent colour only — convenience for tray highlights. */
    fun accentColor(shipId: ShipId?): Color =
        if (shipId != null) palettes[shipId]?.accent ?: NavyPrimary else NavyPrimary
}
