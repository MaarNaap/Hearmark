package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.AudioPlayerManager

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

        // 1. Playback & Completion Thresholds Card
        PlaybackSettingsCard(
            sliderValue = sliderValue,
            onSliderValueChange = { sliderValue = it },
            skipVal = skipVal,
            onSkipValChange = { skipVal = it },
            voiceBoostLevel = viewModel.voiceBoostLevel,
            onVoiceBoostChange = { viewModel.updateVoiceBoost(it) }
        )

        // 2. Headset & Media Controls Card
        HeadsetControlsCard(
            headsetEnabled = headsetEnabled,
            onHeadsetEnabledChange = { headsetEnabled = it },
            headsetAction = headsetAction,
            onHeadsetActionChange = { headsetAction = it }
        )

        // 3. Practice Mode & Segmentation Card
        PracticeSettingsCard(
            context = context,
            viewModel = viewModel,
            currentPlayingTrack = currentPlayingTrack,
            segmentSource = segmentSource,
            onSegmentSourceChange = { segmentSource = it },
            pauseMultiplier = pauseMultiplier,
            onPauseMultiplierChange = { pauseMultiplier = it },
            silencePadding = silencePadding,
            onSilencePaddingChange = { silencePadding = it },
            silenceMinDuration = silenceMinDuration,
            onSilenceMinDurationChange = { silenceMinDuration = it },
            silenceSensitivity = silenceSensitivity,
            onSilenceSensitivityChange = { silenceSensitivity = it },
            onOpenWaveformEditor = { showWaveformDialogInSettings = true }
        )

        // 4. Appearance & Language Card
        AppearanceSettingsCard(
            chosenTheme = chosenTheme,
            onThemeChange = { chosenTheme = it },
            chosenLang = chosenLang,
            onLangChange = { chosenLang = it }
        )

        // 5. Gemini AI Settings Card
        GeminiSettingsCard(
            viewModel = viewModel,
            context = context
        )

        // 6. Data Management Card (Clear History, Backup/Restore, Relink)
        DataManagementCard(
            viewModel = viewModel,
            context = context,
            playbackHistoryList = playbackHistoryList
        )

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

        if (viewModel.showRelinkDialog.value) {
            com.example.ui.dialogs.RelinkFilesAndStatsDialog(
                candidates = viewModel.relinkCandidates.value,
                isLoading = viewModel.isScanningForRelink.value,
                onDismissRequest = { viewModel.showRelinkDialog.value = false },
                onConfirmRelink = { selected ->
                    viewModel.applyRelinkCandidates(selected)
                }
            )
        }
    }
}
