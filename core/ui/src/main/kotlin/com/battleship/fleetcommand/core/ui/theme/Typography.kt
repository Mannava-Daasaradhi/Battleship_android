// ============================================================
// REPLACES: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/theme/Typography.kt
// ============================================================
//
// Drop-in upgrade. Adds a real type system to the existing M3 dark theme.
// Uses Compose's Downloadable Fonts integration — no .ttf files to ship.
//
// Required dependency in core/ui/build.gradle.kts:
//   implementation("androidx.compose.ui:ui-text-google-fonts:1.7.6")
//
// Required res/values/font_certs.xml (see android/res/values/font_certs.xml in this design system).
//
package com.battleship.fleetcommand.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.battleship.fleetcommand.core.ui.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs,
)

private val displayFontName = GoogleFont("Big Shoulders Display")
private val bodyFontName    = GoogleFont("Inter")
private val monoFontName    = GoogleFont("JetBrains Mono")

val DisplayFamily = FontFamily(
    Font(googleFont = displayFontName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = displayFontName, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = displayFontName, fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = displayFontName, fontProvider = provider, weight = FontWeight.Black),
)

val BodyFamily = FontFamily(
    Font(googleFont = bodyFontName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = bodyFontName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = bodyFontName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = bodyFontName, fontProvider = provider, weight = FontWeight.Bold),
)

val MonoFamily = FontFamily(
    Font(googleFont = monoFontName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = monoFontName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = monoFontName, fontProvider = provider, weight = FontWeight.Bold),
)

// Tracking constants — match colors_and_type.css `--tracking-*`.
private val TrackingWide   = 0.12.em
private val TrackingXWide  = 0.20.em
private val TrackingXXWide = 0.28.em

val BattleshipTypography = Typography(
    displayLarge   = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Black,      fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = TrackingXXWide),
    displayMedium  = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold,  fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = TrackingXXWide),
    displaySmall   = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold,  fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = TrackingXXWide),

    headlineLarge  = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold,  fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = TrackingXXWide),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,       fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = TrackingXWide),
    headlineSmall  = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,       fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = TrackingXWide),

    titleLarge     = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,       fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = TrackingXWide),
    titleMedium    = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.SemiBold,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = TrackingWide),
    titleSmall     = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.SemiBold,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = TrackingWide),

    bodyLarge      = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.Normal,     fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.Normal,     fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.Normal,     fontSize = 12.sp, lineHeight = 16.sp),

    labelLarge     = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.SemiBold,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = TrackingWide),
    labelMedium    = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.SemiBold,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = TrackingWide),
    labelSmall     = TextStyle(fontFamily = BodyFamily,    fontWeight = FontWeight.SemiBold,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = TrackingWide),
)

// Convenience — for numerics & coordinates. Apply via .copy(fontFamily = MonoFamily).
// Example: Text("12", style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFamily))
