package com.example.player

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/**
 * Optional "voice boost": adds gain on top of the normal volume using Android's
 * LoudnessEnhancer, so quiet recordings can be played louder than setVolume(1.0f) allows.
 *
 * - Level 0 means off: no audio effect is created at all.
 * - Each level adds +3 dB (300 mB), up to +15 dB at MAX_LEVEL.
 * - The effect is tied to one MediaPlayer's audio session, so AudioPlayerManager's
 *   mediaPlayer setter calls [attach] every time the player changes (including null on release).
 * - Everything is best effort: if a device rejects the effect, playback continues unboosted.
 */
object VoiceBoostController {
    private const val TAG = "VoiceBoost"
    const val MAX_LEVEL = 5
    private const val GAIN_MB_PER_LEVEL = 300

    private var level = 0
    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null

    fun levelToGainMb(level: Int): Int = level.coerceIn(0, MAX_LEVEL) * GAIN_MB_PER_LEVEL

    fun levelToDb(level: Int): Int = levelToGainMb(level) / 100

    @Synchronized
    fun setLevel(newLevel: Int) {
        level = newLevel.coerceIn(0, MAX_LEVEL)
        applyToCurrentPlayer()
    }

    /** Called whenever the active MediaPlayer changes. Pass null when it is released. */
    @Synchronized
    fun attach(newPlayer: MediaPlayer?) {
        releaseEnhancer()
        player = newPlayer
        applyToCurrentPlayer()
    }

    private fun applyToCurrentPlayer() {
        val gainMb = levelToGainMb(level)
        val p = player
        if (p == null || gainMb == 0) {
            releaseEnhancer()
            return
        }
        try {
            var e = enhancer
            if (e == null) {
                val session = p.audioSessionId
                // 0 is the global output mix: never attach an effect there.
                if (session <= 0) return
                e = LoudnessEnhancer(session)
                e.setEnabled(true)
                enhancer = e
            }
            e.setTargetGain(gainMb)
        } catch (ex: Exception) {
            Log.w(TAG, "Voice boost unavailable: ${ex.message}")
            releaseEnhancer()
        }
    }

    private fun releaseEnhancer() {
        try {
            enhancer?.release()
        } catch (ex: Exception) {
            Log.w(TAG, "Voice boost release failed: ${ex.message}")
        }
        enhancer = null
    }
}
