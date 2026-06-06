// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/haptic/HapticManager.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/haptic/HapticManager.kt
package com.battleship.fleetcommand.core.ui.haptic

import android.content.Context
import android.os.Build
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
            HapticEvent.SHIP_PLACED_VALID  -> vibrator.vibrate(predefined(VibrationEffect.EFFECT_CLICK, fallbackMs = 20))
            HapticEvent.SHIP_PLACEMENT_ERROR -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1))
            HapticEvent.SHOT_FIRED         -> vibrator.vibrate(predefined(VibrationEffect.EFFECT_TICK, fallbackMs = 10))
            HapticEvent.HIT                -> vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            HapticEvent.MISS               -> vibrator.vibrate(predefined(VibrationEffect.EFFECT_TICK, fallbackMs = 10))
            HapticEvent.SHIP_SUNK          -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50, 200), -1))
            HapticEvent.VICTORY            -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50, 50, 300), -1))
            HapticEvent.DEFEAT             -> vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            HapticEvent.HAND_OFF_GO        -> vibrator.vibrate(predefined(VibrationEffect.EFFECT_HEAVY_CLICK, fallbackMs = 40))
            HapticEvent.COUNTDOWN_TICK     -> vibrator.vibrate(predefined(VibrationEffect.EFFECT_TICK, fallbackMs = 10))
            HapticEvent.ONLINE_OPPONENT_JOIN -> vibrator.vibrate(predefined(VibrationEffect.EFFECT_CLICK, fallbackMs = 20))
        }
    }

    /**
     * [VibrationEffect.createPredefined] requires API 29+, but minSdk is 26.
     * On API 26–28 fall back to a short one-shot so haptics still fire instead of crashing.
     */
    private fun predefined(effectId: Int, fallbackMs: Long): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(effectId)
        } else {
            VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
}