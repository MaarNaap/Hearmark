package com.example.player

import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object VideoSurfaceController {

    fun toggleFocusMode(): Boolean {
        with(AudioPlayerManager) {
            val next = !isFocusMode.value
            setFocusMode(next)
            return next
        }
    }

    fun setFocusMode(enabled: Boolean) {
        with(AudioPlayerManager) {
            isFocusMode.value = enabled
            if (!enabled && _isPracticeMode.value) {
                stopPracticeMode()
            }
        }
    }

    fun toggleVideoFullWidth(): Boolean {
        with(AudioPlayerManager) {
            isVideoFullWidth.value = true
            return true
        }
    }

    fun setVideoFullWidth(enabled: Boolean) {
        with(AudioPlayerManager) {
            isVideoFullWidth.value = true
        }
    }

    fun toggleVideoSubtitleMode(): AudioPlayerManager.VideoSubtitleMode {
        with(AudioPlayerManager) {
            val next = when (videoSubtitleMode.value) {
                AudioPlayerManager.VideoSubtitleMode.SHOW -> AudioPlayerManager.VideoSubtitleMode.HIDE
                AudioPlayerManager.VideoSubtitleMode.HIDE -> AudioPlayerManager.VideoSubtitleMode.BLACK
                AudioPlayerManager.VideoSubtitleMode.BLACK -> AudioPlayerManager.VideoSubtitleMode.SHOW
            }
            videoSubtitleMode.value = next
            return next
        }
    }

    fun setInPipMode(inPip: Boolean) {
        with(AudioPlayerManager) {
            _isInPipMode.value = inPip
        }
    }

    fun attachSurface(surface: android.view.Surface?) {
        with(AudioPlayerManager) {
            activeSurface = surface
            try {
                mediaPlayer?.let { mp ->
                    mp.setSurface(surface)
                    if (surface != null && surface.isValid) {
                        try {
                            val currentPos = mp.currentPosition
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mp.seekTo(currentPos.toLong(), MediaPlayer.SEEK_CLOSEST)
                            } else {
                                mp.seekTo(currentPos)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Frame resync after surface attach: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching surface: ${e.message}")
            }
        }
    }

    fun attachSurfaceHolder(holder: android.view.SurfaceHolder?) {
        with(AudioPlayerManager) {
            activeSurfaceHolder = holder
            try {
                mediaPlayer?.setDisplay(holder)
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching surface holder: ${e.message}")
            }
        }
    }

    fun setPracticeSettings(source: String, multiplier: Float) {
        with(AudioPlayerManager) {
            val oldSource = segmentSource
            segmentSource = when (source.uppercase()) {
                "SUBTITLES" -> "SUBTITLES"
                "MANUAL" -> "MANUAL"
                else -> "SILENCE"
            }
            val coerced = multiplier.coerceIn(0.25f, 4.0f)
            practicePauseMultiplier = coerced
            _practicePauseMultiplierFlow.value = coerced

            if (!oldSource.equals(segmentSource, ignoreCase = true)) {
                val track = currentTrackValue
                val repo = repository
                val ctx = appContext
                if (track != null && repo != null && ctx != null) {
                    if (_isPracticeMode.value || !track.practiceSegments.isNullOrBlank()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            reanalyzePracticeSegmentsInternal(track, ctx, repo, silent = true)
                        }
                    }
                }
            }
        }
    }

    fun setSilenceSettings(sensitivity: String, minDurationMs: Long, paddingMs: Long) {
        with(AudioPlayerManager) {
            val changed = (silenceSensitivity != sensitivity) || (silenceMinDurationMs != minDurationMs) || (silencePaddingMs != paddingMs)
            silenceSensitivity = sensitivity
            silenceMinDurationMs = minDurationMs
            silencePaddingMs = paddingMs

            if (changed && segmentSource == "SILENCE") {
                val track = currentTrackValue
                val repo = repository
                val ctx = appContext
                if (track != null && repo != null && ctx != null) {
                    if (_isPracticeMode.value || !track.practiceSegments.isNullOrBlank()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            reanalyzePracticeSegmentsInternal(track, ctx, repo, silent = true)
                        }
                    }
                }
            }
        }
    }
}
