package com.example.ui

import java.util.Locale

// --- UTILITY FORMATS FOR PLAYBACK SPEED & DURATION ---
fun formatPlaybackSpeed(speed: Float): String {
    val rounded = (Math.round(speed * 100f) / 100f)
    return when {
        Math.abs(rounded - rounded.toInt()) < 0.001f -> "${rounded.toInt()}x"
        Math.abs(rounded * 10f - (rounded * 10f).toInt()) < 0.001f -> String.format(Locale.US, "%.1fx", rounded)
        else -> String.format(Locale.US, "%.2fx", rounded)
    }
}

// --- UTILITY FORMATS FOR MILLISECONDS DURATION ---
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
