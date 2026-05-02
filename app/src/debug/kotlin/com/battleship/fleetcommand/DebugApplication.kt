package com.battleship.fleetcommand

import leakcanary.LeakCanary
import timber.log.Timber

// Debug-only Application subclass — wires LeakCanary + Timber
// Declared in AndroidManifest debug sourceSet via tools:replace
class DebugApplication : BattleshipApplication() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        LeakCanary.config = LeakCanary.config.copy(dumpHeap = true)
    }
}