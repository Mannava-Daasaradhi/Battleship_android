// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/sound/SoundManager.kt
// Section 10 — Sound Architecture
package com.battleship.fleetcommand.core.ui.sound

import android.animation.ObjectAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.battleship.fleetcommand.core.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** All sound events that can be played in Battleship Fleet Command. */
enum class GameSound {
    FIRE_CANNON,
    MISS_SPLASH,
    HIT_EXPLOSION,
    SHIP_SUNK,
    VICTORY,
    DEFEAT,
    UI_CLICK,
    SHIP_PLACE,
    COUNTDOWN_TICK,
}

/**
 * SoundPool-based SFX manager + MediaPlayer background music.
 * Section 10 — Sound Architecture.
 *
 * Call [initialize] once from Application.onCreate (or via Hilt EntryPoint)
 * passing the application-scoped coroutine scope.
 * Call [release] when the process is exiting (App.onTerminate).
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
) {
    private lateinit var soundPool: SoundPool
    private val soundIds = mutableMapOf<GameSound, Int>()

    private var musicPlayer: MediaPlayer? = null
    private var isMusicEnabled = true
    private var isSoundEnabled = true

    /** Load sounds on a background IO thread and observe preference changes. */
    fun initialize(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                isSoundEnabled = preferencesRepository.observeSoundEnabled().first()
                isMusicEnabled = preferencesRepository.observeMusicEnabled().first()
            } catch (e: Exception) {
                Timber.w(e, "SoundManager: could not read preferences; using defaults")
            }

            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()

            loadSoundResources()

            if (isMusicEnabled) {
                scope.launch(Dispatchers.Main) { startMusic() }
            }
        }

        // Observe preference changes at runtime
        scope.launch {
            preferencesRepository.observeSoundEnabled().collect { enabled ->
                isSoundEnabled = enabled
            }
        }
        scope.launch {
            preferencesRepository.observeMusicEnabled().collect { enabled ->
                setMusicEnabled(enabled)
            }
        }
    }

    /**
     * Load each SFX from res/raw/. Filenames follow Section 24 naming:
     * sfx_cannon_fire.ogg, sfx_water_splash.ogg, etc.
     *
     * ⚠️ Add the actual .ogg assets to app/src/main/res/raw/ before building.
     *    Sources: freesound.org (CC0 licence required) — see Section 10 table.
     */
    private fun loadSoundResources() {
        val resources = context.resources
        val pkg = context.packageName

        fun rawId(name: String): Int = resources.getIdentifier(name, "raw", pkg)

        fun loadIfExists(sound: GameSound, resName: String) {
            val id = rawId(resName)
            if (id != 0) {
                soundIds[sound] = soundPool.load(context, id, 1)
            } else {
                Timber.w("SoundManager: missing raw resource '$resName' — skipping")
            }
        }

        loadIfExists(GameSound.FIRE_CANNON,    "sfx_cannon_fire")
        loadIfExists(GameSound.MISS_SPLASH,    "sfx_water_splash")
        loadIfExists(GameSound.HIT_EXPLOSION,  "sfx_explosion")
        loadIfExists(GameSound.SHIP_SUNK,      "sfx_ship_sunk")
        loadIfExists(GameSound.VICTORY,        "sfx_victory")
        loadIfExists(GameSound.DEFEAT,         "sfx_defeat")
        loadIfExists(GameSound.UI_CLICK,       "sfx_ui_click")
        loadIfExists(GameSound.SHIP_PLACE,     "sfx_ship_place")
        loadIfExists(GameSound.COUNTDOWN_TICK, "sfx_tick")
    }

    /** Play a SFX. No-op if sounds are disabled or the asset is missing. */
    fun play(sound: GameSound, volume: Float = 1f) {
        if (!isSoundEnabled) return
        val id = soundIds[sound] ?: return
        soundPool.play(id, volume, volume, 1, 0, 1.0f)
    }

    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
    }

    fun setMusicEnabled(enabled: Boolean) {
        isMusicEnabled = enabled
        if (enabled) startMusic() else stopMusic()
    }

    private fun startMusic() {
        if (musicPlayer != null) return
        val resId = context.resources.getIdentifier("bgm_naval_theme", "raw", context.packageName)
        if (resId == 0) {
            Timber.w("SoundManager: missing raw resource 'bgm_naval_theme' — no background music")
            return
        }
        try {
            musicPlayer = MediaPlayer.create(context, resId)?.apply {
                isLooping = true
                setVolume(0f, 0f)
                start()
            }
            // Fade in
            musicPlayer?.let {
                ObjectAnimator.ofFloat(it, "volumeFloat", 0f, 0.4f).setDuration(1000).start()
            }
        } catch (e: Exception) {
            Timber.w(e, "SoundManager: failed to start background music")
        }
    }

    private fun stopMusic() {
        val mp = musicPlayer ?: return
        ObjectAnimator.ofFloat(mp, "volumeFloat", 0.4f, 0f).apply {
            duration = 1000
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    mp.pause()
                }
            })
            start()
        }
    }

    /** Must be called on app exit to release native resources. */
    fun release() {
        if (::soundPool.isInitialized) soundPool.release()
        musicPlayer?.release()
        musicPlayer = null
    }
}