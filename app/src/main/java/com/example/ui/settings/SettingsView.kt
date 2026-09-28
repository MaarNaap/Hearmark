package com.example.ui

import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.WindowManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.RectangleShape
import com.example.data.*
import com.example.player.*
import com.example.ui.theme.*
import com.example.R
import com.example.util.AudioMetadataExtractor
import com.example.util.TrackMetadata
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

// Note: StatsView, charts, and statistics data structures are located in StatsScreen.kt

// --- SUB-SCREEN 9: SETTINGS VIEW (Themes, Lang, sliders) ---
@Composable
fun SettingsView(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var sliderValue by remember { mutableStateOf(viewModel.thresholdSetting.toFloat()) }
    var skipVal by remember { mutableStateOf(viewModel.skipSecondsSetting) }
    var chosenTheme by remember { mutableStateOf(viewModel.selectedTheme) }
    var chosenLang by remember { mutableStateOf(Loc.currentLanguage) }
    var headsetEnabled by remember { mutableStateOf(viewModel.headsetControlsEnabled) }
    var headsetAction by remember { mutableStateOf(viewModel.headsetMultiClickAction) }
    var segmentSource by remember { mutableStateOf(viewModel.segmentSourceSetting) }
    var pauseMultiplier by remember { mutableStateOf(viewModel.practicePauseMultiplierSetting) }
    var silenceSensitivity by remember { mutableStateOf(viewModel.silenceSensitivitySetting) }
    var silenceMinDuration by remember { mutableStateOf(viewModel.silenceMinDurationSetting) }
    var silencePadding by remember { mutableStateOf(viewModel.silencePaddingSetting) }
    var showWaveformDialogInSettings by remember { mutableStateOf(false) }
    val currentPlayingTrack by AudioPlayerManager.currentTrack.collectAsStateWithLifecycle()

    val playbackHistoryList by viewModel.playbackHistory.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedPruneOption by remember { mutableStateOf("ALL") } // "ALL", "LIMIT_1000", "OLDER_YEAR"
    var showFinalConfirm by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    val success = viewModel.exportBackupToJson(outputStream)
                    if (success) {
                        Toast.makeText(context, Loc.getText("backup_success"), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, Loc.getText("backup_failed"), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, Loc.getText("backup_failed"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            try {
                val jsonBytes = context.contentResolver.openInputStream(fileUri)?.use { stream ->
                    stream.readBytes()
                }
                if (jsonBytes != null && jsonBytes.isNotEmpty()) {
                    val offset = if (jsonBytes.size >= 3 &&
                        jsonBytes[0] == 0xEF.toByte() &&
                        jsonBytes[1] == 0xBB.toByte() &&
                        jsonBytes[2] == 0xBF.toByte()
                    ) 3 else 0
                    val jsonString = String(jsonBytes, offset, jsonBytes.size - offset, Charsets.UTF_8)
                    viewModel.restoreBackupFromJsonString(jsonString)
                } else {
                    Toast.makeText(context, Loc.getText("restore_failed"), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, Loc.getText("restore_failed"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(chosenTheme, sliderValue, skipVal, chosenLang) {
        viewModel.updateSettings(chosenTheme, sliderValue.toInt(), skipVal, chosenLang)
    }

    LaunchedEffect(headsetEnabled, headsetAction) {
        viewModel.updateHeadsetSettings(headsetEnabled, headsetAction)
    }

    LaunchedEffect(segmentSource, pauseMultiplier) {
        viewModel.updatePracticeSettings(segmentSource, pauseMultiplier)
    }

    LaunchedEffect(silenceSensitivity, silenceMinDuration, silencePadding) {
        viewModel.updateSilenceSettings(silenceSensitivity, silenceMinDuration, silencePadding)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("btn_settings_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(Loc.getText("settings"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }

        // Threshold slider
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("completion_slider") + ": ${sliderValue.toInt()}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 80f..100f,
                    steps = 20,
                    modifier = Modifier.testTag("threshold_slider")
                )
                Text(Loc.getText("eligible_threshold_note"), fontSize = 10.sp, color = Color.Gray)
            }
        }

        // skip durations
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("skip_duration_label"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    listOf(5, 10, 15, 30).forEach { s ->
                        val selected = skipVal == s
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { skipVal = s }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("${s}s", color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Headset & Media Controls Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_headset_controls"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Loc.getText("headset_controls_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = headsetEnabled,
                        onCheckedChange = { headsetEnabled = it },
                        modifier = Modifier.testTag("switch_headset_controls")
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Loc.getText("headset_controls_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (headsetEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = Loc.getText("headset_single_click_hint"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Loc.getText("headset_double_click_hint"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Loc.getText("headset_triple_click_hint"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "⚡ " + Loc.getText("headset_unplug_pause_hint"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = Loc.getText("headset_double_action_label"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        "NEXT_PREV" to Loc.getText("headset_action_track"),
                        "SKIP_SECONDS" to Loc.getText("headset_action_skip")
                    ).forEach { (actionKey, actionLabel) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { headsetAction = actionKey }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = headsetAction == actionKey,
                                onClick = { headsetAction = actionKey }
                            )
                            Text(
                                text = actionLabel,
                                modifier = Modifier.padding(start = 8.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Practice Mode & Segmentation Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_practice_settings"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = Loc.getText("practice_mode"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                // Segment source selection
                Text(
                    text = Loc.getText("segment_source"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Loc.getText("segment_source_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                listOf(
                    "SILENCE" to Loc.getText("segment_source_silence"),
                    "SUBTITLES" to Loc.getText("segment_source_subtitles"),
                    "MANUAL" to Loc.getText("segment_source_manual")
                ).forEach { (sourceKey, sourceLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { segmentSource = sourceKey }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = segmentSource.equals(sourceKey, ignoreCase = true),
                            onClick = { segmentSource = sourceKey }
                        )
                        Text(
                            text = sourceLabel,
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 12.sp
                        )
                    }
                }

                if (segmentSource.equals("MANUAL", ignoreCase = true)) {
                    val trackForWaveform = currentPlayingTrack
                    if (trackForWaveform != null) {
                        OutlinedButton(
                            onClick = { showWaveformDialogInSettings = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .testTag("btn_open_waveform_editor_settings"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Loc.getText("open_waveform_editor"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            text = Loc.getText("manual_segments_desc"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                // Pause length multiplier setting
                Text(
                    text = Loc.getText("pause_multiplier_title"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Loc.getText("pause_multiplier_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Stepper: Minus button, center chip holding value, Plus button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val next = (Math.round((pauseMultiplier - 0.25f) * 4f) / 4f).coerceIn(0.25f, 4.0f)
                            pauseMultiplier = next
                        },
                        enabled = pauseMultiplier > 0.25f,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("btn_pause_multiplier_decrease")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Decrease multiplier",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .clickable { pauseMultiplier = 1.0f }
                            .testTag("chip_pause_multiplier_value")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.2f", pauseMultiplier) + "x",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    FilledTonalIconButton(
                        onClick = {
                            val next = (Math.round((pauseMultiplier + 0.25f) * 4f) / 4f).coerceIn(0.25f, 4.0f)
                            pauseMultiplier = next
                        },
                        enabled = pauseMultiplier < 4.0f,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("btn_pause_multiplier_increase")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Increase multiplier",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                // Silence Analysis Tuning Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = Loc.getText("silence_analysis_tuning"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = Loc.getText("silence_analysis_tuning_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Word Ending Safety Buffer (Padding)
                Text(
                    text = Loc.getText("silence_padding"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Loc.getText("silence_padding_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        100L to "100ms",
                        200L to "200ms ★",
                        300L to "300ms",
                        400L to "400ms"
                    ).forEach { (paddingMs, label) ->
                        val isSelected = silencePadding == paddingMs
                        FilterChip(
                            selected = isSelected,
                            onClick = { silencePadding = paddingMs },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("chip_silence_padding_${paddingMs}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 2. Minimum Pause Duration
                Text(
                    text = Loc.getText("silence_min_duration"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Loc.getText("silence_min_duration_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        350L to "350ms",
                        500L to "500ms ★",
                        750L to "750ms",
                        1000L to "1.0s"
                    ).forEach { (minMs, label) ->
                        val isSelected = silenceMinDuration == minMs
                        FilterChip(
                            selected = isSelected,
                            onClick = { silenceMinDuration = minMs },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("chip_silence_duration_${minMs}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 3. Detection Sensitivity
                Text(
                    text = Loc.getText("silence_sensitivity"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Loc.getText("silence_sensitivity_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    "HIGH" to Loc.getText("silence_sensitivity_high"),
                    "MEDIUM" to Loc.getText("silence_sensitivity_medium") + " ★",
                    "LOW" to Loc.getText("silence_sensitivity_low")
                ).forEach { (sensKey, sensLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { silenceSensitivity = sensKey }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = silenceSensitivity.equals(sensKey, ignoreCase = true),
                            onClick = { silenceSensitivity = sensKey }
                        )
                        Text(
                            text = sensLabel,
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 12.sp
                        )
                    }
                }

                // 4. Re-analyze Current Track Button
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        Toast.makeText(context, Loc.getText("reanalyzing_with_new_settings"), Toast.LENGTH_SHORT).show()
                        viewModel.reanalyzeCurrentTrackPracticeSegments(context)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("btn_reanalyze_current_track"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Loc.getText("reanalyze_current_track"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // App Theme Selector
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("theme"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                listOf("system" to Loc.getText("system"), "light" to Loc.getText("light"), "dark" to Loc.getText("dark")).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenTheme = key }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = chosenTheme == key, onClick = { chosenTheme = key })
                        Text(label, modifier = Modifier.padding(start = 8.dp), fontSize = 12.sp)
                    }
                }
            }
        }

        // Language Selector
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("app_language"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                listOf("ar" to Loc.getText("ar_label"), "en" to Loc.getText("en_label")).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenLang = key }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = chosenLang == key, onClick = { chosenLang = key })
                        Text(label, modifier = Modifier.padding(start = 8.dp), fontSize = 12.sp)
                    }
                }
            }
        }

        // Gemini AI Assistant Settings Card with Multiple API Keys Management
        var showAddEditDialog by remember { mutableStateOf(false) }
        var editingApiKey by remember { mutableStateOf<AppViewModel.SavedApiKey?>(null) }
        var dialogKeyName by remember { mutableStateOf("") }
        var dialogKeyValue by remember { mutableStateOf("") }
        var dialogKeySetAsActive by remember { mutableStateOf(true) }
        var dialogKeyPasswordVisible by remember { mutableStateOf(false) }
        var keyToDelete by remember { mutableStateOf<AppViewModel.SavedApiKey?>(null) }
        val clipboardManager = LocalClipboardManager.current

        fun maskApiKey(key: String): String {
            val trimmed = key.trim()
            if (trimmed.isBlank()) return "—"
            if (trimmed.length <= 10) return "••••••••"
            return "${trimmed.take(6)}••••${trimmed.takeLast(4)}"
        }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_gemini_ai_settings"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Loc.getText("gemini_ai_assistant"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Loc.getText("custom_gemini_api_key_multi_desc"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Active Key Status Banner
                val activeSavedKey = viewModel.savedApiKeys.firstOrNull { it.id == viewModel.activeApiKeyId }
                if (activeSavedKey != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_api_key_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = Loc.getText("active_key_label") + ":",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = activeSavedKey.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = maskApiKey(activeSavedKey.key),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = Loc.getText("in_use"),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("no_active_api_key_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = Loc.getText("no_api_keys_saved"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // List of Saved Keys (with 1-tap switching)
                if (viewModel.savedApiKeys.isNotEmpty()) {
                    Text(
                        text = Loc.getText("saved_api_keys") + " (${viewModel.savedApiKeys.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        viewModel.savedApiKeys.forEach { item ->
                            val isSelected = item.id == viewModel.activeApiKeyId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (!isSelected) {
                                            viewModel.selectActiveApiKey(item.id)
                                            Toast.makeText(
                                                context,
                                                Loc.getFormattedText("key_switched_success", item.name),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .testTag("saved_api_key_item_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            if (!isSelected) {
                                                viewModel.selectActiveApiKey(item.id)
                                                Toast.makeText(
                                                    context,
                                                    Loc.getFormattedText("key_switched_success", item.name),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.size(24.dp).testTag("radio_api_key_${item.id}")
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.name,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isSelected) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = Loc.getText("active_key_badge"),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = maskApiKey(item.key),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            editingApiKey = item
                                            dialogKeyName = item.name
                                            dialogKeyValue = item.key
                                            dialogKeySetAsActive = isSelected
                                            dialogKeyPasswordVisible = false
                                            showAddEditDialog = true
                                        },
                                        modifier = Modifier.size(32.dp).testTag("btn_edit_api_key_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = Loc.getText("edit_api_key"),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            keyToDelete = item
                                        },
                                        modifier = Modifier.size(32.dp).testTag("btn_delete_api_key_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = Loc.getText("delete_api_key"),
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Key Button
                Button(
                    onClick = {
                        editingApiKey = null
                        dialogKeyName = "Key ${viewModel.savedApiKeys.size + 1}"
                        dialogKeyValue = ""
                        dialogKeySetAsActive = viewModel.savedApiKeys.isEmpty()
                        dialogKeyPasswordVisible = false
                        showAddEditDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("btn_add_new_api_key"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Loc.getText("add_api_key"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Add / Edit API Key Dialog
        if (showAddEditDialog) {
            AlertDialog(
                onDismissRequest = { showAddEditDialog = false },
                title = {
                    Text(
                        text = if (editingApiKey == null) Loc.getText("add_api_key") else Loc.getText("edit_api_key"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = dialogKeyName,
                            onValueChange = { dialogKeyName = it },
                            label = { Text(Loc.getText("key_name_label"), fontSize = 12.sp) },
                            placeholder = { Text(Loc.getText("key_name_placeholder"), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_api_key_name_input")
                        )

                        OutlinedTextField(
                            value = dialogKeyValue,
                            onValueChange = { dialogKeyValue = it },
                            label = { Text(Loc.getText("api_key_value_label"), fontSize = 12.sp) },
                            placeholder = { Text(Loc.getText("api_key_value_placeholder"), fontSize = 12.sp) },
                            singleLine = true,
                            visualTransformation = if (dialogKeyPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val clipText = clipboardManager.getText()?.text
                                            if (!clipText.isNullOrBlank()) {
                                                dialogKeyValue = clipText.trim()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { dialogKeyPasswordVisible = !dialogKeyPasswordVisible },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (dialogKeyPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (dialogKeyPasswordVisible) "Hide key" else "Show key",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_api_key_value_input")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dialogKeySetAsActive = !dialogKeySetAsActive }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = dialogKeySetAsActive,
                                onCheckedChange = { dialogKeySetAsActive = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Loc.getText("set_as_active_key"),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (dialogKeyValue.isBlank()) {
                                Toast.makeText(context, Loc.getText("api_key_cannot_be_empty"), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (editingApiKey == null) {
                                viewModel.addSavedApiKey(dialogKeyName, dialogKeyValue, dialogKeySetAsActive)
                                Toast.makeText(context, Loc.getText("key_added_success"), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateSavedApiKey(editingApiKey!!.id, dialogKeyName, dialogKeyValue)
                                if (dialogKeySetAsActive) {
                                    viewModel.selectActiveApiKey(editingApiKey!!.id)
                                }
                                Toast.makeText(context, Loc.getText("key_updated_success"), Toast.LENGTH_SHORT).show()
                            }
                            showAddEditDialog = false
                        },
                        enabled = dialogKeyValue.isNotBlank()
                    ) {
                        Text(Loc.getText("save"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddEditDialog = false }) {
                        Text(Loc.getText("cancel"))
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        if (keyToDelete != null) {
            AlertDialog(
                onDismissRequest = { keyToDelete = null },
                title = {
                    Text(
                        text = Loc.getText("delete_api_key"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Text(
                        text = Loc.getFormattedText("delete_api_key_confirm", keyToDelete!!.name),
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = keyToDelete
                            if (target != null) {
                                viewModel.deleteSavedApiKey(target.id)
                                Toast.makeText(context, Loc.getText("key_deleted_success"), Toast.LENGTH_SHORT).show()
                            }
                            keyToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(Loc.getText("delete"), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { keyToDelete = null }) {
                        Text(Loc.getText("cancel"))
                    }
                }
            )
        }

        // Clear/Prune History button
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_clear_history"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("clear_history_btn"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(Loc.getText("clear_history_dialog_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.align(Alignment.End).height(38.dp)
                ) {
                    Text(Loc.getText("clear_history_btn").replace("🧹 ", ""), fontSize = 12.sp, color = Color.White)
                }
            }
        }

        // Backup & Restore Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_backup_restore"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("backup_restore_title"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(Loc.getText("backup_restore_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            createDocumentLauncher.launch("hearmark_backup_$timeStamp.json")
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("📤 " + Loc.getText("export_backup_json"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("📥 " + Loc.getText("restore_backup_json"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                Text(Loc.getText("auto_backup_title"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(Loc.getText("auto_backup_desc"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val lastAutoBackupTime = remember { viewModel.getAutoBackupLastModified() }
                if (lastAutoBackupTime != null) {
                    val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastAutoBackupTime))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("ℹ️ " + Loc.getText("latest_auto_snapshot_label") + " $dateFormatted", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        viewModel.restoreLatestAutoBackup { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Text(Loc.getText("restore_auto_snapshot_btn"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showClearDialog = false 
                    showFinalConfirm = false
                },
                title = {
                    Text(
                        text = if (showFinalConfirm) Loc.getText("clear_history_confirm_title") else Loc.getText("clear_history_dialog_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!showFinalConfirm) {
                            Text(
                                text = Loc.getText("clear_history_dialog_desc"),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Export button
                            Button(
                                onClick = { 
                                    viewModel.copyHistoryClipboard(context, playbackHistoryList)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("📋 " + Loc.getText("export_data"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            
                            // Radio option buttons
                            listOf(
                                "ALL" to Loc.getText("clear_history_option_all"),
                                "LIMIT_1000" to Loc.getText("clear_history_option_limit_1000"),
                                "OLDER_YEAR" to Loc.getText("clear_history_option_older_year")
                            ).forEach { (optionKey, optionLabel) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPruneOption = optionKey }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedPruneOption == optionKey,
                                        onClick = { selectedPruneOption = optionKey }
                                    )
                                    Text(
                                        text = optionLabel,
                                        modifier = Modifier.padding(start = 8.dp),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            // Show final confirmation dialog text
                            Text(
                                text = Loc.getText("clear_history_confirm_desc"),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // Summary of what is chosen
                            val summaryText = when (selectedPruneOption) {
                                "ALL" -> Loc.getText("clear_history_option_all")
                                "LIMIT_1000" -> Loc.getText("clear_history_option_limit_1000")
                                else -> Loc.getText("clear_history_option_older_year")
                            }
                            Text(
                                text = "👉 $summaryText",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (!showFinalConfirm) {
                                showFinalConfirm = true
                            } else {
                                // Perform delete action
                                when (selectedPruneOption) {
                                    "ALL" -> viewModel.clearAllPlaybackHistory()
                                    "LIMIT_1000" -> viewModel.prunePlaybackHistoryToRecent1000()
                                    "OLDER_YEAR" -> viewModel.prunePlaybackHistoryOlderThanOneYear()
                                }
                                Toast.makeText(context, Loc.getText("clear_history_success"), Toast.LENGTH_LONG).show()
                                showClearDialog = false
                                showFinalConfirm = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showFinalConfirm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (showFinalConfirm) Loc.getText("delete_completely") else Loc.getText("next"),
                            color = Color.White
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (showFinalConfirm) {
                                showFinalConfirm = false
                            } else {
                                showClearDialog = false
                            }
                        }
                    ) {
                        Text(text = if (showFinalConfirm) Loc.getText("prev") else Loc.getText("cancel"))
                    }
                }
            )
        }

        if (showWaveformDialogInSettings) {
            val trackForWaveform = currentPlayingTrack
            if (trackForWaveform != null) {
                WaveformSegmentEditorDialog(
                    track = trackForWaveform,
                    viewModel = viewModel,
                    onDismiss = { showWaveformDialogInSettings = false }
                )
            }
        }
    }
}

// --- UTILITY FORMATS FOR PLAYBACK SPEED & DURATION ---
fun formatPlaybackSpeed(speed: Float): String {
    val rounded = (Math.round(speed * 100f) / 100f)
    return when {
        Math.abs(rounded - rounded.toInt()) < 0.001f -> "${rounded.toInt()}x"
        Math.abs(rounded * 10f - (rounded * 10f).toInt()) < 0.001f -> String.format(java.util.Locale.US, "%.1fx", rounded)
        else -> String.format(java.util.Locale.US, "%.2fx", rounded)
    }
}

// --- UTILITY FORMATS FOR MILLISECONDS DURATION ---
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
