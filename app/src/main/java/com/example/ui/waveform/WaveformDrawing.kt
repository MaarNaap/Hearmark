package com.example.ui.waveform

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.player.WaveformPoint
import java.util.Locale
import kotlin.math.cos

internal data class TimeAxis(
    val pxPerSec: Float,
    val scrollPx: Double
) {
    fun timeToScreenX(timeMs: Long): Float {
        return ((timeMs / 1000.0) * pxPerSec - scrollPx).toFloat()
    }

    fun screenXToTimeMs(screenX: Float): Long {
        return (((scrollPx + screenX) / pxPerSec) * 1000.0).toLong()
    }
}

internal class WaveformPaints(
    val rulerTextPaint: Paint,
    val segmentBadgePaint: Paint,
    val cutNumPaint: Paint,
    val cutTimeBadgePaint: Paint
)

@Composable
internal fun rememberWaveformPaints(density: Density): WaveformPaints {
    val rulerTextPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.argb(175, 140, 145, 155)
            textSize = with(density) { 9.dp.toPx() }
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }

    val segmentBadgePaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.argb(220, 30, 40, 60)
            textSize = with(density) { 8.5.dp.toPx() }
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    val cutNumPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 8.dp.toPx() }
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    val cutTimeBadgePaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.argb(220, 90, 100, 120)
            textSize = with(density) { 8.dp.toPx() }
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    return remember(rulerTextPaint, segmentBadgePaint, cutNumPaint, cutTimeBadgePaint) {
        WaveformPaints(
            rulerTextPaint = rulerTextPaint,
            segmentBadgePaint = segmentBadgePaint,
            cutNumPaint = cutNumPaint,
            cutTimeBadgePaint = cutTimeBadgePaint
        )
    }
}

