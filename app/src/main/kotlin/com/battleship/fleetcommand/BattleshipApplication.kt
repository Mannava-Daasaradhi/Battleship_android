// FILE: app/src/main/kotlin/com/battleship/fleetcommand/BattleshipApplication.kt
package com.battleship.fleetcommand

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
open class BattleshipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // net.zetetic:sqlcipher-android does not auto-load its native library.
        // Must be called before any Room/SQLCipher database access.
        System.loadLibrary("sqlcipher")
    }
}