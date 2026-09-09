package com.example.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import com.example.player.AudioPlayerManager
import com.example.player.SilenceDetector
import com.example.player.WaveformPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WaveformSegmentEditorDialog(
    track: AudioTrack,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val isPlaying by AudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by AudioPlayerManager.currentPosition.collectAsStateWithLifecycle()
    val durationState by AudioPlayerManager.duration.collectAsStateWithLifecycle()

    val effectiveDuration = remember(track.duration, durationState) {
        if (durationState > 0) durationState
        else if (track.duration > 0) track.duration
        else 60000L
    }

    // Cuts state (sorted milliseconds)
    val cuts = remember {
        mutableStateListOf<Long>().apply {
            val initial = track.getPracticeSegmentsList()
            if (initial.isNotEmpty()) {
                addAll(initial)
            } else {
                val current = AudioPlayerManager.currentPracticeSegments.value
                if (current.isNotEmpty()) addAll(current)
            }
        }
    }

    var selectedCutIndex by remember { mutableStateOf<Int?>(null) }
    var followPlayhead by remember { mutableStateOf(true) }

    // Zoom level states: 1.0x (Fit entire audio on screen), 2.0x, 4.0x, 8.0x, 16.0x, 32.0x, 64.0x
    val zoomLevels = remember { listOf(1.0f, 2.0f, 4.0f, 8.0f, 16.0f, 32.0f, 64.0f) }
    var zoomIndex by remember { mutableIntStateOf(0) } // Default 0: 1x (Fit entire waveform)
    val currentZoom = zoomLevels[zoomIndex]
    var pendingFocusRatio by remember { mutableStateOf<Float?>(null) }

    // Waveform data
    var waveformData by remember { mutableStateOf<List<WaveformPoint>>(emptyList()) }
    var isLoadingWaveform by remember { mutableStateOf(true) }

    LaunchedEffect(track.filePath) {
        isLoadingWaveform = true
        val cached = SilenceDetector.getCachedWaveform(track.filePath)
        // If cached data exists and has rich amplitude (> 0.30f peak), use it; otherwise re-extract
        if (cached != null && cached.isNotEmpty() && cached.any { it.amplitude >= 0.30f }) {
            waveformData = cached
            isLoadingWaveform = false
        } else {
            withContext(Dispatchers.IO) {
                SilenceDetector.clearWaveformCacheFor(track.filePath)
                val extracted = SilenceDetector.extractWaveform(context, track.filePath, effectiveDuration)
                waveformData = extracted
            }
            isLoadingWaveform = false
        }
    }

    // Scroll state for zoomed waveform
    val horizontalScrollState = rememberScrollState()

    fun changeZoom(newIndex: Int) {
        val bounded = newIndex.coerceIn(0, zoomLevels.lastIndex)
        if (bounded == zoomIndex) return
        val focusTimeMs = selectedCutIndex?.let { if (it in cuts.indices) cuts[it] else null } ?: currentPosition
        val focusRatio = if (effectiveDuration > 0) {
            (focusTimeMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
        } else 0f
        zoomIndex = bounded
        pendingFocusRatio = focusRatio
    }

    // Auto-scroll to playhead when enabled and playing
    LaunchedEffect(currentPosition, isPlaying, followPlayhead, currentZoom) {
        if (followPlayhead && effectiveDuration > 0 && isPlaying) {
            try {
                val maxScroll = horizontalScrollState.maxValue
                if (maxScroll > 0) {
                    val approxVisiblePx = with(density) { 360.dp.toPx() }
                    val playheadRatio = (currentPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
                    val targetScrollX = ((playheadRatio * (maxScroll + approxVisiblePx)) - (approxVisiblePx / 2f)).toInt().coerceIn(0, maxScroll)
                    if (kotlin.math.abs(horizontalScrollState.value - targetScrollX) > 24) {
                        horizontalScrollState.animateScrollTo(targetScrollX)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Single segment preview playback job
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun previewSegment(cutIdx: Int) {
        if (cutIdx !in cuts.indices) return
        val endMs = cuts[cutIdx]
        val startMs = if (cutIdx > 0) cuts[cutIdx - 1] else 0L

        previewJob?.cancel()
        AudioPlayerManager.seekTo(startMs, isPhysicalTimestamp = false)
        AudioPlayerManager.resume()

        previewJob = coroutineScope.launch {
            while (true) {
                delay(30)
                if (AudioPlayerManager.currentPosition.value >= endMs) {
                    AudioPlayerManager.pause()
                    break
                }
            }
        }
    }

    fun addCut(atMs: Long) {
        val bounded = atMs.coerceIn(200L, effectiveDuration - 200L)
        // Avoid duplicate within 150ms
        if (cuts.none { kotlin.math.abs(it - bounded) < 150L }) {
            cuts.add(bounded)
            cuts.sort()
            selectedCutIndex = cuts.indexOf(bounded)
        }
    }

    fun removeCut(index: Int) {
        if (index in cuts.indices) {
            cuts.removeAt(index)
            selectedCutIndex = if (cuts.isEmpty()) null else index.coerceAtMost(cuts.lastIndex)
        }
    }

    fun nudgeSelectedCut(deltaMs: Long) {
        val idx = selectedCutIndex ?: return
        if (idx !in cuts.indices) return
        val current = cuts[idx]
        val prevCut = if (idx > 0) cuts[idx - 1] + 150L else 200L
        val nextCut = if (idx < cuts.lastIndex) cuts[idx + 1] - 150L else effectiveDuration - 200L
        val updated = (current + deltaMs).coerceIn(prevCut, nextCut)
        cuts[idx] = updated
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.96f)
                .imePadding()
                .testTag("dialog_waveform_segment_editor"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = Loc.getText("manual_segments_title"),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = track.fileName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("btn_waveform_cancel")
                        ) {
                            Text(Loc.getText("cancel"), fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.saveManualPracticeSegments(track, cuts.toList(), context, autoEnable = true)
                                viewModel.updatePracticeSettings("MANUAL", viewModel.practicePauseMultiplierSetting)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("btn_waveform_save_apply"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Loc.getText("save_and_apply"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toolbar: Zoom controls & Playhead Following
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Zoom Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = Loc.getText("waveform_zoom") + ":",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FilledTonalIconButton(
                            onClick = { if (zoomIndex > 0) changeZoom(zoomIndex - 1) },
                            enabled = zoomIndex > 0,
                            modifier = Modifier.size(32.dp).testTag("btn_zoom_out")
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = Loc.getText("zoom_out"), modifier = Modifier.size(16.dp))
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable { 
                                if (zoomIndex == 0) changeZoom(2) else changeZoom(0) 
                            }
                        ) {
                            Text(
                                text = if (zoomIndex == 0) "1x (${Loc.getText("zoom_fit")})" else "${zoomLevels[zoomIndex].toInt()}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { if (zoomIndex < zoomLevels.lastIndex) changeZoom(zoomIndex + 1) },
                            enabled = zoomIndex < zoomLevels.lastIndex,
                            modifier = Modifier.size(32.dp).testTag("btn_zoom_in")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = Loc.getText("zoom_in"), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Cut Count & Follow playhead chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "${cuts.size} ${Loc.getText("cuts_label")}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        FilterChip(
                            selected = followPlayhead,
                            onClick = { followPlayhead = !followPlayhead },
                            label = { 
                                Text(
                                    text = Loc.getText("follow_playhead"), 
                                    fontSize = 11.sp,
                                    fontWeight = if (followPlayhead) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            leadingIcon = if (followPlayhead) {
                                { Icon(Icons.Filled.MyLocation, contentDescription = Loc.getText("follow_playhead_desc"), modifier = Modifier.size(12.dp)) }
                            } else null,
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Safe canvas upper bound to keep within Android display list limits
                val maxSafeCanvasWidthDp = 24000.dp

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(205.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    val containerWidthDp = maxWidth

                    if (isLoadingWaveform) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(Loc.getText("waveform_loading"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        val effectiveCanvasWidth = remember(containerWidthDp, currentZoom) {
                            if (currentZoom <= 1.0f) {
                                containerWidthDp
                            } else {
                                (containerWidthDp * currentZoom).coerceIn(containerWidthDp, maxSafeCanvasWidthDp)
                            }
                        }

                        // Smoothly scroll to keep focal point (cut or playhead) centered whenever zoom changes
                        LaunchedEffect(zoomIndex, effectiveCanvasWidth) {
                            val ratio = pendingFocusRatio ?: return@LaunchedEffect
                            snapshotFlow { horizontalScrollState.maxValue }
                                .filter { it > 0 || zoomIndex == 0 }
                                .first()
                            val maxScroll = horizontalScrollState.maxValue
                            if (maxScroll > 0) {
                                val visiblePx = with(density) { containerWidthDp.toPx() }
                                val target = ((ratio * (maxScroll + visiblePx)) - (visiblePx / 2f)).toInt().coerceIn(0, maxScroll)
                                horizontalScrollState.scrollTo(target)
                            } else {
                                horizontalScrollState.scrollTo(0)
                            }
                            pendingFocusRatio = null
                        }

                        // Auto-gain safeguard so speech waveforms are always big, clear, and prominent
                        val observedMaxAmp = remember(waveformData) {
                            waveformData.maxOfOrNull { it.amplitude } ?: 1.0f
                        }
                        val waveformGain = remember(observedMaxAmp) {
                            if (observedMaxAmp < 0.40f && observedMaxAmp > 0.001f) {
                                (0.92f / observedMaxAmp).coerceAtMost(12.0f)
                            } else {
                                1.0f
                            }
                        }

                        val rulerTextPaint = remember(density) {
                            Paint().apply {
                                color = android.graphics.Color.argb(160, 150, 150, 160)
                                textSize = with(density) { 9.dp.toPx() }
                                isAntiAlias = true
                                typeface = Typeface.MONOSPACE
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val playheadColor = Color(0xFFE53935)
                            val cutColor = MaterialTheme.colorScheme.tertiary
                            val selectedCutColor = MaterialTheme.colorScheme.primary
                            val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)

                            Canvas(
                                modifier = Modifier
                                    .width(effectiveCanvasWidth)
                                    .fillMaxHeight()
                                    .pointerInput(effectiveDuration, currentZoom) {
                                        detectTapGestures { offset ->
                                            val clickedRatio = (offset.x / size.width).coerceIn(0f, 1f)
                                            val clickedMs = (clickedRatio * effectiveDuration).toLong()
                                            AudioPlayerManager.seekTo(clickedMs, isPhysicalTimestamp = false)

                                            // Check if tapped near an existing cut
                                            val hitThresholdPx = 28.dp.toPx()
                                            val hitIndex = cuts.indexOfFirst { cutMs ->
                                                val cutX = (cutMs.toFloat() / effectiveDuration.toFloat()) * size.width
                                                kotlin.math.abs(cutX - offset.x) <= hitThresholdPx
                                            }
                                            selectedCutIndex = if (hitIndex >= 0) hitIndex else null
                                        }
                                    }
                                    .pointerInput(effectiveDuration, currentZoom) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val hitThresholdPx = 32.dp.toPx()
                                                val hitIndex = cuts.indexOfFirst { cutMs ->
                                                    val cutX = (cutMs.toFloat() / effectiveDuration.toFloat()) * size.width
                                                    kotlin.math.abs(cutX - offset.x) <= hitThresholdPx
                                                }
                                                if (hitIndex >= 0) {
                                                    selectedCutIndex = hitIndex
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val idx = selectedCutIndex
                                                if (idx != null && idx in cuts.indices) {
                                                    val newRatio = (change.position.x / size.width).coerceIn(0f, 1f)
                                                    val newMs = (newRatio * effectiveDuration).toLong()
                                                    val prev = if (idx > 0) cuts[idx - 1] + 150L else 200L
                                                    val next = if (idx < cuts.lastIndex) cuts[idx + 1] - 150L else effectiveDuration - 200L
                                                    cuts[idx] = newMs.coerceIn(prev, next)
                                                } else {
                                                    // Dragging playhead
                                                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                                                    val targetMs = (ratio * effectiveDuration).toLong()
                                                    AudioPlayerManager.seekTo(targetMs, isPhysicalTimestamp = false)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                val canvasW = size.width
                                val canvasH = size.height
                                val rulerH = 22.dp.toPx()
                                val waveTop = rulerH + 4.dp.toPx()
                                val waveH = canvasH - waveTop - 4.dp.toPx()
                                val centerY = waveTop + (waveH / 2f)

                                // Visible viewport culling window
                                val scrollX = horizontalScrollState.value.toFloat()
                                val visibleWPx = containerWidthDp.toPx()
                                val minVisibleX = (scrollX - 80f).coerceAtLeast(0f)
                                val maxVisibleX = scrollX + visibleWPx + 80f

                                // 1. Time Ruler Grid & Exact Timestamps
                                val pxPerSec = if (effectiveDuration > 0) canvasW / (effectiveDuration / 1000f) else 100f
                                val (majorStepMs, minorStepMs) = when {
                                    pxPerSec >= 1500f -> Pair(100L, 20L)    // 100ms major, 20ms minor ticks
                                    pxPerSec >= 600f  -> Pair(250L, 50L)    // 250ms major, 50ms minor ticks
                                    pxPerSec >= 250f  -> Pair(500L, 100L)   // 500ms major, 100ms minor ticks
                                    pxPerSec >= 100f  -> Pair(1000L, 200L)  // 1s major, 200ms minor ticks
                                    pxPerSec >= 40f   -> Pair(2000L, 500L)  // 2s major, 500ms minor ticks
                                    pxPerSec >= 15f   -> Pair(5000L, 1000L) // 5s major, 1s minor ticks
                                    pxPerSec >= 6f    -> Pair(15000L, 5000L)// 15s major, 5s minor ticks
                                    pxPerSec >= 2f    -> Pair(30000L, 10000L)// 30s major, 10s minor ticks
                                    pxPerSec >= 0.8f  -> Pair(60000L, 15000L)// 1m major, 15s minor ticks
                                    else              -> Pair(120000L, 30000L)// 2m major, 30s minor ticks
                                }

                                val minMs = ((minVisibleX / canvasW) * effectiveDuration).toLong().coerceAtLeast(0L)
                                val maxMs = (((maxVisibleX / canvasW) * effectiveDuration).toLong() + majorStepMs).coerceAtMost(effectiveDuration)

                                val firstMinor = (minMs / minorStepMs) * minorStepMs
                                var t = firstMinor
                                while (t <= maxMs) {
                                    val x = (t.toFloat() / effectiveDuration.toFloat()) * canvasW
                                    val isMajor = (t % majorStepMs == 0L)
                                    if (isMajor) {
                                        // Full vertical guide line
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(x, 0f),
                                            end = Offset(x, canvasH),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                        val minutes = t / 60000L
                                        val seconds = (t % 60000L) / 1000L
                                        val timeLabel = if (majorStepMs < 1000L) {
                                            val millis = (t % 1000L) / 100L
                                            "$minutes:${if (seconds < 10) "0$seconds" else "$seconds"}.$millis"
                                        } else {
                                            "$minutes:${if (seconds < 10) "0$seconds" else "$seconds"}"
                                        }
                                        drawContext.canvas.nativeCanvas.drawText(
                                            timeLabel,
                                            x + 3.dp.toPx(),
                                            rulerH - 5.dp.toPx(),
                                            rulerTextPaint
                                        )
                                    } else {
                                        // Minor tick in ruler header
                                        drawLine(
                                            color = gridColor.copy(alpha = 0.5f),
                                            start = Offset(x, rulerH - 5.dp.toPx()),
                                            end = Offset(x, rulerH),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    t += minorStepMs
                                }

                                // 2. Segment background bands
                                var prevCutX = 0f
                                cuts.forEachIndexed { i, cutMs ->
                                    val cutX = (cutMs.toFloat() / effectiveDuration.toFloat()) * canvasW
                                    if (cutX >= minVisibleX && prevCutX <= maxVisibleX) {
                                        val bandColor = if (i % 2 == 0) {
                                            primaryColor.copy(alpha = 0.06f)
                                        } else {
                                            Color(0xFF81C784).copy(alpha = 0.07f)
                                        }
                                        val drawStart = prevCutX.coerceAtLeast(minVisibleX)
                                        val drawEnd = cutX.coerceAtMost(maxVisibleX)
                                        if (drawEnd > drawStart) {
                                            drawRect(
                                                color = bandColor,
                                                topLeft = Offset(drawStart, waveTop),
                                                size = Size(drawEnd - drawStart, waveH)
                                            )
                                        }
                                    }
                                    prevCutX = cutX
                                }
                                if (prevCutX < canvasW && canvasW >= minVisibleX && prevCutX <= maxVisibleX) {
                                    val drawStart = prevCutX.coerceAtLeast(minVisibleX)
                                    val drawEnd = canvasW.coerceAtMost(maxVisibleX)
                                    if (drawEnd > drawStart) {
                                        drawRect(
                                            color = primaryColor.copy(alpha = 0.04f),
                                            topLeft = Offset(drawStart, waveTop),
                                            size = Size(drawEnd - drawStart, waveH)
                                        )
                                    }
                                }

                                // 3. Subtle Center Baseline & High-Definition Waveform Bars
                                drawLine(
                                    color = primaryColor.copy(alpha = 0.20f),
                                    start = Offset(minVisibleX, centerY),
                                    end = Offset(maxVisibleX, centerY),
                                    strokeWidth = 1.dp.toPx()
                                )

                                if (waveformData.isNotEmpty()) {
                                    val barWidthPx = 3.0.dp.toPx()
                                    val barSpacingPx = 1.5.dp.toPx()
                                    val barPitchPx = barWidthPx + barSpacingPx

                                    val totalBars = (canvasW / barPitchPx).toInt().coerceAtLeast(1)
                                    val msPerBar = effectiveDuration.toFloat() / totalBars.toFloat()

                                    val firstVisibleBar = ((minVisibleX / barPitchPx).toInt() - 1).coerceAtLeast(0)
                                    val lastVisibleBar = ((maxVisibleX / barPitchPx).toInt() + 1).coerceAtMost(totalBars - 1)

                                    val curPlayheadX = (currentPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f) * canvasW

                                    for (b in firstVisibleBar..lastVisibleBar) {
                                        val barX = b * barPitchPx + (barWidthPx / 2f)
                                        val barTimeStart = (b * msPerBar).toLong()
                                        val barTimeEnd = ((b + 1) * msPerBar).toLong()

                                        val startIdx = (barTimeStart / 50L).toInt().coerceIn(0, waveformData.size - 1)
                                        val endIdx = (barTimeEnd / 50L).toInt().coerceIn(startIdx, waveformData.size - 1)

                                        var peakAmp = 0f
                                        if (startIdx == endIdx) {
                                            // Smooth high-zoom linear interpolation between 50ms samples
                                            val midTime = (barTimeStart + barTimeEnd) / 2f
                                            val frac = ((midTime - startIdx * 50f) / 50f).coerceIn(0f, 1f)
                                            val a1 = waveformData[startIdx].amplitude
                                            val a2 = if (startIdx < waveformData.size - 1) waveformData[startIdx + 1].amplitude else a1
                                            peakAmp = a1 * (1f - frac) + a2 * frac
                                        } else {
                                            // Peak aggregation across 50ms points
                                            for (k in startIdx..endIdx) {
                                                val a = waveformData[k].amplitude
                                                if (a > peakAmp) peakAmp = a
                                            }
                                        }

                                        val boosted = (peakAmp * waveformGain).coerceIn(0f, 1f)
                                        val visualAmp = Math.pow(boosted.toDouble(), 0.65).toFloat().coerceIn(0.05f, 1.0f)
                                        val barH = (visualAmp * (waveH / 2f) * 0.95f).coerceAtLeast(2.5.dp.toPx())

                                        val isPlayed = (barX <= curPlayheadX)
                                        val barColor = if (isPlayed) {
                                            primaryColor
                                        } else {
                                            primaryColor.copy(alpha = 0.65f)
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

                                // 4. Cut boundary lines and flags
                                cuts.forEachIndexed { i, cutMs ->
                                    val cutX = (cutMs.toFloat() / effectiveDuration.toFloat()) * canvasW
                                    if (cutX in minVisibleX..maxVisibleX) {
                                        val isSelected = (selectedCutIndex == i)
                                        val flagColor = if (isSelected) selectedCutColor else cutColor

                                        // Vertical dashed line
                                        drawLine(
                                            color = flagColor,
                                            start = Offset(cutX, waveTop),
                                            end = Offset(cutX, canvasH),
                                            strokeWidth = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                        )

                                        // Pin/Flag at top
                                        drawCircle(
                                            color = flagColor,
                                            radius = if (isSelected) 7.dp.toPx() else 5.dp.toPx(),
                                            center = Offset(cutX, rulerH / 2f)
                                        )
                                    }
                                }

                                // 5. Playhead Line
                                val playheadRatio = (currentPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
                                val playheadX = playheadRatio * canvasW
                                if (playheadX in minVisibleX..maxVisibleX) {
                                    drawLine(
                                        color = playheadColor,
                                        start = Offset(playheadX, 0f),
                                        end = Offset(playheadX, canvasH),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                    drawCircle(
                                        color = playheadColor,
                                        radius = 6.dp.toPx(),
                                        center = Offset(playheadX, rulerH / 2f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Playback and Selected Cut Fine-Tuning Controls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Playback & "+ Add Cut Here" Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Playback buttons
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = {
                                        val target = (currentPosition - 2000L).coerceAtLeast(0L)
                                        AudioPlayerManager.seekTo(target, isPhysicalTimestamp = false)
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Filled.Replay, contentDescription = "-2s", modifier = Modifier.size(16.dp))
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                FilledIconButton(
                                    onClick = {
                                        if (isPlaying) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                                    },
                                    modifier = Modifier.size(38.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                FilledTonalIconButton(
                                    onClick = {
                                        val target = (currentPosition + 2000L).coerceAtMost(effectiveDuration)
                                        AudioPlayerManager.seekTo(target, isPhysicalTimestamp = false)
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Filled.Forward5, contentDescription = "+2s", modifier = Modifier.size(16.dp))
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = formatTimestamp(currentPosition),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // "+ Add Cut Here" Button
                            Button(
                                onClick = { addCut(currentPosition) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_add_cut_at_playhead")
                            ) {
                                Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Loc.getText("add_cut_at_playhead"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Selected Cut Nudge & Preview Bar
                        val selIdx = selectedCutIndex
                        if (selIdx != null && selIdx in cuts.indices) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = String.format(java.util.Locale.US, Loc.getText("segment_num"), selIdx + 1) +
                                                " (${formatTimestamp(cuts[selIdx])})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Preview segment
                                    OutlinedButton(
                                        onClick = { previewSegment(selIdx) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(Loc.getText("play_segment_preview"), fontSize = 10.sp)
                                    }

                                    // Nudge backward 50ms
                                    FilledTonalButton(
                                        onClick = { nudgeSelectedCut(-50L) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("btn_nudge_backward")
                                    ) {
                                        Text("-50ms", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Micro-nudge backward 10ms
                                    FilledTonalButton(
                                        onClick = { nudgeSelectedCut(-10L) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("btn_nudge_back_10ms")
                                    ) {
                                        Text("-10ms", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Micro-nudge forward 10ms
                                    FilledTonalButton(
                                        onClick = { nudgeSelectedCut(10L) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("btn_nudge_fwd_10ms")
                                    ) {
                                        Text("+10ms", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Nudge forward 50ms
                                    FilledTonalButton(
                                        onClick = { nudgeSelectedCut(50L) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("btn_nudge_forward")
                                    ) {
                                        Text("+50ms", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Delete cut
                                    IconButton(
                                        onClick = { removeCut(selIdx) },
                                        modifier = Modifier.size(28.dp).testTag("btn_delete_selected_cut")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = Loc.getText("delete_cut"),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Presets row: Import from Silence / Subtitles / Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    val silenceCuts = SilenceDetector.detectBoundaries(
                                        context = context,
                                        filePath = track.filePath,
                                        totalDurationMs = effectiveDuration
                                    )
                                    cuts.clear()
                                    cuts.addAll(silenceCuts)
                                    selectedCutIndex = if (cuts.isNotEmpty()) 0 else null
                                    Toast.makeText(context, "${silenceCuts.size} cuts imported from silence", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp).testTag("btn_import_from_silence")
                        ) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Loc.getText("copy_from_silence"), fontSize = 10.sp)
                        }

                        if (AudioPlayerManager.subtitlesCues.value.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    val subCuts = AudioPlayerManager.subtitlesCues.value.map { it.endMs }.filter { it > 0 }.distinct().sorted()
                                    cuts.clear()
                                    cuts.addAll(subCuts)
                                    selectedCutIndex = if (cuts.isNotEmpty()) 0 else null
                                    Toast.makeText(context, "${subCuts.size} cuts imported from subtitles", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp).testTag("btn_import_from_subtitles")
                            ) {
                                Icon(Icons.Filled.Subtitles, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Loc.getText("copy_from_subtitles"), fontSize = 10.sp)
                            }
                        }
                    }

                    if (cuts.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                cuts.clear()
                                selectedCutIndex = null
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(Loc.getText("clear_all_cuts"), fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Section: Segment List
                Text(
                    text = String.format(Loc.getText("manual_segments_count"), cuts.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (cuts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Loc.getText("no_cuts_yet"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(cuts) { idx, cutMs ->
                            val startMs = if (idx > 0) cuts[idx - 1] else 0L
                            val durationSec = (cutMs - startMs) / 1000f
                            val isSelected = (selectedCutIndex == idx)

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                },
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCutIndex = idx
                                        AudioPlayerManager.seekTo(startMs, isPhysicalTimestamp = false)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "#${idx + 1}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${formatTimestamp(startMs)} → ${formatTimestamp(cutMs)}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = String.format(java.util.Locale.US, "(%.1fs)", durationSec),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { previewSegment(idx) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = "Preview",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { removeCut(idx) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val totalSec = ms / 1000L
    val m = totalSec / 60L
    val s = totalSec % 60L
    val millis = (ms % 1000L) / 100L // 1 decimal place for tenths
    return String.format(java.util.Locale.US, "%02d:%02d.%d", m, s, millis)
}
