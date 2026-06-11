// FILE: app/src/main/kotlin/com/battleship/fleetcommand/BattleshipApplication.kt
package com.battleship.fleetcommand

import android.app.Application
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
open class BattleshipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // net.zetetic:sqlcipher-android does not auto-load its native library.
        // Must be called before any Room/SQLCipher database access.
        System.loadLibrary("sqlcipher")

        // ── Firebase App Check ────────────────────────────────────────────
        // Attests that requests to RTDB / Cloud Functions come from the
        // genuine, unmodified app. Without it, anyone holding the API key
        // (extractable from any APK) can script the backend directly.
        //
        // Setup required in Firebase Console → App Check:
        //   1. Register the Android app with the Play Integrity provider
        //      (needs the app's SHA-256 signing certificate fingerprint).
        //   2. For debug builds: copy the debug token printed in Logcat
        //      ("DebugAppCheckProvider") and add it under
        //      App Check → Apps → Manage debug tokens.
        //   3. Leave enforcement OFF until metrics show verified traffic,
        //      then enable enforcement for Realtime Database and flip
        //      ENFORCE_APP_CHECK = true in functions/index.js.
        FirebaseApp.initializeApp(this)
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val provider = if (isDebuggable) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(provider)
    }
}
