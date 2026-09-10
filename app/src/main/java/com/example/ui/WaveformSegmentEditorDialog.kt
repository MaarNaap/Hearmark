package com.example.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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

    // Zoom level states defined by physical scale: dp of width per 1 second of audio
    // 1x (Default Wide) is 180 dp/sec where every single second is stretched wide across 180dp of screen space
    data class WaveformZoomSetting(
        val id: String,
        val dpPerSec: Float, // dp width per 1 second of audio; -1f for Fit whole file
        val labelKey: String,
        val displayLabel: String
    )

    val zoomLevels = remember {
        listOf(
            WaveformZoomSetting("fit", -1f, "zoom_fit", "Fit"),
            WaveformZoomSetting("0.5x", 90f, "zoom_out", "0.5x"),
            WaveformZoomSetting("1x", 180f, "zoom_wide", "1x (Wide)"), // Default: matches previous 128x ultra-wide
            WaveformZoomSetting("1.5x", 270f, "zoom_wide", "1.5x"),
            WaveformZoomSetting("2x", 360f, "zoom_hyper", "2x (Hyper)"), // 360 dp/sec -> 1 full screen = 1 sec
            WaveformZoomSetting("3x", 540f, "zoom_hyper", "3x"),
            WaveformZoomSetting("5x", 900f, "zoom_extreme", "5x (Max)") // 900 dp/sec -> maximum stretch
        )
    }
    var zoomIndex by remember { mutableIntStateOf(2) } // Default: index 2 -> 1x (Wide: 180 dp/sec)
    val currentZoomSetting = zoomLevels[zoomIndex]
    var pendingFocusMs by remember { mutableStateOf<Long?>(null) }
    var scrollOffsetPx by remember { mutableDoubleStateOf(0.0) }

    fun changeZoom(newIndex: Int, explicitFocusMs: Long? = null) {
        val bounded = newIndex.coerceIn(0, zoomLevels.lastIndex)
        if (bounded == zoomIndex && explicitFocusMs == null) return
        val focusMs = explicitFocusMs ?: run {
            selectedCutIndex?.let { if (it in cuts.indices) cuts[it] else null } ?: currentPosition
        }
        zoomIndex = bounded
        pendingFocusMs = focusMs
    }

    // Pinch-to-zoom state on waveform
    var accumulatedPinchZoom by remember { mutableFloatStateOf(1f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        accumulatedPinchZoom *= zoomChange
        if (accumulatedPinchZoom > 1.25f && zoomIndex < zoomLevels.lastIndex) {
            changeZoom(zoomIndex + 1)
            accumulatedPinchZoom = 1f
        } else if (accumulatedPinchZoom < 0.78f && zoomIndex > 0) {
            changeZoom(zoomIndex - 1)
            accumulatedPinchZoom = 1f
        }
    }

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

                // Toolbar: Zoom controls, Presets & Playhead Following
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Zoom Stepper Controls
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
                                modifier = Modifier.size(30.dp).testTag("btn_zoom_out")
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = Loc.getText("zoom_out"), modifier = Modifier.size(15.dp))
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable { 
                                    if (zoomIndex == 2) changeZoom(0) else changeZoom(2) 
                                }
                            ) {
                                val zoomLabel = zoomLevels[zoomIndex].displayLabel
                                Text(
                                    text = zoomLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { if (zoomIndex < zoomLevels.lastIndex) changeZoom(zoomIndex + 1) },
                                enabled = zoomIndex < zoomLevels.lastIndex,
                                modifier = Modifier.size(30.dp).testTag("btn_zoom_in")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = Loc.getText("zoom_in"), modifier = Modifier.size(15.dp))
                            }
                        }

                        // Right side: Cut Count & Follow playhead chip
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

                    // Quick Zoom Presets row & Gesture hint
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val presets = listOf(
                                0 to Loc.getText("zoom_fit"),
                                1 to "0.5x",
                                2 to "1x (" + Loc.getText("zoom_wide") + ")",
                                4 to "2x (" + Loc.getText("zoom_hyper") + ")",
                                6 to "5x (" + Loc.getText("zoom_extreme") + ")"
                            )
                            presets.forEach { (idx, label) ->
                                val isSelected = (zoomIndex == idx)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.clickable { changeZoom(idx) }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = Loc.getText("pinch_to_zoom_hint"),
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    val containerWidthDp = maxWidth
                    val containerWidthPx = with(density) { containerWidthDp.toPx() }

                    val primaryColor = MaterialTheme.colorScheme.primary
                    val playheadColor = Color(0xFFE53935)
                    val cutColor = MaterialTheme.colorScheme.tertiary
                    val selectedCutColor = MaterialTheme.colorScheme.primary
                    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    val silentBarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
                    val unplayedBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)

                    if (isLoadingWaveform) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(255.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(Loc.getText("waveform_loading"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        val pxPerSec = remember(zoomIndex, containerWidthPx, effectiveDuration) {
                            val setting = zoomLevels[zoomIndex]
                            if (setting.dpPerSec <= 0f) {
                                if (effectiveDuration > 0) {
                                    (containerWidthPx / (effectiveDuration / 1000.0)).toFloat().coerceAtLeast(6f)
                                } else 100f
                            } else {
                                with(density) { setting.dpPerSec.dp.toPx() }
                            }
                        }

                        val totalVirtualWidthPx = remember(effectiveDuration, pxPerSec) {
                            (effectiveDuration / 1000.0) * pxPerSec
                        }
                        val maxScrollPx = remember(totalVirtualWidthPx, containerWidthPx) {
                            (totalVirtualWidthPx - containerWidthPx).coerceAtLeast(0.0)
                        }

                        // Keep focal point (cut, playhead, or explicit focus) centered when zoom changes
                        LaunchedEffect(zoomIndex, pxPerSec, containerWidthPx) {
                            val focus = pendingFocusMs
                            if (focus != null) {
                                val targetScroll = ((focus / 1000.0) * pxPerSec - (containerWidthPx / 2.0)).coerceIn(0.0, maxScrollPx)
                                scrollOffsetPx = targetScroll
                                pendingFocusMs = null
                            } else {
                                scrollOffsetPx = scrollOffsetPx.coerceIn(0.0, maxScrollPx)
                            }
                        }

                        // Auto-scroll to follow playhead during playback
                        LaunchedEffect(currentPosition, isPlaying, followPlayhead, pxPerSec) {
                            if (followPlayhead && effectiveDuration > 0 && isPlaying) {
                                val playheadScreenX = ((currentPosition / 1000.0) * pxPerSec - scrollOffsetPx).toFloat()
                                if (playheadScreenX > containerWidthPx * 0.72f || playheadScreenX < containerWidthPx * 0.12f) {
                                    val target = ((currentPosition / 1000.0) * pxPerSec - containerWidthPx * 0.28f).coerceIn(0.0, maxScrollPx)
                                    scrollOffsetPx = target
                                }
                            }
                        }

                        // Auto-gain safeguard so speech waveforms are always prominent and easy to read
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

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 1. Mini-Map Overview Strip: Full track overview with cut markers and visible viewport window
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .pointerInput(effectiveDuration, totalVirtualWidthPx, pxPerSec, containerWidthPx) {
                                        detectTapGestures { offset ->
                                            if (totalVirtualWidthPx > 0 && effectiveDuration > 0) {
                                                val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                                val targetMs = (ratio * effectiveDuration).toLong()
                                                val targetScroll = ((targetMs / 1000.0) * pxPerSec - (containerWidthPx / 2.0)).coerceIn(0.0, maxScrollPx)
                                                scrollOffsetPx = targetScroll
                                            }
                                        }
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    if (effectiveDuration <= 0) return@Canvas

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
                                    val playheadRatio = (currentPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
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
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                        )
                                        drawRoundRect(
                                            color = primaryColor.copy(alpha = 0.85f),
                                            topLeft = Offset(boxLeft, 1.dp.toPx()),
                                            size = Size(boxRight - boxLeft, h - 2.dp.toPx()),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 1.dp)

                            // 2. Main Ultra-Wide Waveform Viewport
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(235.dp)
                                    .transformable(state = transformState)
                                    .pointerInput(pxPerSec, cuts.size, effectiveDuration) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val hitThresholdPx = 32.dp.toPx()
                                            val hitCutIndex = cuts.indexOfFirst { cutMs ->
                                                val cutScreenX = ((cutMs / 1000.0) * pxPerSec - scrollOffsetPx).toFloat()
                                                kotlin.math.abs(cutScreenX - down.position.x) <= hitThresholdPx
                                            }

                                            if (hitCutIndex >= 0) {
                                                // Direct Cut Handle Dragging
                                                selectedCutIndex = hitCutIndex
                                                down.consume()
                                                val pointerId = down.id
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                                    if (change.isConsumed || !change.pressed) break
                                                    change.consume()
                                                    val touchX = change.position.x
                                                    val newMs = (((scrollOffsetPx + touchX) / pxPerSec) * 1000.0).toLong().coerceIn(0L, effectiveDuration)
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
                                                            selectedCutIndex = null
                                                            val clickedMs = (((scrollOffsetPx + down.position.x) / pxPerSec) * 1000.0).toLong().coerceIn(0L, effectiveDuration)
                                                            AudioPlayerManager.seekTo(clickedMs, isPhysicalTimestamp = false)
                                                        }
                                                        break
                                                    }
                                                    val deltaX = change.position.x - change.previousPosition.x
                                                    totalDragX += deltaX
                                                    if (!isDrag && kotlin.math.abs(totalDragX) > 6f) {
                                                        isDrag = true
                                                    }
                                                    if (isDrag) {
                                                        change.consume()
                                                        scrollOffsetPx = (scrollOffsetPx - deltaX).coerceIn(0.0, maxScrollPx)
                                                    }
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val canvasW = size.width
                                    val canvasH = size.height
                                    val rulerH = 24.dp.toPx()
                                    val waveTop = rulerH + 6.dp.toPx()
                                    val waveH = canvasH - waveTop - 6.dp.toPx()
                                    val centerY = waveTop + (waveH / 2f)

                                    if (effectiveDuration <= 0) return@Canvas

                                    fun timeToScreenX(timeMs: Long): Float {
                                        return ((timeMs / 1000.0) * pxPerSec - scrollOffsetPx).toFloat()
                                    }

                                    val minVisibleMs = ((scrollOffsetPx / pxPerSec) * 1000.0).toLong().coerceAtLeast(0L)
                                    val maxVisibleMs = (((scrollOffsetPx + canvasW) / pxPerSec) * 1000.0).toLong().coerceAtMost(effectiveDuration)

                                    // 1. Time Ruler Grid & Exact Dynamic Timestamps
                                    val (majorStepMs, minorStepMs) = when {
                                        pxPerSec >= 600f -> Pair(100L, 20L)    // 100ms major, 20ms minor ticks (5x Max Stretch)
                                        pxPerSec >= 300f -> Pair(250L, 50L)    // 250ms major, 50ms minor ticks (2x Hyper)
                                        pxPerSec >= 150f -> Pair(500L, 100L)   // 500ms major, 100ms minor ticks (1x Wide - Default)
                                        pxPerSec >= 70f  -> Pair(1000L, 200L)  // 1s major, 200ms minor ticks (0.5x)
                                        pxPerSec >= 30f  -> Pair(2000L, 500L)  // 2s major, 500ms minor ticks
                                        pxPerSec >= 10f  -> Pair(5000L, 1000L) // 5s major, 1s minor ticks
                                        else             -> Pair(10000L, 2000L)// 10s major, 2s minor ticks
                                    }

                                    val firstMinor = (minVisibleMs / minorStepMs) * minorStepMs
                                    var t = firstMinor
                                    while (t <= maxVisibleMs + majorStepMs) {
                                        val x = timeToScreenX(t)
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
                                                        String.format(java.util.Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
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

                                    // 2. Segment background bands and badges
                                    var prevCutMs = 0L
                                    cuts.forEachIndexed { i, cutMs ->
                                        val startX = timeToScreenX(prevCutMs)
                                        val endX = timeToScreenX(cutMs)
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
                                                val label = "S${i + 1} (${String.format(java.util.Locale.US, "%.1fs", durationSec)})"
                                                drawContext.canvas.nativeCanvas.drawText(
                                                    label,
                                                    startX.coerceAtLeast(4.dp.toPx()) + 4.dp.toPx(),
                                                    waveTop + 14.dp.toPx(),
                                                    segmentBadgePaint
                                                )
                                            }
                                        }
                                        prevCutMs = cutMs
                                    }
                                    if (prevCutMs < effectiveDuration) {
                                        val startX = timeToScreenX(prevCutMs)
                                        val endX = timeToScreenX(effectiveDuration)
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
                                                val label = "S${cuts.size + 1} (${String.format(java.util.Locale.US, "%.1fs", durationSec)})"
                                                drawContext.canvas.nativeCanvas.drawText(
                                                    label,
                                                    startX.coerceAtLeast(4.dp.toPx()) + 4.dp.toPx(),
                                                    waveTop + 14.dp.toPx(),
                                                    segmentBadgePaint
                                                )
                                            }
                                        }
                                    }

                                    // 3. Subtle Center Baseline
                                    drawLine(
                                        color = primaryColor.copy(alpha = 0.18f),
                                        start = Offset(0f, centerY),
                                        end = Offset(canvasW, centerY),
                                        strokeWidth = 1.dp.toPx()
                                    )

                                    // 4. Ultra-Wide High-Definition Waveform Bars
                                    if (waveformData.isNotEmpty()) {
                                        val barWidthPx = 3.2.dp.toPx()
                                        val barSpacingPx = 1.8.dp.toPx()
                                        val barPitchPx = barWidthPx + barSpacingPx

                                        val numScreenBars = (canvasW / barPitchPx).toInt() + 2
                                        val curPlayheadX = timeToScreenX(currentPosition)

                                        for (b in 0..numScreenBars) {
                                            val barX = b * barPitchPx + (barWidthPx / 2f)
                                            val barTimeMs = (((scrollOffsetPx + barX) / pxPerSec) * 1000.0).toLong()

                                            if (barTimeMs in 0L..effectiveDuration) {
                                                val sampleIdx = (barTimeMs / 50L).toInt().coerceIn(0, waveformData.size - 1)
                                                val nextIdx = (sampleIdx + 1).coerceAtMost(waveformData.size - 1)
                                                val frac = ((barTimeMs % 50L) / 50f).coerceIn(0f, 1f)
                                                val smoothFrac = (1f - kotlin.math.cos(frac * Math.PI.toFloat())) / 2f
                                                val rawAmp = waveformData[sampleIdx].amplitude * (1f - smoothFrac) + waveformData[nextIdx].amplitude * smoothFrac
                                                val boosted = (rawAmp * waveformGain).coerceIn(0f, 1f)

                                                // Clear distinction: silence has small baseline dots, speech has prominent bars
                                                val isSilentGap = boosted <= 0.08f
                                                val visualAmp = if (isSilentGap) {
                                                    0.02f
                                                } else {
                                                    val speechNorm = ((boosted - 0.08f) / 0.92f).coerceIn(0f, 1f)
                                                    (0.12f + 0.88f * Math.pow(speechNorm.toDouble(), 0.50).toFloat()).coerceIn(0.08f, 1.0f)
                                                }
                                                val barH = if (isSilentGap) 1.5.dp.toPx() else (visualAmp * (waveH / 2f) * 0.96f).coerceAtLeast(2.8.dp.toPx())

                                                val isPlayed = (barX <= curPlayheadX)
                                                val barColor = when {
                                                    isSilentGap -> silentBarColor
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

                                    // 5. Cut Boundary Lines, Pins, and Timestamp Badges
                                    cuts.forEachIndexed { i, cutMs ->
                                        val cutX = timeToScreenX(cutMs)
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
                                            if (isSelected || pxPerSec >= 150f) {
                                                val millis = cutMs % 1000L
                                                val seconds = (cutMs % 60000L) / 1000L
                                                val minutes = cutMs / 60000L
                                                val badgeText = String.format(java.util.Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
                                                drawContext.canvas.nativeCanvas.drawText(
                                                    badgeText,
                                                    cutX,
                                                    canvasH - 4.dp.toPx(),
                                                    cutTimeBadgePaint
                                                )
                                            }
                                        }
                                    }

                                    // 6. Playhead Line & Top Marker
                                    val playheadX = timeToScreenX(currentPosition)
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
                                        if (effectiveDuration > 0) {
                                            val midMs = (startMs + cutMs) / 2L
                                            pendingFocusMs = midMs
                                        }
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
