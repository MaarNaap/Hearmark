package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Lightweight, dedicated audio player for note audio snippets.
 * Completely isolated from the main AudioPlayerManager.
 */
object NoteAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var trackingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playingNoteId = MutableStateFlow<Long?>(null)
    val playingNoteId: StateFlow<Long?> = _playingNoteId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    fun playSnippet(
        context: Context,
        trackFilePath: String,
        noteId: Long,
        startMs: Long,
        endMs: Long? = null
    ) {
        // If clicking the same note snippet that is already playing, pause it (toggle behavior)
        if (_playingNoteId.value == noteId && _isPlaying.value) {
            stop()
            return
        }

        // Pause main player to avoid overlapping audio
        AudioPlayerManager.pause()

        stopInternal()

        try {
            val file = File(trackFilePath)
            val uri = if (file.exists()) {
                Uri.fromFile(file)
            } else {
                Uri.parse(trackFilePath)
            }

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, uri)
                prepare()
                val seekPos = startMs.coerceAtLeast(0L).coerceAtMost(duration.toLong())
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    seekTo(seekPos, MediaPlayer.SEEK_CLOSEST)
                } else {
                    seekTo(seekPos.toInt())
                }
                start()

                setOnCompletionListener {
                    stop()
                }

                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
            }
            mediaPlayer = mp
            _isPlaying.value = true
            _playingNoteId.value = noteId
            _currentPosition.value = startMs

            // Tracking loop to stop playback precisely when endMs is reached
            trackingJob = coroutineScope.launch {
                val effectiveEnd = if (endMs != null && endMs > startMs) endMs else null
                while (isActive) {
                    try {
                        val currentMp = mediaPlayer
                        val isPlaying = try { currentMp?.isPlaying == true } catch (e: Exception) { false }
                        if (currentMp != null && isPlaying) {
                            val pos = try { currentMp.currentPosition.toLong() } catch (e: Exception) { -1L }
                            if (pos >= 0L) {
                                _currentPosition.value = pos
                                if (effectiveEnd != null && pos >= effectiveEnd) {
                                    stop()
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore and continue
                    }
                    delay(40)
                }
            }
        } catch (e: Exception) {
            Log.w("NoteAudioPlayer", "Error playing note snippet", e)
            stop()
        }
    }

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                mp.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
            } else {
                mp.seekTo(positionMs.toInt())
            }
            _currentPosition.value = positionMs
        } catch (e: Exception) {
            Log.w("NoteAudioPlayer", "Error seeking note snippet", e)
        }
    }

    fun stop() {
        stopInternal()
        _playingNoteId.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
    }

    private fun stopInternal() {
        trackingJob?.cancel()
        trackingJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w("NoteAudioPlayer", "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
    }
}
