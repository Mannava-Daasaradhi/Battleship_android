// FILE: app/src/debug/kotlin/com/battleship/fleetcommand/DebugApplication.kt
package com.battleship.fleetcommand

import timber.log.Timber

// Debug-only Application subclass — plants Timber DebugTree for logcat output.
// Declared in AndroidManifest debug sourceSet via tools:replace.
// LeakCanary removed — re-add if actively debugging memory leaks locally.
class DebugApplication : BattleshipApplication() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}