package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import com.example.player.AudioPlayerManager
import com.example.player.SilenceDetector
import com.example.player.WaveformPoint
import com.example.ui.waveform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

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
    val currentPositionState = AudioPlayerManager.currentPosition.collectAsStateWithLifecycle()
    val positionMs: () -> Long = { currentPositionState.value }
    val durationState by AudioPlayerManager.duration.collectAsStateWithLifecycle()
    val subtitlesCues by AudioPlayerManager.subtitlesCues.collectAsStateWithLifecycle()

    var fileDurationMs by remember(track.filePath) { mutableLongStateOf(0L) }
    LaunchedEffect(track.filePath) {
        withContext(Dispatchers.IO) {
            val d = SilenceDetector.getAudioDuration(context, track.filePath)
            if (d > 0) fileDurationMs = d
        }
    }

    var waveformData by remember { mutableStateOf<List<WaveformPoint>>(emptyList()) }
    var isLoadingWaveform by remember { mutableStateOf(true) }

    val effectiveDuration = remember(track.duration, durationState, fileDurationMs, waveformData) {
        val maxWaveformTime = waveformData.lastOrNull()?.timeMs ?: 0L
        maxOf(
            durationState,
            track.duration,
            fileDurationMs,
            maxWaveformTime,
            1000L
        )
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

    val zoomLevels = remember { defaultWaveformZoomLevels() }
    var zoomIndex by remember { mutableIntStateOf(2) } // Default: index 2 -> 1x (220 dp/sec)
    var pendingFocusMs by remember { mutableStateOf<Long?>(null) }
    var scrollOffsetPx by remember { mutableDoubleStateOf(0.0) }

    fun changeZoom(newIndex: Int, explicitFocusMs: Long? = null) {
        val bounded = newIndex.coerceIn(0, zoomLevels.lastIndex)
        if (bounded == zoomIndex && explicitFocusMs == null) return
        val focusMs = explicitFocusMs ?: run {
            selectedCutIndex?.let { if (it in cuts.indices) cuts[it] else null } ?: positionMs()
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

    // Immediate dynamic envelope fallback so the waveform canvas is NEVER blank while loading or decoding
    val activeWaveform = remember(waveformData, effectiveDuration) {
        if (waveformData.isNotEmpty()) {
            waveformData
        } else {
            buildSyntheticSpeechEnvelope(effectiveDuration)
        }
    }

    LaunchedEffect(track.filePath) {
        isLoadingWaveform = true
        withContext(Dispatchers.IO) {
            SilenceDetector.clearWaveformCacheFor(track.filePath)
            val dur = maxOf(effectiveDuration, fileDurationMs, track.duration)
            val extracted = SilenceDetector.extractWaveform(context, track.filePath, dur)
            waveformData = extracted
        }
        isLoadingWaveform = false
    }

    // Single segment preview playback job
    var previewJob by remember { mutableStateOf<Job?>(null) }

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
        if (cuts.none { abs(it - bounded) < 150L }) {
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

    val importFromSilence: (String) -> Unit = { locKey ->
        coroutineScope.launch {
            val silenceCuts = SilenceDetector.detectBoundaries(
                context = context,
                filePath = track.filePath,
                totalDurationMs = effectiveDuration
            )
            cuts.clear()
            cuts.addAll(silenceCuts)
            selectedCutIndex = if (cuts.isNotEmpty()) 0 else null
            Toast.makeText(context, Loc.getFormattedText(locKey, silenceCuts.size), Toast.LENGTH_SHORT).show()
        }
    }

    val importFromSubtitles: () -> Unit = {
        val subCuts = subtitlesCues.map { it.endMs }.filter { it > 0 }.distinct().sorted()
        cuts.clear()
        cuts.addAll(subCuts)
        selectedCutIndex = if (cuts.isNotEmpty()) 0 else null
        Toast.makeText(context, Loc.getFormattedText("cuts_imported_from_subtitles", subCuts.size), Toast.LENGTH_SHORT).show()
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
                WaveformEditorHeader(
                    fileName = track.fileName,
                    onDismiss = onDismiss,
                    onSaveAndApply = {
                        viewModel.saveManualPracticeSegments(track, cuts.toList(), context, autoEnable = true)
                        viewModel.updatePracticeSettings("MANUAL", viewModel.practicePauseMultiplierSetting)
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                WaveformEditorToolbar(
                    zoomIndex = zoomIndex,
                    zoomLevels = zoomLevels,
                    cutsCount = cuts.size,
                    followPlayhead = followPlayhead,
                    onChangeZoom = { changeZoom(it) },
                    onToggleFollowPlayhead = { followPlayhead = !followPlayhead }
                )

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
                    LaunchedEffect(zoomIndex, pxPerSec, containerWidthPx, pendingFocusMs) {
                        val focus = pendingFocusMs
                        if (focus != null) {
                            val targetScroll = ((focus / 1000.0) * pxPerSec - (containerWidthPx / 2.0)).coerceIn(0.0, maxScrollPx)
                            scrollOffsetPx = targetScroll
                            pendingFocusMs = null
                        } else {
                            scrollOffsetPx = scrollOffsetPx.coerceIn(0.0, maxScrollPx)
                        }
                    }

                    // Auto-scroll to follow playhead during playback without triggering root recompositions
                    LaunchedEffect(isPlaying, followPlayhead, pxPerSec, containerWidthPx, maxScrollPx, effectiveDuration) {
                        if (followPlayhead && effectiveDuration > 0 && isPlaying) {
                            snapshotFlow { positionMs() }.collect { pos ->
                                val playheadScreenX = ((pos / 1000.0) * pxPerSec - scrollOffsetPx).toFloat()
                                if (playheadScreenX > containerWidthPx * 0.72f || playheadScreenX < containerWidthPx * 0.12f) {
                                    val target = ((pos / 1000.0) * pxPerSec - containerWidthPx * 0.28f).coerceIn(0.0, maxScrollPx)
                                    scrollOffsetPx = target
                                }
                            }
                        }
                    }

                    val waveformGain = remember(waveformData) { computeWaveformGain(waveformData) }
                    val paints = rememberWaveformPaints(density)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 1. Mini-Map Overview Strip
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .miniMapGestures(
                                    effectiveDuration = effectiveDuration,
                                    totalVirtualWidthPx = totalVirtualWidthPx,
                                    pxPerSec = pxPerSec,
                                    containerWidthPx = containerWidthPx,
                                    maxScrollPx = maxScrollPx,
                                    onScrollOffsetChange = { scrollOffsetPx = it }
                                )
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawMiniMap(
                                    effectiveDuration = effectiveDuration,
                                    cuts = cuts,
                                    positionMs = positionMs,
                                    scrollOffsetPx = scrollOffsetPx,
                                    totalVirtualWidthPx = totalVirtualWidthPx,
                                    containerWidthPx = containerWidthPx,
                                    primaryColor = primaryColor,
                                    cutColor = cutColor,
                                    playheadColor = playheadColor
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 1.dp)

                        // 2. Main Ultra-Wide Waveform Viewport
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(118.dp)
                                .transformable(state = transformState)
                                .waveformGestures(
                                    pxPerSec = pxPerSec,
                                    cuts = cuts,
                                    effectiveDuration = effectiveDuration,
                                    maxScrollPx = maxScrollPx,
                                    getScrollOffsetPx = { scrollOffsetPx },
                                    onScrollOffsetChange = { scrollOffsetPx = it },
                                    onSelectCut = { selectedCutIndex = it }
                                )
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (effectiveDuration <= 0) return@Canvas
                                val canvasW = size.width
                                val canvasH = size.height
                                val rulerH = 20.dp.toPx()
                                val waveTop = rulerH + 2.dp.toPx()
                                val waveH = (canvasH - waveTop - 2.dp.toPx()).coerceAtLeast(10f)
                                val centerY = waveTop + (waveH / 2f)
                                val axis = TimeAxis(pxPerSec = pxPerSec, scrollPx = scrollOffsetPx)
                                val activeData = if (waveformData.isNotEmpty()) waveformData else activeWaveform

                                drawTimeRuler(
                                    axis = axis,
                                    effectiveDuration = effectiveDuration,
                                    canvasW = canvasW,
                                    canvasH = canvasH,
                                    rulerH = rulerH,
                                    gridColor = gridColor,
                                    rulerTextPaint = paints.rulerTextPaint
                                )
                                drawSegmentBands(
                                    axis = axis,
                                    effectiveDuration = effectiveDuration,
                                    cuts = cuts,
                                    canvasW = canvasW,
                                    waveTop = waveTop,
                                    waveH = waveH,
                                    primaryColor = primaryColor,
                                    segmentBadgePaint = paints.segmentBadgePaint
                                )
                                drawWaveformBars(
                                    axis = axis,
                                    effectiveDuration = effectiveDuration,
                                    activeData = activeData,
                                    waveformGain = waveformGain,
                                    positionMs = positionMs,
                                    canvasW = canvasW,
                                    waveH = waveH,
                                    centerY = centerY,
                                    primaryColor = primaryColor,
                                    unplayedBarColor = unplayedBarColor,
                                    silentBarColor = silentBarColor
                                )
                                drawCutMarkers(
                                    axis = axis,
                                    cuts = cuts,
                                    selectedCutIndex = selectedCutIndex,
                                    canvasW = canvasW,
                                    canvasH = canvasH,
                                    rulerH = rulerH,
                                    cutColor = cutColor,
                                    selectedCutColor = selectedCutColor,
                                    cutNumPaint = paints.cutNumPaint,
                                    cutTimeBadgePaint = paints.cutTimeBadgePaint
                                )
                                drawPlayhead(
                                    axis = axis,
                                    positionMs = positionMs,
                                    canvasW = canvasW,
                                    canvasH = canvasH,
                                    rulerH = rulerH,
                                    playheadColor = playheadColor
                                )
                            }
                        }
                    }

                    if (isLoadingWaveform) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .align(Alignment.TopCenter),
                            color = primaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                WaveformTransportAndCutCard(
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    effectiveDuration = effectiveDuration,
                    cuts = cuts,
                    selectedCutIndex = selectedCutIndex,
                    onAddCutAtPlayhead = { addCut(positionMs()) },
                    onPreviewSegment = { previewSegment(it) },
                    onNudgeSelectedCut = { nudgeSelectedCut(it) },
                    onRemoveCut = { removeCut(it) }
                )

                Spacer(modifier = Modifier.height(6.dp))

                WaveformPresetsRow(
                    hasSubtitles = subtitlesCues.isNotEmpty(),
                    hasCuts = cuts.isNotEmpty(),
                    onImportFromSilence = { importFromSilence("cuts_imported_from_silence") },
                    onImportFromSubtitles = importFromSubtitles,
                    onClearAllCuts = {
                        cuts.clear()
                        selectedCutIndex = null
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                WaveformSegmentsSection(
                    cuts = cuts,
                    selectedCutIndex = selectedCutIndex,
                    effectiveDuration = effectiveDuration,
                    hasSubtitles = subtitlesCues.isNotEmpty(),
                    onAutoDetectSilence = { importFromSilence("cuts_auto_detected_from_silence") },
                    onImportFromSubtitles = importFromSubtitles,
                    onAddCutAtPlayhead = { addCut(positionMs()) },
                    onSelectSegment = { idx, startMs, cutMs ->
                        selectedCutIndex = idx
                        if (effectiveDuration > 0) {
                            pendingFocusMs = (startMs + cutMs) / 2L
                        }
                    },
                    onPreviewSegment = { previewSegment(it) },
                    onRemoveCut = { removeCut(it) }
                )
            }
        }
    }
}
