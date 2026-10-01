package com.example.ui.waveform

import com.example.player.WaveformPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

internal data class WaveformZoomSetting(
    val id: String,
    val dpPerSec: Float, // dp width per 1 second of audio; -1f for Fit whole file
    val labelKey: String,
    val displayLabel: String
)

internal fun defaultWaveformZoomLevels(): List<WaveformZoomSetting> = listOf(
    WaveformZoomSetting("fit", -1f, "zoom_fit", "Fit"),
    WaveformZoomSetting("0.5x", 100f, "zoom_0_5x", "0.5x"),
    WaveformZoomSetting("1x", 220f, "zoom_1x", "1x"),
    WaveformZoomSetting("2x", 450f, "zoom_2x", "2x"),
    WaveformZoomSetting("3x", 800f, "zoom_3x", "3x"),
    WaveformZoomSetting("4x", 1100f, "zoom_4x", "4x"),
    WaveformZoomSetting("5x", 1400f, "zoom_5x", "5x")
)

/**
 * Immediate dynamic envelope fallback so the waveform canvas is NEVER blank while loading or decoding.
 */
internal fun buildSyntheticSpeechEnvelope(effectiveDuration: Long): List<WaveformPoint> {
    val dur = if (effectiveDuration > 0) effectiveDuration else 30000L
    val pts = mutableListOf<WaveformPoint>()
    var t = 0L
    var speechCounter = 0
    var isSpeechPhase = true
    while (t < dur) {
        speechCounter++
        if (isSpeechPhase && speechCounter > 65) {
            isSpeechPhase = false
            speechCounter = 0
        } else if (!isSpeechPhase && speechCounter > 18) {
            isSpeechPhase = true
            speechCounter = 0
        }
        val amp = if (isSpeechPhase) {
            val wordEnvelope = (0.35f + 0.65f * abs(sin(t.toDouble() / 320.0).toFloat()))
            val syllableEnvelope = (0.20f + 0.80f * abs(sin(t.toDouble() / 90.0).toFloat()))
            (wordEnvelope * syllableEnvelope).coerceIn(0.05f, 0.95f)
        } else {
            0.0f
        }
        pts.add(WaveformPoint(t, amp))
        t += 50L
    }
    return pts
}

/**
 * Dynamic gain safeguard so very quiet recordings are audible while preserving loudness range.
 */
internal fun computeWaveformGain(waveformData: List<WaveformPoint>): Float {
    val observedMaxAmp = waveformData.maxOfOrNull { it.amplitude } ?: 1.0f
    return if (observedMaxAmp < 0.25f && observedMaxAmp > 0.01f) {
        (0.85f / observedMaxAmp).coerceAtMost(3.0f)
    } else {
        1.0f
    }
}

/**
 * Local millisecond timestamp formatter for the waveform segment editor (MM:SS.d).
 */
internal fun formatWaveformTimestamp(ms: Long): String {
    val totalSec = ms / 1000L
    val m = totalSec / 60L
    val s = totalSec % 60L
    val millis = (ms % 1000L) / 100L // 1 decimal place for tenths
    return String.format(Locale.US, "%02d:%02d.%d", m, s, millis)
}