internal fun DrawScope.drawMiniMap(
    effectiveDuration: Long,
    cuts: List<Long>,
    positionMs: () -> Long,
    scrollOffsetPx: Double,
    totalVirtualWidthPx: Double,
    containerWidthPx: Float,
    primaryColor: Color,
    cutColor: Color,
    playheadColor: Color
) {
    val w = size.width
    val h = size.height
    if (effectiveDuration <= 0) return

    // Center track line
    drawLine(
        color = primaryColor.copy(alpha = 0.20f),
        start = Offset(0f, h / 2f),
        end = Offset(w, h / 2f),
        strokeWidth = 1.dp.toPx()
    )

    // Cuts on mini-map
    cuts.forEach { cutMs ->
        val cutX = (cutMs.toFloat() / effectiveDuration.toFloat()) * w
        drawLine(
            color = cutColor.copy(alpha = 0.85f),
            start = Offset(cutX, 2.dp.toPx()),
            end = Offset(cutX, h - 2.dp.toPx()),
            strokeWidth = 1.5.dp.toPx()
        )
    }

    // Playhead on mini-map
    val currentPos = positionMs()
    val playheadRatio = (currentPos.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    val playheadX = playheadRatio * w
    drawLine(
        color = playheadColor,
        start = Offset(playheadX, 0f),
        end = Offset(playheadX, h),
        strokeWidth = 2.dp.toPx()
    )

    // Visible viewport window box
    if (totalVirtualWidthPx > containerWidthPx && totalVirtualWidthPx > 0.0) {
        val windowStartRatio = (scrollOffsetPx / totalVirtualWidthPx).toFloat().coerceIn(0f, 1f)
        val windowEndRatio = ((scrollOffsetPx + containerWidthPx) / totalVirtualWidthPx).toFloat().coerceIn(0f, 1f)
        val boxLeft = windowStartRatio * w
        val boxRight = (windowEndRatio * w).coerceAtLeast(boxLeft + 6.dp.toPx())
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.22f),
            topLeft = Offset(boxLeft, 1.dp.toPx()),
            size = Size(boxRight - boxLeft, h - 2.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.85f),
            topLeft = Offset(boxLeft, 1.dp.toPx()),
            size = Size(boxRight - boxLeft, h - 2.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

internal fun DrawScope.drawTimeRuler(
    axis: TimeAxis,
    effectiveDuration: Long,
    canvasW: Float,
    canvasH: Float,
    rulerH: Float,
    gridColor: Color,
    rulerTextPaint: Paint
) {
    val minVisibleMs = ((axis.scrollPx / axis.pxPerSec) * 1000.0).toLong().coerceAtLeast(0L)
    val maxVisibleMs = (((axis.scrollPx + canvasW) / axis.pxPerSec) * 1000.0).toLong().coerceAtMost(effectiveDuration)

    val (majorStepMs, minorStepMs) = when {
        axis.pxPerSec >= 600f -> Pair(100L, 20L)    // 100ms major, 20ms minor ticks (5x Max Stretch)
        axis.pxPerSec >= 300f -> Pair(250L, 50L)    // 250ms major, 50ms minor ticks (2x Hyper)
        axis.pxPerSec >= 150f -> Pair(500L, 100L)   // 500ms major, 100ms minor ticks (1x Wide - Default)
        axis.pxPerSec >= 70f  -> Pair(1000L, 200L)  // 1s major, 200ms minor ticks (0.5x)
        axis.pxPerSec >= 30f  -> Pair(2000L, 500L)  // 2s major, 500ms minor ticks
        axis.pxPerSec >= 10f  -> Pair(5000L, 1000L) // 5s major, 1s minor ticks
        else                  -> Pair(10000L, 2000L)// 10s major, 2s minor ticks
    }

    val firstMinor = (minVisibleMs / minorStepMs) * minorStepMs
    var t = firstMinor
    while (t <= maxVisibleMs + majorStepMs) {
        val x = axis.timeToScreenX(t)
        if (x in -20f..(canvasW + 20f)) {
            val isMajor = (t % majorStepMs == 0L)
            if (isMajor) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, canvasH),
                    strokeWidth = 1.dp.toPx()
                )
                val minutes = t / 60000L
                val seconds = (t % 60000L) / 1000L
                val timeLabel = when {
                    majorStepMs < 500L -> {
                        val millis = t % 1000L
                        String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
                    }
                    majorStepMs < 1000L -> {
                        val millis = (t % 1000L) / 100L
                        "$minutes:${if (seconds < 10) "0$seconds" else "$seconds"}.$millis"
                    }
                    else -> {
                        "$minutes:${if (seconds < 10) "0$seconds" else "$seconds"}"
                    }
                }
                drawContext.canvas.nativeCanvas.drawText(
                    timeLabel,
                    x + 3.dp.toPx(),
                    rulerH - 5.dp.toPx(),
                    rulerTextPaint
                )
            } else {
                drawLine(
                    color = gridColor.copy(alpha = 0.5f),
                    start = Offset(x, rulerH - 5.dp.toPx()),
                    end = Offset(x, rulerH),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        t += minorStepMs
    }
}

internal fun DrawScope.drawSegmentBands(
    axis: TimeAxis,
    effectiveDuration: Long,
    cuts: List<Long>,
    canvasW: Float,
    waveTop: Float,
    waveH: Float,
    primaryColor: Color,
    segmentBadgePaint: Paint
) {
    var prevCutMs = 0L
    cuts.forEachIndexed { i, cutMs ->
        val startX = axis.timeToScreenX(prevCutMs)
        val endX = axis.timeToScreenX(cutMs)
        if (endX >= 0f && startX <= canvasW) {
            val bandColor = if (i % 2 == 0) {
                primaryColor.copy(alpha = 0.05f)
            } else {
                Color(0xFF4CAF50).copy(alpha = 0.06f)
            }
            val drawStart = startX.coerceAtLeast(0f)
            val drawEnd = endX.coerceAtMost(canvasW)
            if (drawEnd > drawStart) {
                drawRect(
                    color = bandColor,
                    topLeft = Offset(drawStart, waveTop),
                    size = Size(drawEnd - drawStart, waveH)
                )
            }

            val segWidthPx = endX - startX
            if (segWidthPx >= 48.dp.toPx() && (startX + 4.dp.toPx()) in 0f..canvasW) {
                val durationSec = (cutMs - prevCutMs) / 1000f
                val label = "S${i + 1} (${String.format(Locale.US, "%.1fs", durationSec)})"
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    startX.coerceAtLeast(4.dp.toPx()) + 4.dp.toPx(),
                    waveTop + 10.dp.toPx(),
                    segmentBadgePaint
                )
            }
        }
        prevCutMs = cutMs
    }
    if (prevCutMs < effectiveDuration) {
        val startX = axis.timeToScreenX(prevCutMs)
        val endX = axis.timeToScreenX(effectiveDuration)
        if (endX >= 0f && startX <= canvasW) {
            val drawStart = startX.coerceAtLeast(0f)
            val drawEnd = endX.coerceAtMost(canvasW)
            if (drawEnd > drawStart) {
                drawRect(
                    color = primaryColor.copy(alpha = 0.04f),
                    topLeft = Offset(drawStart, waveTop),
                    size = Size(drawEnd - drawStart, waveH)
                )
            }
            val segWidthPx = endX - startX
            if (segWidthPx >= 48.dp.toPx() && (startX + 4.dp.toPx()) in 0f..canvasW) {
                val durationSec = (effectiveDuration - prevCutMs) / 1000f
                val label = "S${cuts.size + 1} (${String.format(Locale.US, "%.1fs", durationSec)})"
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    startX.coerceAtLeast(4.dp.toPx()) + 4.dp.toPx(),
                    waveTop + 10.dp.toPx(),
                    segmentBadgePaint
                )
            }
        }
    }
}

internal fun DrawScope.drawWaveformBars(
    axis: TimeAxis,
    effectiveDuration: Long,
    activeData: List<WaveformPoint>,
    waveformGain: Float,
    positionMs: () -> Long,
    canvasW: Float,
    waveH: Float,
    centerY: Float,
    primaryColor: Color,
    unplayedBarColor: Color,
    silentBarColor: Color
) {
    // Subtle Center Baseline
    drawLine(
        color = primaryColor.copy(alpha = 0.18f),
        start = Offset(0f, centerY),
        end = Offset(canvasW, centerY),
        strokeWidth = 1.dp.toPx()
    )

    if (activeData.isEmpty()) return

    val barWidthPx = 3.2.dp.toPx()
    val barSpacingPx = 1.8.dp.toPx()
    val barPitchPx = barWidthPx + barSpacingPx

    val numScreenBars = (canvasW / barPitchPx).toInt() + 2
    val curPlayheadX = axis.timeToScreenX(positionMs())

    for (b in 0..numScreenBars) {
        val barX = b * barPitchPx + (barWidthPx / 2f)
        val barTimeMs = axis.screenXToTimeMs(barX)

        if (barTimeMs in 0L..effectiveDuration) {
            val sampleIdx = (barTimeMs / 50L).toInt()
            val rawAmp = if (sampleIdx in activeData.indices) {
                val nextIdx = (sampleIdx + 1).coerceAtMost(activeData.size - 1)
                val frac = ((barTimeMs % 50L) / 50f).coerceIn(0f, 1f)
                val smoothFrac = (1f - cos(frac * Math.PI.toFloat())) / 2f
                activeData[sampleIdx].amplitude * (1f - smoothFrac) + activeData[nextIdx].amplitude * smoothFrac
            } else {
                0.0f
            }
            val amp = (rawAmp * waveformGain).coerceIn(0f, 1f)

            // True dynamic loudness: silence is flat on baseline, speech height varies proportionally with volume
            val isSilence = (amp <= 0.035f)
            val maxHalfH = (waveH / 2f) - 1.5.dp.toPx()
            val barH = if (isSilence) {
                1.2.dp.toPx() // Flat subtle baseline indicator for silence
            } else {
                // Dynamic amplitude: quiet speech is short (15-35%), normal speech is medium (40-65%), loud bursts reach high (75-95%)
                (amp * maxHalfH).coerceIn(2.5.dp.toPx(), maxHalfH)
            }

            val isPlayed = (barX <= curPlayheadX)
            val barColor = when {
                isSilence -> silentBarColor
                isPlayed -> primaryColor
                else -> unplayedBarColor
            }

            drawLine(
                color = barColor,
                start = Offset(barX, centerY - barH),
                end = Offset(barX, centerY + barH),
                strokeWidth = barWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}

internal fun DrawScope.drawCutMarkers(
    axis: TimeAxis,
    cuts: List<Long>,
    selectedCutIndex: Int?,
    canvasW: Float,
    canvasH: Float,
    rulerH: Float,
    cutColor: Color,
    selectedCutColor: Color,
    cutNumPaint: Paint,
    cutTimeBadgePaint: Paint
) {
    cuts.forEachIndexed { i, cutMs ->
        val cutX = axis.timeToScreenX(cutMs)
        if (cutX in -30f..(canvasW + 30f)) {
            val isSelected = (selectedCutIndex == i)
            val flagColor = if (isSelected) selectedCutColor else cutColor

            // Vertical line
            drawLine(
                color = flagColor,
                start = Offset(cutX, rulerH),
                end = Offset(cutX, canvasH),
                strokeWidth = if (isSelected) 2.8.dp.toPx() else 1.8.dp.toPx(),
                pathEffect = if (isSelected) null else PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )

            // Pin handle at ruler
            val pinY = rulerH / 2f
            drawCircle(
                color = flagColor,
                radius = if (isSelected) 8.5.dp.toPx() else 6.dp.toPx(),
                center = Offset(cutX, pinY)
            )

            // Cut index number inside pin
            drawContext.canvas.nativeCanvas.drawText(
                "${i + 1}",
                cutX,
                pinY + 3.dp.toPx(),
                cutNumPaint
            )

            // Floating millisecond badge at bottom
            if (isSelected || axis.pxPerSec >= 150f) {
                val millis = cutMs % 1000L
                val seconds = (cutMs % 60000L) / 1000L
                val minutes = cutMs / 60000L
                val badgeText = String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
                drawContext.canvas.nativeCanvas.drawText(
                    badgeText,
                    cutX,
                    canvasH - 4.dp.toPx(),
                    cutTimeBadgePaint
                )
            }
        }
    }
}

internal fun DrawScope.drawPlayhead(
    axis: TimeAxis,
    positionMs: () -> Long,
    canvasW: Float,
    canvasH: Float,
    rulerH: Float,
    playheadColor: Color
) {
    val playheadX = axis.timeToScreenX(positionMs())
    if (playheadX in -20f..(canvasW + 20f)) {
        drawLine(
            color = playheadColor,
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, canvasH),
            strokeWidth = 2.2.dp.toPx()
        )
        drawCircle(
            color = playheadColor,
            radius = 6.5.dp.toPx(),
            center = Offset(playheadX, rulerH / 2f)
        )
    }
}
