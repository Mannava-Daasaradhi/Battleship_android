# Battleship Fleet Command — R8 / ProGuard Rules
# Section 18 — Performance & App Size
# ──────────────────────────────────────────────────────────────────────────

# ── General ────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ── Coroutines ────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── Kotlinx Serialization ─────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.battleship.fleetcommand.** { *; }

# ── Hilt / Dagger ─────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-dontwarn dagger.hilt.**

# ── Room ──────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.**

# ── Firebase ──────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-keepclassmembers class com.battleship.fleetcommand.** { public <init>(); }

# ── Google Play Games v2 ──────────────────────────────────────────────────
-keep class com.google.android.gms.games.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# ── Lottie ────────────────────────────────────────────────────────────────
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# ── Coil ──────────────────────────────────────────────────────────────────
-dontwarn coil.**
-keep class coil.** { *; }

# ── Timber ────────────────────────────────────────────────────────────────
-keep class com.jakewharton.timber.** { *; }
-dontwarn org.jetbrains.annotations.**

# ── Domain models — only keep @Serializable classes for Firebase/serialization ──
-keep @kotlinx.serialization.Serializable class com.battleship.fleetcommand.core.domain.** { *; }
-keepclassmembers class com.battleship.fleetcommand.core.domain.engine.FireResult { *; }
-keepclassmembers class com.battleship.fleetcommand.core.domain.ship.ShipId { *; }
-keepclassmembers class com.battleship.fleetcommand.core.domain.model.GameMode { *; }
-keepclassmembers class com.battleship.fleetcommand.core.domain.player.Difficulty { *; }
-keepclassmembers class com.battleship.fleetcommand.core.domain.player.PlayerSlot { *; }

# ── Navigation routes ─────────────────────────────────────────────────────
-keep class com.battleship.fleetcommand.navigation.** { *; }

# ── Compose ───────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Navigation ────────────────────────────────────────────────────────────
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ── DataStore ─────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ── Lifecycle ─────────────────────────────────────────────────────────────
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ── Crashlytics — keep SourceFile for deobfuscated crash reports ──────────
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**
-keep public class * extends java.lang.Exception

# ── OkHttp / Okio (transitive from Firebase) ─────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Strip ALL Timber logs in release ────────────────────────────────────
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ── Enums ─────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelable ────────────────────────────────────────────────────────────
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ── Serializable ──────────────────────────────────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}