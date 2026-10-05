package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.AudioPlayerManager
import com.example.player.SubtitleCue
import com.example.player.SubtitleParser
import java.util.Locale

data class SyncLineItem(
    val id: Int,
    val text: String,
    val startMs: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSubtitleSyncDialog(
    initialCues: List<SubtitleCue>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val isPlayingState by AudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    val currentPositionState by AudioPlayerManager.currentPosition.collectAsStateWithLifecycle()
    val durationState by AudioPlayerManager.duration.collectAsStateWithLifecycle()
    val speedState by AudioPlayerManager.playbackSpeed.collectAsStateWithLifecycle()

    // Initialize lines from existing cues or empty
    var syncLines by remember {
        mutableStateOf<List<SyncLineItem>>(
            if (initialCues.isNotEmpty()) {
                initialCues.mapIndexed { idx, cue ->
                    SyncLineItem(
                        id = idx + 1,
                        text = cue.text,
                        startMs = if (cue.isTimed && cue.startMs >= 0L) cue.startMs else null
                    )
                }
            } else {
                emptyList()
            }
        )
    }

    var rawInputText by remember { mutableStateOf("") }
    var currentFocusIndex by remember {
        val firstUntimed = syncLines.indexOfFirst { it.startMs == null }
        mutableIntStateOf(if (firstUntimed >= 0) firstUntimed else 0)
    }

    var editingLineItem by remember { mutableStateOf<Pair<Int, String>?>(null) }

    val timedCount = syncLines.count { it.startMs != null }
    val totalCount = syncLines.size
    val progressPercent = if (totalCount > 0) ((timedCount.toFloat() / totalCount.toFloat()) * 100).toInt() else 0

    // Auto-scroll when focus changes
    LaunchedEffect(currentFocusIndex) {
        if (syncLines.isNotEmpty() && currentFocusIndex in syncLines.indices) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val targetOffset = if (viewportHeight > 0) -(viewportHeight * 0.30f).toInt() else 0
            listState.animateScrollToItem(index = currentFocusIndex, scrollOffset = targetOffset)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Top App Bar
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Loc.getText("cancel"))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = Loc.getText("tap_to_sync_title"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (syncLines.isNotEmpty()) {
                                    Text(
                                        text = Loc.getFormattedText("sync_progress", timedCount, totalCount, progressPercent),
                                        fontSize = 11.sp,
                                        color = if (timedCount == totalCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (syncLines.isEmpty()) {
                                    Toast.makeText(context, Loc.getText("paste_lines_prompt"), Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val formattedLrc = SubtitleParser.formatLinesToLrc(
                                    syncLines.map { Pair(it.startMs, it.text) }
                                )
                                onSave(formattedLrc)
                                Toast.makeText(context, Loc.getText("subtitles_saved_success"), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            enabled = syncLines.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Loc.getText("save"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Main Body: Paste setup view or sync list view
                if (syncLines.isEmpty()) {
                    // Empty state / Paste new lines to start
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(Loc.getText("tap_to_sync"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(Loc.getText("tap_to_sync_desc"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Text(
                            text = Loc.getText("paste_lines_prompt"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = rawInputText,
                            onValueChange = { rawInputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            placeholder = { Text("Line 1...\nLine 2...\nLine 3...") },
                            maxLines = 50
                        )

                        Button(
                            onClick = {
                                val splitLines = rawInputText.lines()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                if (splitLines.isNotEmpty()) {
                                    syncLines = splitLines.mapIndexed { idx, line ->
                                        SyncLineItem(id = idx + 1, text = line, startMs = null)
                                    }
                                    currentFocusIndex = 0
                                } else {
                                    Toast.makeText(context, Loc.getText("paste_lines_prompt"), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = rawInputText.isNotBlank()
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Loc.getText("start_syncing"), fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Sync List View
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(syncLines, key = { index, item -> item.id }) { index, item ->
                                val isFocused = currentFocusIndex == index
                                val isTimed = item.startMs != null && item.startMs >= 0L
                                val itemBgColor = when {
                                    isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                    isTimed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                                val borderColor = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isTimed) {
                                                item.startMs?.let { AudioPlayerManager.seekTo(it, isPhysicalTimestamp = true) }
                                            }
                                            currentFocusIndex = index
                                        },
                                    colors = CardDefaults.cardColors(containerColor = itemBgColor),
                                    border = BorderStroke(width = 1.dp, color = borderColor),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Header Row: Line number & Timestamp badge (start) + Action buttons (end)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            // Left side: Line Number index + Timestamp pill
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                ) {
                                                    Text(
                                                        text = "#${item.id}",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                // Timestamp pill / badge (Click to seek if timed, or set to current time)
                                                Surface(
                                                    color = if (isTimed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(0.5.dp, if (isTimed) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.clickable {
                                                        if (isTimed) {
                                                            item.startMs?.let { AudioPlayerManager.seekTo(it, isPhysicalTimestamp = true) }
                                                        } else {
                                                            val now = AudioPlayerManager.currentPosition.value
                                                            syncLines = syncLines.mapIndexed { idx, old ->
                                                                if (idx == index) old.copy(startMs = now) else old
                                                            }
                                                            currentFocusIndex = (index + 1).coerceAtMost(syncLines.size - 1)
                                                        }
                                                    }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isTimed) Icons.Filled.PlayArrow else Icons.Filled.AccessTime,
                                                            contentDescription = null,
                                                            tint = if (isTimed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = if (isTimed) item.startMs?.let { SubtitleParser.formatTimestampTag(it) } ?: "--:--" else "--:--",
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isTimed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            // Right side: Edit line text + Clear timing buttons
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // Edit text button
                                                IconButton(
                                                    onClick = { editingLineItem = Pair(index, item.text) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Edit,
                                                        contentDescription = Loc.getText("edit_subtitles"),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // Clear/Delete timing button (only if timed)
                                                if (isTimed) {
                                                    IconButton(
                                                        onClick = {
                                                            syncLines = syncLines.mapIndexed { idx, old ->
                                                                if (idx == index) old.copy(startMs = null) else old
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Close,
                                                            contentDescription = Loc.getText("clear_line_time"),
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Subtitle text taking full width across the card
                                        Text(
                                            text = item.text,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 22.sp,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sticky Bottom Controller & Big Tap Button
                    Surface(
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Progress bar & Elapsed time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SubtitleParser.formatTimestampTag(currentPositionState),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    text = "${Loc.getText("line_number")} ${currentFocusIndex + 1} / ${syncLines.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = SubtitleParser.formatShortTimeTag(durationState),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Interactive Audio Control Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Speed cycle button (0.75x -> 1.0x -> 1.25x -> 1.5x)
                                OutlinedButton(
                                    onClick = {
                                        val nextSpeed = when {
                                            speedState < 0.9f -> 1.0f
                                            speedState < 1.15f -> 1.25f
                                            speedState < 1.4f -> 1.5f
                                            else -> 0.75f
                                        }
                                        AudioPlayerManager.setSpeed(nextSpeed)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(formatPlaybackSpeed(speedState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // Skip -5s
                                FilledTonalIconButton(
                                    onClick = { AudioPlayerManager.skipBackward() },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(Icons.Filled.FastRewind, contentDescription = "Rewind", modifier = Modifier.size(20.dp))
                                }

                                // Play/Pause
                                FilledIconButton(
                                    onClick = {
                                        if (isPlayingState) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                                    },
                                    modifier = Modifier.size(48.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Skip +5s
                                FilledTonalIconButton(
                                    onClick = { AudioPlayerManager.skipForward() },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(Icons.Filled.FastForward, contentDescription = "Fast Forward", modifier = Modifier.size(20.dp))
                                }

                                // Previous line
                                IconButton(
                                    onClick = {
                                        if (currentFocusIndex > 0) {
                                            currentFocusIndex--
                                        }
                                    },
                                    enabled = currentFocusIndex > 0
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Prev Line")
                                }
                            }

                            // PRIMARY BIG TAP BUTTON: Timestamp current line & auto advance!
                            Button(
                                onClick = {
                                    val now = AudioPlayerManager.currentPosition.value
                                    syncLines = syncLines.mapIndexed { idx, old ->
                                        if (idx == currentFocusIndex) old.copy(startMs = now) else old
                                    }
                                    if (currentFocusIndex < syncLines.size - 1) {
                                        currentFocusIndex++
                                    } else {
                                        Toast.makeText(context, Loc.getText("all_lines_synced"), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${Loc.getText("timestamp_and_next")}  ${SubtitleParser.formatTimestampTag(currentPositionState)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Secondary helpers row: Re-time prev line / Skip to next
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = {
                                        if (currentFocusIndex > 0) {
                                            val prevIdx = currentFocusIndex - 1
                                            val now = AudioPlayerManager.currentPosition.value
                                            syncLines = syncLines.mapIndexed { idx, old ->
                                                if (idx == prevIdx) old.copy(startMs = now) else old
                                            }
                                        }
                                    },
                                    enabled = currentFocusIndex > 0
                                ) {
                                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(Loc.getText("retime_prev_line"), fontSize = 12.sp)
                                }

                                TextButton(
                                    onClick = {
                                        if (currentFocusIndex < syncLines.size - 1) {
                                            currentFocusIndex++
                                        }
                                    },
                                    enabled = currentFocusIndex < syncLines.size - 1
                                ) {
                                    Text(Loc.getText("next"), fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Line text edit dialog (if user needs to fix a typo in a line)
    editingLineItem?.let { (index, text) ->
        var editedText by remember(text) { mutableStateOf(text) }
        AlertDialog(
            onDismissRequest = { editingLineItem = null },
            title = { Text(Loc.getText("edit_subtitles")) },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        syncLines = syncLines.mapIndexed { idx, old ->
                            if (idx == index) old.copy(text = editedText.trim()) else old
                        }
                        editingLineItem = null
                    }
                ) {
                    Text(Loc.getText("save_changes"))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLineItem = null }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }
}
