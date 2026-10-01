package com.example.ui.waveform

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.player.AudioPlayerManager
import kotlin.math.abs

internal fun Modifier.miniMapGestures(
    effectiveDuration: Long,
    totalVirtualWidthPx: Double,
    pxPerSec: Float,
    containerWidthPx: Float,
    maxScrollPx: Double,
    onScrollOffsetChange: (Double) -> Unit
): Modifier = this.pointerInput(effectiveDuration, totalVirtualWidthPx, pxPerSec, containerWidthPx) {
    detectTapGestures { offset ->
        if (totalVirtualWidthPx > 0 && effectiveDuration > 0) {
            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
            val targetMs = (ratio * effectiveDuration).toLong()
            val targetScroll = ((targetMs / 1000.0) * pxPerSec - (containerWidthPx / 2.0)).coerceIn(0.0, maxScrollPx)
            onScrollOffsetChange(targetScroll)
        }
    }
}

internal fun Modifier.waveformGestures(
    pxPerSec: Float,
    cuts: SnapshotStateList<Long>,
    effectiveDuration: Long,
    maxScrollPx: Double,
    getScrollOffsetPx: () -> Double,
    onScrollOffsetChange: (Double) -> Unit,
    onSelectCut: (Int?) -> Unit
): Modifier = this.pointerInput(pxPerSec, cuts.size, effectiveDuration) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val hitThresholdPx = 32.dp.toPx()
        val currentScrollOffset = getScrollOffsetPx()
        val hitCutIndex = cuts.indexOfFirst { cutMs ->
            val cutScreenX = ((cutMs / 1000.0) * pxPerSec - currentScrollOffset).toFloat()
            abs(cutScreenX - down.position.x) <= hitThresholdPx
        }

        if (hitCutIndex >= 0) {
            // Direct Cut Handle Dragging
            onSelectCut(hitCutIndex)
            down.consume()
            val pointerId = down.id
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (change.isConsumed || !change.pressed) break
                change.consume()
                val touchX = change.position.x
                val scrollPx = getScrollOffsetPx()
                val newMs = (((scrollPx + touchX) / pxPerSec) * 1000.0).toLong().coerceIn(0L, effectiveDuration)
                val prev = if (hitCutIndex > 0) cuts[hitCutIndex - 1] + 100L else 100L
                val next = if (hitCutIndex < cuts.lastIndex) cuts[hitCutIndex + 1] - 100L else effectiveDuration - 100L
                cuts[hitCutIndex] = newMs.coerceIn(prev, next)
            }
        } else {
            // Waveform Pan / Tap-to-seek
            var totalDragX = 0f
            val pointerId = down.id
            var isDrag = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                    if (!isDrag) {
                        // Tap to seek playback
                        onSelectCut(null)
                        val scrollPx = getScrollOffsetPx()
                        val clickedMs = (((scrollPx + down.position.x) / pxPerSec) * 1000.0).toLong().coerceIn(0L, effectiveDuration)
                        AudioPlayerManager.seekTo(clickedMs, isPhysicalTimestamp = false)
                    }
                    break
                }
                val deltaX = change.position.x - change.previousPosition.x
                totalDragX += deltaX
                if (!isDrag && abs(totalDragX) > 6f) {
                    isDrag = true
                }
                if (isDrag) {
                    change.consume()
                    val scrollPx = getScrollOffsetPx()
                    onScrollOffsetChange((scrollPx - deltaX).coerceIn(0.0, maxScrollPx))
                }
            }
        }
    }
}
