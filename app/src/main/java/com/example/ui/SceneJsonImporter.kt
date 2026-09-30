package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.AudioTrack
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.util.Locale

data class ParsedSceneItem(
    val sceneNumber: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val summary: String = ""
)

enum class SceneImportMode {
    REPLACE,
    APPEND,
    CREATE_NEW
}

object SceneJsonParser {

    fun parseTimestamp(value: Any?, isExplicitMs: Boolean = false): Long? {
        if (value == null) return null
        when (value) {
            is Number -> {
                val d = value.toDouble()
                return if (d <= 0.0) 0L
                else if (isExplicitMs && d % 1.0 == 0.0) d.toLong()
                else if (d % 1.0 != 0.0 || d < 1000.0) (d * 1000).toLong()
                else d.toLong()
            }
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isEmpty()) return null
                trimmed.toLongOrNull()?.let { return it }
                if (!trimmed.contains(":") && (trimmed.contains(".") || trimmed.contains(","))) {
                    trimmed.replace(',', '.').toDoubleOrNull()?.let { return (it * 1000).toLong() }
                }
                val parts = trimmed.split(":")
                if (parts.size == 3) {
                    val h = parts[0].trim().toLongOrNull() ?: 0L
                    val m = parts[1].trim().toLongOrNull() ?: 0L
                    val sStr = parts[2].trim().replace(',', '.')
                    val sDouble = sStr.toDoubleOrNull() ?: 0.0
                    return (h * 3600000L) + (m * 60000L) + (sDouble * 1000).toLong()
                } else if (parts.size == 2) {
                    val m = parts[0].trim().toLongOrNull() ?: 0L
                    val sStr = parts[1].trim().replace(',', '.')
                    val sDouble = sStr.toDoubleOrNull() ?: 0.0
                    return (m * 60000L) + (sDouble * 1000).toLong()
                }
            }
        }
        return null
    }

    private fun extractTime(obj: JSONObject, msKeys: List<String>, generalKeys: List<String>): Long? {
        for (k in msKeys) {
            if (obj.has(k)) {
                val v = obj.get(k)
                val parsed = parseTimestamp(v, isExplicitMs = true)
                if (parsed != null) return parsed
            }
        }
        for (k in generalKeys) {
            if (obj.has(k)) {
                val v = obj.get(k)
                if (v is Number) {
                    val d = v.toDouble()
                    if (k.contains("sec", ignoreCase = true) || (d > 0 && d <= 7200 && !k.contains("ms", ignoreCase = true))) {
                        return (d * 1000).toLong()
                    }
                }
                val parsed = parseTimestamp(v)
                if (parsed != null) return parsed
            }
        }
        return null
    }

    fun parse(jsonString: String): Result<List<ParsedSceneItem>> {
        return try {
            val cleanBOM = jsonString.trim().removePrefix("\uFEFF")
            val jsonClean = when {
                cleanBOM.startsWith("```json", ignoreCase = true) ->
                    cleanBOM.substringAfter("\n").substringBeforeLast("```").trim()
                cleanBOM.startsWith("```") ->
                    cleanBOM.substringAfter("\n").substringBeforeLast("```").trim()
                else -> cleanBOM
            }

            val tokener = JSONTokener(jsonClean)
            val root = tokener.nextValue()
            val jsonArray = when (root) {
                is JSONArray -> root
                is JSONObject -> {
                    val arrayKey = listOf(
                        "scenes", "virtual_scenes", "virtualScenes", "items",
                        "chapters", "segments", "data", "track_scenes", "trackScenes", "sceneList"
                    ).firstOrNull { root.has(it) && root.optJSONArray(it) != null }
                    if (arrayKey != null) {
                        root.getJSONArray(arrayKey)
                    } else {
                        JSONArray().put(root)
                    }
                }
                else -> return Result.failure(IllegalArgumentException("JSON must be an array or object containing a scenes list."))
            }

            val parsedList = mutableListOf<ParsedSceneItem>()
            for (i in 0 until jsonArray.length()) {
                val itemObj = jsonArray.optJSONObject(i) ?: continue
                val title = listOf("title", "scene_title", "name", "scene_name", "label")
                    .mapNotNull { if (itemObj.has(it)) itemObj.optString(it) else null }
                    .firstOrNull { it.isNotBlank() } ?: "Scene ${i + 1}"

                val summary = listOf("summary", "description", "desc")
                    .mapNotNull { if (itemObj.has(it)) itemObj.optString(it) else null }
                    .firstOrNull { it.isNotBlank() } ?: ""

                val startMs = extractTime(
                    itemObj,
                    listOf("startMs", "start_ms", "startOffsetMs", "start_offset_ms", "startTimeMs"),
                    listOf("startTime", "start_time", "start", "start_seconds", "startSec")
                ) ?: 0L

                val endMs = extractTime(
                    itemObj,
                    listOf("endMs", "end_ms", "endOffsetMs", "end_offset_ms", "endTimeMs"),
                    listOf("endTime", "end_time", "end", "end_seconds", "endSec")
                ) ?: (startMs + 10000L)

                val sceneNum = itemObj.optInt("sceneNumber", itemObj.optInt("scene_number", itemObj.optInt("number", i + 1)))

                parsedList.add(
                    ParsedSceneItem(
                        sceneNumber = sceneNum,
                        title = title.trim(),
                        startMs = startMs.coerceAtLeast(0L),
                        endMs = endMs.coerceAtLeast(startMs + 1000L),
                        summary = summary.trim()
                    )
                )
            }

            if (parsedList.isEmpty()) {
                return Result.failure(IllegalArgumentException("No valid scene objects detected in JSON."))
            }

            val sorted = parsedList.sortedBy { it.startMs }.mapIndexed { index, scene ->
                scene.copy(sceneNumber = index + 1)
            }

            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateAiPromptForTrack(track: AudioTrack, subtitleExcerpt: String? = null): String {
        val durationFormatted = formatTimestampMs(track.duration)
        val subsSection = if (!subtitleExcerpt.isNullOrBlank()) {
            "\n\nHere is the transcript/subtitles of this video for reference:\n---\n${subtitleExcerpt.take(5000)}\n---"
        } else {
            ""
        }
        return """
You are an expert video/audio editor. Your task is to analyze the video "${track.fileName}" (Duration: $durationFormatted, ${track.duration} ms) and divide it into meaningful, contextually complete virtual study scenes.

Please return ONLY a valid, raw JSON array (with no conversational text, no pleasantries, and no markdown other than ```json if needed) strictly adhering to the following structure:

[
  {
    "sceneNumber": 1,
    "title": "Concise Descriptive Scene Title",
    "startMs": 0,
    "endMs": 45000,
    "summary": "Brief explanation of what happens in this scene."
  },
  {
    "sceneNumber": 2,
    "title": "Next Scene Title",
    "startMs": 45000,
    "endMs": 120000,
    "summary": "Brief explanation of what happens in this scene."
  }
]

RULES:
1. "startMs" and "endMs" must be integer timestamps in MILLISECONDS (1 second = 1000 ms). Alternatively, formatted strings like "00:01:15.000" or "01:15" are also supported.
2. "endMs" must be strictly greater than "startMs".
3. Scenes must be chronological, non-overlapping, and cover the key moments of the video.
4. "title" should be concise (3-7 words).
5. Output pure JSON only.$subsSection
""".trimIndent()
    }
}

fun AppViewModel.importScenesFromJson(
    parentTrack: AudioTrack,
    scenes: List<ParsedSceneItem>,
    mode: SceneImportMode,
    onSuccess: (folderId: Long, count: Int) -> Unit = { _, _ -> }
) {
    viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        try {
            val langCode = Loc.currentLanguage
            var activeSubtitleContent = parentTrack.subtitleContent
            if (activeSubtitleContent.isNullOrBlank() && !parentTrack.subtitlePath.isNullOrBlank()) {
                try {
                    val f = File(parentTrack.subtitlePath)
                    if (f.exists()) activeSubtitleContent = f.readText()
                } catch (_: Exception) {}
            }
            if (activeSubtitleContent.isNullOrBlank()) {
                val matching = com.example.player.SubtitleParser.findMatchingSubtitleFile(parentTrack.filePath)
                if (matching != null && matching.exists()) {
                    try {
                        activeSubtitleContent = matching.readText()
                    } catch (_: Exception) {}
                }
            }

            val existingScenes = repository.getScenesForParentTrack(parentTrack.id)
            val existingSceneIds = existingScenes.map { it.id }.toSet()

            // If audio player is currently playing one of the scenes being replaced, safely stop or switch
            withContext(Dispatchers.Main) {
                try {
                    val currentPlaying = AudioPlayerManager.currentTrack.value
                    if (currentPlaying != null && (currentPlaying.id in existingSceneIds || (mode == SceneImportMode.REPLACE && currentPlaying.parentTrackId == parentTrack.id))) {
                        val wasPlaying = AudioPlayerManager.isPlaying.value
                        AudioPlayerManager.pause()
                        if (wasPlaying) {
                            AudioPlayerManager.playTrack(parentTrack)
                        } else {
                            AudioPlayerManager.stop()
                        }
                    }
                    if (existingSceneIds.isNotEmpty()) {
                        AudioPlayerManager.removeTracksByIds(existingSceneIds)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SceneJsonImporter", "Error handling player during scene replacement: ${e.message}")
                }
            }

            var targetFolderId: Long? = null
            var startingSceneNum = 1

            when (mode) {
                SceneImportMode.REPLACE -> {
                    val candidateFolderId = existingScenes.firstOrNull()?.parentFolderId
                    // Validate folder still exists in repository
                    targetFolderId = if (candidateFolderId != null && repository.getFolderById(candidateFolderId) != null) {
                        candidateFolderId
                    } else {
                        null
                    }
                    startingSceneNum = 1
                }
                SceneImportMode.APPEND -> {
                    val candidateFolderId = existingScenes.firstOrNull()?.parentFolderId
                    targetFolderId = if (candidateFolderId != null && repository.getFolderById(candidateFolderId) != null) {
                        candidateFolderId
                    } else {
                        null
                    }
                    startingSceneNum = (existingScenes.maxOfOrNull { it.sceneNumber ?: 0 } ?: 0) + 1
                }
                SceneImportMode.CREATE_NEW -> {
                    targetFolderId = null
                    startingSceneNum = 1
                }
            }

            val baseCleanName = parentTrack.fileName.substringBeforeLast(".")
            val folderName = if (langCode == "ar") "مشاهد - $baseCleanName" else "$baseCleanName - Scenes"

            val folderId = if (targetFolderId != null && targetFolderId != 0L) {
                targetFolderId
            } else {
                val parentDir = File(context.filesDir, "imported")
                val folderDir = File(parentDir, folderName.replace("/", "_").replace(" ", "_"))
                folderDir.mkdirs()
                repository.addFolder(
                    path = folderDir.absolutePath,
                    name = folderName,
                    parentFolderId = parentTrack.parentFolderId
                )
            }

            val newTracks = scenes.mapIndexed { index, item ->
                val sceneNum = startingSceneNum + index
                val formattedNumber = String.format(Locale.US, "%03d", sceneNum)
                val sceneTitle = item.title.ifBlank { "Scene $sceneNum" }
                val virtualFileName = "$formattedNumber - $sceneTitle"
                val sceneDuration = (item.endMs - item.startMs).coerceAtLeast(1000L)

                AudioTrack(
                    filePath = parentTrack.filePath,
                    fileName = virtualFileName,
                    duration = sceneDuration,
                    playCount = 0,
                    lastPosition = 0L,
                    parentFolderId = folderId,
                    isMissing = false,
                    isIndependent = false,
                    listenedSegments = "",
                    subtitlePath = parentTrack.subtitlePath,
                    subtitleContent = activeSubtitleContent,
                    subtitleOffsetMs = parentTrack.subtitleOffsetMs,
                    startOffsetMs = item.startMs,
                    endOffsetMs = item.endMs,
                    isVirtualScene = true,
                    parentTrackId = parentTrack.id,
                    sceneNumber = sceneNum
                )
            }

            // Perform atomic database operation
            if (mode == SceneImportMode.REPLACE) {
                repository.replaceVirtualScenes(parentTrack.id, newTracks)
            } else {
                repository.insertTracks(newTracks)
            }

            withContext(Dispatchers.Main) {
                val successMsg = String.format(Locale.getDefault(), Loc.getText("scenes_imported_success"), scenes.size, folderName)
                Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                onSuccess(folderId, scenes.size)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                val rawErr = e.message ?: "Unknown error"
                val errMsg = try {
                    String.format(Locale.getDefault(), Loc.getText("invalid_json_scenes_file"), rawErr)
                } catch (_: Exception) {
                    "Error importing scenes: $rawErr"
                }
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun ImportScenesJsonDialog(
    parentTrack: AudioTrack,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onImportFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val existingScenesFlow = remember(parentTrack.id) { viewModel.getScenesForTrackFlow(parentTrack.id) }
    val existingScenes by existingScenesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var parsedScenes by remember { mutableStateOf<List<ParsedSceneItem>?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var importMode by remember { mutableStateOf(SceneImportMode.REPLACE) }
    var isImporting by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            selectedFileName = uri.lastPathSegment?.substringAfterLast("/") ?: "scenes.json"
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val jsonString = stream.bufferedReader().use { it.readText() }
                val parseResult = SceneJsonParser.parse(jsonString)
                if (parseResult.isSuccess) {
                    parsedScenes = parseResult.getOrNull()
                    parseError = null
                } else {
                    parsedScenes = null
                    parseError = parseResult.exceptionOrNull()?.message ?: Loc.getText("no_scenes_in_json")
                }
            }
        } catch (e: Exception) {
            parsedScenes = null
            parseError = e.message ?: "Failed to read file"
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FileUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Loc.getText("import_scenes_json_title"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Track information
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = parentTrack.fileName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatTimestampMs(parentTrack.duration)} • ${existingScenes.size} existing scenes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Copy Prompt & Pick File actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val prompt = SceneJsonParser.generateAiPromptForTrack(
                                track = parentTrack,
                                subtitleExcerpt = parentTrack.subtitleContent
                            )
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("AI Scene Prompt", prompt))
                            Toast.makeText(context, Loc.getText("ai_prompt_copied"), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Loc.getText("copy_ai_prompt"),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            filePicker.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Loc.getText("choose_json_file"),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }

                // Error Message if parsing failed
                if (parseError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = parseError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Parsed Scenes Preview & Import Mode Selection
                if (parsedScenes != null) {
                    val scenesList = parsedScenes!!
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.getDefault(), Loc.getText("scenes_parsed_count"), scenesList.size),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (selectedFileName != null) {
                                Text(
                                    text = selectedFileName!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // If existing scenes exist, let user choose REPLACE or APPEND
                        if (existingScenes.isNotEmpty()) {
                            Text(
                                text = Loc.getText("existing_scenes_prompt_title"),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = importMode == SceneImportMode.REPLACE,
                                    onClick = { importMode = SceneImportMode.REPLACE },
                                    label = { Text(Loc.getText("replace_existing_scenes")) },
                                    leadingIcon = {
                                        if (importMode == SceneImportMode.REPLACE) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = importMode == SceneImportMode.APPEND,
                                    onClick = { importMode = SceneImportMode.APPEND },
                                    label = { Text(Loc.getText("append_to_existing_scenes")) },
                                    leadingIcon = {
                                        if (importMode == SceneImportMode.APPEND) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Preview of first few scenes
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(scenesList.take(20)) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${item.sceneNumber}. ${item.title}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${formatTimestampMs(item.startMs)} - ${formatTimestampMs(item.endMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else if (parseError == null) {
                    // Explanatory guidance when no file has been chosen yet
                    Text(
                        text = Loc.getText("import_scenes_json_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                    )
                }
            }
        },
        confirmButton = {
            if (parsedScenes != null) {
                Button(
                    enabled = !isImporting,
                    onClick = {
                        val scenesToImport = parsedScenes ?: return@Button
                        val modeToUse = if (existingScenes.isEmpty()) SceneImportMode.CREATE_NEW else importMode
                        isImporting = true
                        viewModel.importScenesFromJson(
                            parentTrack = parentTrack,
                            scenes = scenesToImport,
                            mode = modeToUse,
                            onSuccess = { _, _ ->
                                isImporting = false
                                onImportFinished()
                                onDismiss()
                            }
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Loc.getText("loading"))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Loc.getText("confirm_import_btn"))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isImporting,
                onClick = onDismiss
            ) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}
