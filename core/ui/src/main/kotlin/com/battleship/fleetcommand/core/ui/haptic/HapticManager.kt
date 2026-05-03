// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/haptic/HapticManager.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/haptic/HapticManager.kt
package com.battleship.fleetcommand.core.ui.haptic

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HapticEvent {
    SHIP_PLACED_VALID, SHIP_PLACEMENT_ERROR, SHOT_FIRED,
    HIT, MISS, SHIP_SUNK, VICTORY, DEFEAT,
    HAND_OFF_GO, COUNTDOWN_TICK, ONLINE_OPPONENT_JOIN
}

@Singleton
class HapticManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var hapticEnabled = true

    fun setEnabled(enabled: Boolean) { hapticEnabled = enabled }

    fun perform(event: HapticEvent) {
        if (!hapticEnabled || vibrator?.hasVibrator() != true) return
        when (event) {
            HapticEvent.SHIP_PLACED_VALID  -> vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            HapticEvent.SHIP_PLACEMENT_ERROR -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1))
            HapticEvent.SHOT_FIRED         -> vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            HapticEvent.HIT                -> vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            HapticEvent.MISS               -> vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            HapticEvent.SHIP_SUNK          -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50, 200), -1))
            HapticEvent.VICTORY            -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50, 50, 300), -1))
            HapticEvent.DEFEAT             -> vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            HapticEvent.HAND_OFF_GO        -> vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            HapticEvent.COUNTDOWN_TICK     -> vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            HapticEvent.ONLINE_OPPONENT_JOIN -> vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
    }
}