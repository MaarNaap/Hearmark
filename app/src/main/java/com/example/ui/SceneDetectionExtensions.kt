package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiService
import com.example.data.AudioTrack
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

fun AppViewModel.cancelSceneDetection() {
    sceneDetectionJob?.cancel()
    sceneDetectionJob = null
    isDetectingScenes.value = false
    detectingTrackName.value = null
    sceneDetectionStatus.value = null
}

fun AppViewModel.startAiSceneDetection(track: AudioTrack, onSuccess: ((folderId: Long, sceneCount: Int) -> Unit)? = null) {
    sceneDetectionJob?.cancel()
    sceneDetectionJob = viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        try {
            isDetectingScenes.value = true
            detectingTrackName.value = track.fileName
            sceneDetectionStatus.value = Loc.getText("ai_scene_extracting_subtitles")

            // 1. Get subtitle cues
            var cues: List<com.example.player.SubtitleCue> = emptyList()
            var activeSubtitleContent = track.subtitleContent

            if (!track.subtitleContent.isNullOrBlank()) {
                cues = com.example.player.SubtitleParser.parseContent(track.subtitleContent, track.subtitleOffsetMs)
            } else if (!track.subtitlePath.isNullOrBlank()) {
                cues = com.example.player.SubtitleParser.parseFile(File(track.subtitlePath), track.subtitleOffsetMs)
            } else {
                val matching = com.example.player.SubtitleParser.findMatchingSubtitleFile(track.filePath)
                if (matching != null && matching.exists()) {
                    cues = com.example.player.SubtitleParser.parseFile(matching, track.subtitleOffsetMs)
                }
            }

            // If no subtitles exist yet, automatically generate them with Gemini AI from the audio or video file!
            if (cues.isEmpty()) {
                val mediaFile = File(track.filePath)
                if (mediaFile.exists() && mediaFile.canRead()) {
                    sceneDetectionStatus.value = Loc.getText("ai_scene_generating_subtitles_first")
                    val subResult = GeminiService.generateSubtitles(
                        audioFile = mediaFile,
                        existingSubtitleText = null,
                        totalDurationMs = if (track.duration > 0) track.duration else GeminiService.getMediaDurationMs(mediaFile),
                        customApiKey = customGeminiApiKey,
                        language = Loc.currentLanguage,
                        onProgressUpdate = { progressText ->
                            sceneDetectionStatus.value = progressText
                        }
                    )
                    val generatedSrt = subResult.getOrNull()
                    if (!generatedSrt.isNullOrBlank()) {
                        activeSubtitleContent = generatedSrt
                        AudioPlayerManager.setSubtitleContentForTrack(track.id, generatedSrt)
                        cues = com.example.player.SubtitleParser.parseContent(generatedSrt, track.subtitleOffsetMs)
                    } else if (subResult.isFailure) {
                        val subError = subResult.exceptionOrNull()?.message ?: Loc.getText("ai_scene_no_subtitles_error")
                        withContext(Dispatchers.Main) {
                            isDetectingScenes.value = false
                            detectingTrackName.value = null
                            sceneDetectionStatus.value = null
                            Toast.makeText(context, "${Loc.getText("ai_scene_detection_failed")}: $subError", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }
            }

            if (cues.isEmpty()) {
                withContext(Dispatchers.Main) {
                    isDetectingScenes.value = false
                    detectingTrackName.value = null
                    sceneDetectionStatus.value = null
                    Toast.makeText(context, Loc.getText("ai_scene_no_subtitles_error"), Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // 2. Call Gemini
            sceneDetectionStatus.value = Loc.getText("ai_scene_analyzing")
            val totalDuration = if (track.duration > 0) track.duration else (cues.lastOrNull()?.endMs ?: 60000L)
            val langCode = Loc.currentLanguage
            val result = GeminiService.detectScenes(
                transcriptCues = cues,
                totalDurationMs = totalDuration,
                mediaTitle = track.fileName,
                customApiKey = customGeminiApiKey,
                language = langCode,
                onProgressUpdate = { progressText ->
                    sceneDetectionStatus.value = progressText
                }
            )

            val detectedScenes = result.getOrNull()
            if (detectedScenes.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    isDetectingScenes.value = false
                    detectingTrackName.value = null
                    sceneDetectionStatus.value = null
                    val errMsg = result.exceptionOrNull()?.message ?: Loc.getText("ai_scene_detection_failed")
                    Toast.makeText(context, "${Loc.getText("ai_scene_detection_failed")}: $errMsg", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // 3. Create Scenes Folder
            sceneDetectionStatus.value = Loc.getText("ai_scene_saving")
            val baseCleanName = track.fileName.substringBeforeLast(".")
            val folderName = if (langCode == "ar") "مشاهد - $baseCleanName" else "$baseCleanName - Scenes"

            val parentDir = File(context.filesDir, "imported")
            val folderDir = File(parentDir, folderName.replace("/", "_").replace(" ", "_"))
            folderDir.mkdirs()
            val folderPath = folderDir.absolutePath

            val folderId = repository.addFolder(
                path = folderPath,
                name = folderName,
                parentFolderId = track.parentFolderId
            )

            // 4. Insert virtual AudioTrack for each scene
            detectedScenes.forEachIndexed { index, scene ->
                val sceneNum = index + 1
                val formattedNumber = String.format(Locale.US, "%03d", sceneNum)
                val sceneTitle = scene.title.ifBlank { "Scene $sceneNum" }
                val virtualFileName = "$formattedNumber - $sceneTitle"
                val sceneDuration = (scene.endMs - scene.startMs).coerceAtLeast(1000L)

                val virtualTrack = AudioTrack(
                    filePath = track.filePath,
                    fileName = virtualFileName,
                    duration = sceneDuration,
                    playCount = 0,
                    lastPosition = 0L,
                    parentFolderId = folderId,
                    isMissing = false,
                    isIndependent = false,
                    listenedSegments = "",
                    subtitlePath = track.subtitlePath,
                    subtitleContent = activeSubtitleContent,
                    subtitleOffsetMs = track.subtitleOffsetMs,
                    startOffsetMs = scene.startMs,
                    endOffsetMs = scene.endMs,
                    isVirtualScene = true,
                    parentTrackId = track.id,
                    sceneNumber = sceneNum
                )
                repository.insertTrack(virtualTrack)
            }

            withContext(Dispatchers.Main) {
                isDetectingScenes.value = false
                detectingTrackName.value = null
                sceneDetectionStatus.value = null
                val successMsg = String.format(Locale.getDefault(), Loc.getText("ai_scenes_created_success"), detectedScenes.size, folderName)
                Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                onSuccess?.invoke(folderId, detectedScenes.size)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            withContext(Dispatchers.Main) {
                isDetectingScenes.value = false
                detectingTrackName.value = null
                sceneDetectionStatus.value = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                isDetectingScenes.value = false
                detectingTrackName.value = null
                sceneDetectionStatus.value = null
                Toast.makeText(context, "${Loc.getText("ai_scene_detection_failed")}: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

fun AppViewModel.getScenesForTrackFlow(parentTrackId: Long): Flow<List<AudioTrack>> {
    return repository.getScenesForParentTrackFlow(parentTrackId)
}

fun AppViewModel.updateVirtualScene(
    track: AudioTrack,
    newTitle: String,
    newStartOffsetMs: Long,
    newEndOffsetMs: Long,
    onSuccess: (() -> Unit)? = null
) {
    viewModelScope.launch(Dispatchers.IO) {
        val durationMs = (newEndOffsetMs - newStartOffsetMs).coerceAtLeast(1000L)
        val updated = track.copy(
            fileName = newTitle.trim().ifBlank { track.fileName },
            startOffsetMs = newStartOffsetMs.coerceAtLeast(0L),
            endOffsetMs = newEndOffsetMs,
            duration = durationMs
        )
        repository.updateTrack(updated)

        // If currently playing, update in player
        if (AudioPlayerManager.currentTrack.value?.id == track.id) {
            withContext(Dispatchers.Main) {
                AudioPlayerManager.playTrack(updated)
            }
        }

        withContext(Dispatchers.Main) {
            val context = getApplication<Application>()
            Toast.makeText(context, Loc.getText("scene_updated_success"), Toast.LENGTH_SHORT).show()
            onSuccess?.invoke()
        }
    }
}

fun AppViewModel.deleteVirtualScene(track: AudioTrack, onSuccess: (() -> Unit)? = null) {
    viewModelScope.launch(Dispatchers.IO) {
        // If currently playing, stop
        if (AudioPlayerManager.currentTrack.value?.id == track.id) {
            withContext(Dispatchers.Main) {
                AudioPlayerManager.pause()
            }
        }
        repository.deleteTrack(track)
        withContext(Dispatchers.Main) {
            val context = getApplication<Application>()
            Toast.makeText(context, Loc.getText("scene_deleted_success"), Toast.LENGTH_SHORT).show()
            onSuccess?.invoke()
        }
    }
}

fun AppViewModel.addNewVirtualScene(
    parentTrack: AudioTrack,
    folderId: Long?,
    title: String,
    startOffsetMs: Long,
    endOffsetMs: Long,
    onSuccess: (() -> Unit)? = null
) {
    viewModelScope.launch(Dispatchers.IO) {
        val sceneDuration = (endOffsetMs - startOffsetMs).coerceAtLeast(1000L)
        val existingScenes = repository.getScenesForParentTrack(parentTrack.id)
        val nextSceneNumber = (existingScenes.maxOfOrNull { it.sceneNumber ?: 0 } ?: 0) + 1
        val formattedNumber = String.format(Locale.US, "%03d", nextSceneNumber)
        val cleanTitle = title.trim().ifBlank { "Scene $nextSceneNumber" }
        val virtualFileName = "$formattedNumber - $cleanTitle"

        val virtualTrack = AudioTrack(
            filePath = parentTrack.filePath,
            fileName = virtualFileName,
            duration = sceneDuration,
            playCount = 0,
            lastPosition = 0L,
            parentFolderId = folderId ?: parentTrack.parentFolderId,
            isMissing = false,
            isIndependent = false,
            listenedSegments = "",
            subtitlePath = parentTrack.subtitlePath,
            subtitleContent = parentTrack.subtitleContent,
            subtitleOffsetMs = parentTrack.subtitleOffsetMs,
            startOffsetMs = startOffsetMs.coerceAtLeast(0L),
            endOffsetMs = endOffsetMs,
            isVirtualScene = true,
            parentTrackId = parentTrack.id,
            sceneNumber = nextSceneNumber
        )
        repository.insertTrack(virtualTrack)

        withContext(Dispatchers.Main) {
            val context = getApplication<Application>()
            Toast.makeText(context, Loc.getText("scene_created_success"), Toast.LENGTH_SHORT).show()
            onSuccess?.invoke()
        }
    }
}
