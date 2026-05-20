// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/theme/Typography.kt
package com.battleship.fleetcommand.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Tracking constants — calibrated for system SansSerif (Roboto/Noto).
// The previous values (0.12–0.28em) were designed for a custom military stencil
// font. On Roboto they look extremely stretched. These values give a clean,
// slightly wide military feel without looking broken.
private val TrackingWide   = 0.04.em   // was 0.12 — labels, body
private val TrackingXWide  = 0.06.em   // was 0.20 — titles, headlines
private val TrackingXXWide = 0.08.em   // was 0.28 — display / hero text

// Using system default font families — no network dependency, no Play Services cert required.
// DisplayFamily: SansSerif Bold for headings (military stencil feel)
// BodyFamily: SansSerif for body text (clean, readable)
// MonoFamily: Monospace for coordinates and numeric readouts
val DisplayFamily: FontFamily = FontFamily.SansSerif
val BodyFamily: FontFamily    = FontFamily.SansSerif
val MonoFamily: FontFamily    = FontFamily.Monospace

val BattleshipTypography = Typography(
    displayLarge   = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Black,      fontSize = 57.sp, lineHeight = 64.sp,  letterSpacing = TrackingXXWide),
    displayMedium  = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold,  fontSize = 45.sp, lineHeight = 52.sp,  letterSpacing = TrackingXXWide),
    displaySmall   = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold,  fontSize = 36.sp, lineHeight = 44.sp,  letterSpacing = TrackingXXWide),

    headlineLarge  = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold,  fontSize = 32.sp, lineHeight = 40.sp,  letterSpacing = TrackingXXWide),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,       fontSize = 28.sp, lineHeight = 36.sp,  letterSpacing = TrackingXWide),
    headlineSmall  = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,       fontSize = 24.sp, lineHeight = 32.sp,  letterSpacing = TrackingXWide),

    titleLarge     = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,       fontSize = 22.sp, lineHeight = 28.sp,  letterSpacing = TrackingXWide),
    titleMedium    = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.SemiBold,   fontSize = 16.sp, lineHeight = 24.sp,  letterSpacing = TrackingWide),
    titleSmall     = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.SemiBold,   fontSize = 14.sp, lineHeight = 20.sp,  letterSpacing = TrackingWide),

    bodyLarge      = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.Normal,     fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.Normal,     fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.Normal,     fontSize = 12.sp, lineHeight = 16.sp),

    labelLarge     = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.SemiBold,   fontSize = 14.sp, lineHeight = 20.sp,  letterSpacing = TrackingWide),
    labelMedium    = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.SemiBold,   fontSize = 12.sp, lineHeight = 16.sp,  letterSpacing = TrackingWide),
    labelSmall     = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.SemiBold,   fontSize = 11.sp, lineHeight = 16.sp,  letterSpacing = TrackingWide),
)

// Convenience — for numerics & coordinates.
// Example: Text("B4", style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFamily))