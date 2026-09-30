package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.data.AudioTrack
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun AppViewModel.updateSettings(theme: String, threshold: Int, skipSeconds: Int, lang: String) {
    selectedTheme = theme
    thresholdSetting = threshold
    skipSecondsSetting = skipSeconds
    Loc.currentLanguage = lang

    val sharedPref = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
        putString("theme", theme)
        putInt("threshold", threshold)
        putInt("skip_seconds", skipSeconds)
        putString("language", lang)
        apply()
    }

    AudioPlayerManager.setSettings(threshold, skipSeconds)
    AudioPlayerManager.updateNotification()
}

fun AppViewModel.updateHeadsetSettings(enabled: Boolean, action: String) {
    headsetControlsEnabled = enabled
    headsetMultiClickAction = action
    AudioPlayerManager.setHeadsetSettings(enabled, action)
}

fun AppViewModel.updatePracticeSettings(source: String, multiplier: Float) {
    segmentSourceSetting = source
    practicePauseMultiplierSetting = multiplier
    val sharedPref = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
        putString("segment_source", source)
        putFloat("practice_pause_multiplier", multiplier)
        apply()
    }
    AudioPlayerManager.setPracticeSettings(source, multiplier)
}

fun AppViewModel.updateSilenceSettings(sensitivity: String, minDurationMs: Long, paddingMs: Long) {
    silenceSensitivitySetting = sensitivity
    silenceMinDurationSetting = minDurationMs
    silencePaddingSetting = paddingMs
    val sharedPref = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
        putString("silence_sensitivity", sensitivity)
        putLong("silence_min_duration", minDurationMs)
        putLong("silence_padding", paddingMs)
        apply()
    }
    AudioPlayerManager.setSilenceSettings(sensitivity, minDurationMs, paddingMs)
}

fun AppViewModel.reanalyzeCurrentTrackPracticeSegments(context: Context) {
    AudioPlayerManager.reanalyzePracticeSegments(context, repository, silent = false)
}

fun AppViewModel.saveManualPracticeSegments(track: AudioTrack, boundaries: List<Long>, context: Context, autoEnable: Boolean = true) {
    AudioPlayerManager.saveManualPracticeSegments(context, track, boundaries, autoEnable)
}

fun AppViewModel.startPracticeWithSource(track: AudioTrack, source: String, multiplier: Float, context: Context) {
    updatePracticeSettings(source, multiplier)
    AudioPlayerManager.applyPracticeSettingsAndStart(track, source, multiplier, context, repository)
}

fun AppViewModel.resetTrackSegments(track: AudioTrack) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.updateTrackPracticeSegments(track.id, null)
        val updated = track.copy(practiceSegments = null)
        repository.dao.updateTrack(updated)
        withContext(Dispatchers.Main) {
            if (AudioPlayerManager.currentTrack.value?.id == track.id) {
                AudioPlayerManager.clearPracticeSegmentsForCurrentTrack()
            }
        }
    }
}
