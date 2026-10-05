package com.example.ui

import java.util.Locale

fun formatTimestampMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val formatted = if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
    return formatted.toWesternDigits()
}

fun parseTimestampToMs(input: String): Long? {
    val rawParts = input.toWesternDigits().trim().split(":")
    val parts = rawParts.map { it.trim().toLongOrNull() }
    if (parts.any { it == null || it < 0L }) return null
    val nonNullParts = parts.filterNotNull()
    return when (nonNullParts.size) {
        1 -> nonNullParts[0] * 1000L
        2 -> (nonNullParts[0] * 60L + nonNullParts[1]) * 1000L
        3 -> (nonNullParts[0] * 3600L + nonNullParts[1] * 60L + nonNullParts[2]) * 1000L
        else -> null
    }
}
