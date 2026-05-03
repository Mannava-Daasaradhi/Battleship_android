// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/adaptive/AdaptiveLayoutHelpers.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/adaptive/AdaptiveLayoutHelpers.kt
package com.battleship.fleetcommand.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Returns true when the screen width is less than 360dp (compact — needs zoom). Section 16. */
@Composable
fun isCompactScreen(): Boolean = LocalConfiguration.current.screenWidthDp < 360