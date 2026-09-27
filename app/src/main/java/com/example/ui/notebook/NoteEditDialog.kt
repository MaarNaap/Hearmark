package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Note
import com.example.player.AudioPlayerManager
import com.example.player.NoteAudioPlayer
import com.example.player.SubtitleCue
import com.example.player.SubtitleParser
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteModal(
    initialNote: Note? = null,
    prefilledText: String = "",
    prefilledComment: String = "",
    prefilledTrackId: Long? = null,
    prefilledStartMs: Long = 0L,
    prefilledEndMs: Long = 0L,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val existingTags by viewModel.noteTags.collectAsStateWithLifecycle()
    val allNotes by viewModel.notes.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isSnippetPlaying by NoteAudioPlayer.isPlaying.collectAsStateWithLifecycle()
    val playingSnippetNoteId by NoteAudioPlayer.playingNoteId.collectAsStateWithLifecycle()
    val isCurrentSnippetPlaying = isSnippetPlaying && playingSnippetNoteId == -1L

    DisposableEffect(Unit) {
        onDispose {
            if (NoteAudioPlayer.playingNoteId.value == -1L) {
                NoteAudioPlayer.stop()
            }
        }
    }

    val favTag = Loc.getText("favorite_tag_name")
    val isFavoriteTag: (String) -> Boolean = remember(favTag) {
        { tag -> tag.equals("favorite", ignoreCase = true) || tag == "المفضلة" || tag.equals(favTag, ignoreCase = true) }
    }

    val availableTags = remember(allNotes, existingTags, favTag) {
        (allNotes.flatMap { it.getTagsList() } + existingTags.map { it.name })
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isFavoriteTag(it) }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    var noteText by remember {
        mutableStateOf(initialNote?.text ?: prefilledText)
    }
    var commentText by remember {
        mutableStateOf(initialNote?.comment ?: prefilledComment)
    }
    var selectedTrackId by remember {
        mutableStateOf(initialNote?.trackId ?: prefilledTrackId)
    }
    val selectedTrack = remember(selectedTrackId, allTracks) {
        allTracks.find { it.id == selectedTrackId }
    }
    var startMs by remember {
        val initialStart = initialNote?.startTimestampMs ?: prefilledStartMs
        val adjustedStart = if (selectedTrack?.isVirtualScene == true && initialStart > 0 && initialStart < selectedTrack.startOffsetMs) {
            selectedTrack.startOffsetMs + initialStart
        } else if (selectedTrack?.isVirtualScene == true && initialStart <= 0) {
            selectedTrack.startOffsetMs
        } else {
            initialStart
        }
        mutableLongStateOf(adjustedStart.coerceAtLeast(0L))
    }
    var endMs by remember {
        val initialEnd = initialNote?.endTimestampMs ?: if (prefilledEndMs > prefilledStartMs) prefilledEndMs else (prefilledStartMs + 5000L)
        val adjustedEnd = if (selectedTrack?.isVirtualScene == true && initialEnd > 0 && initialEnd < selectedTrack.startOffsetMs) {
            selectedTrack.startOffsetMs + initialEnd
        } else if (selectedTrack?.isVirtualScene == true && initialEnd <= 0) {
            selectedTrack.startOffsetMs + 5000L
        } else {
            initialEnd
        }
        mutableLongStateOf(adjustedEnd.coerceAtLeast(startMs))
    }

    var selectedTags by remember(initialNote, favTag) {
        val tags = initialNote?.getTagsList() ?: emptyList()
        mutableStateOf(tags.filter { !isFavoriteTag(it) }.toSet())
    }
    var newTagInput by remember { mutableStateOf("") }
    var isNewTagFieldVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingAiExplanation by remember { mutableStateOf(false) }
    var showDiscardConfirmationDialog by remember { mutableStateOf(false) }

    val handleDismissAttempt: () -> Unit = {
        if (commentText.isNotBlank()) {
            showDiscardConfirmationDialog = true
        } else {
            onDismiss()
        }
    }

    val currentPlayingTrack by AudioPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val currentPlayingCues by AudioPlayerManager.subtitlesCues.collectAsStateWithLifecycle()

    val trackCues = remember(selectedTrack, currentPlayingTrack, currentPlayingCues) {
        val raw = if (selectedTrack == null) {
            emptyList<SubtitleCue>()
        } else if (selectedTrack.id == currentPlayingTrack?.id && currentPlayingCues.isNotEmpty()) {
            currentPlayingCues
        } else if (!selectedTrack.subtitleContent.isNullOrBlank()) {
            SubtitleParser.parseContent(selectedTrack.subtitleContent, selectedTrack.subtitleOffsetMs)
        } else if (!selectedTrack.subtitlePath.isNullOrBlank()) {
            SubtitleParser.parseFile(File(selectedTrack.subtitlePath), selectedTrack.subtitleOffsetMs)
        } else {
            val autoFile = SubtitleParser.findMatchingSubtitleFile(selectedTrack.filePath)
            if (autoFile != null) SubtitleParser.parseFile(autoFile, selectedTrack.subtitleOffsetMs) else emptyList()
        }
        if (selectedTrack?.isVirtualScene == true) {
            val sceneStart = selectedTrack.startOffsetMs
            val sceneEnd = selectedTrack.endOffsetMs ?: (selectedTrack.startOffsetMs + selectedTrack.duration)
            raw.filter { cue ->
                if (!cue.isTimed || cue.startMs < 0) true
                else {
                    val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 5000L
                    cueEnd >= sceneStart && cue.startMs <= sceneEnd
                }
            }
        } else {
            raw
        }
    }

    var currentStartCueIndex by remember(selectedTrackId) { mutableStateOf<Int?>(null) }
    var currentEndCueIndex by remember(selectedTrackId) { mutableStateOf<Int?>(null) }

    var originStartMs by remember(initialNote, prefilledStartMs) {
        mutableStateOf<Long?>(
            initialNote?.originStartMs ?: if (prefilledStartMs > 0) prefilledStartMs else null
        )
    }

    val spannedCues = remember(trackCues, startMs, endMs) {
        if (trackCues.isEmpty()) emptyList()
        else {
            trackCues.filter { cue ->
                val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 4000L
                cue.startMs < endMs && cueEnd > startMs
            }
        }
    }

    LaunchedEffect(trackCues, startMs, endMs, prefilledText) {
        if (currentStartCueIndex == null && trackCues.isNotEmpty()) {
            val trimmedNote = noteText.trim()
            val matchByText = if (trimmedNote.isNotEmpty()) {
                trackCues.indexOfFirst { it.text.trim().equals(trimmedNote, ignoreCase = true) }
            } else -1

            val matchByStart = if (matchByText < 0) {
                trackCues.indexOfFirst { Math.abs(it.startMs - startMs) <= 500 }
            } else matchByText

            val initialIdx = when {
                matchByText >= 0 -> matchByText
                matchByStart >= 0 -> matchByStart
                else -> {
                    val idx = trackCues.indexOfFirst { it.startMs >= startMs }
                    if (idx >= 0) idx else 0
                }
            }
            currentStartCueIndex = initialIdx
            currentEndCueIndex = initialIdx
            if (originStartMs == null && initialIdx in trackCues.indices) {
                originStartMs = trackCues[initialIdx].startMs
            }
        }
    }

    LaunchedEffect(spannedCues) {
        if (originStartMs == null && spannedCues.isNotEmpty()) {
            val dedicated = SubtitleParser.findDedicatedCueForNote(
                initialNote ?: Note(text = "", startTimestampMs = startMs, endTimestampMs = endMs),
                trackCues
            )
            originStartMs = dedicated?.startMs ?: spannedCues[0].startMs
        }
    }

    val minBound = remember(selectedTrack) {
        if (selectedTrack?.isVirtualScene == true) selectedTrack.startOffsetMs else 0L
    }

    val maxBound = remember(selectedTrack) {
        if (selectedTrack?.isVirtualScene == true) {
            selectedTrack.endOffsetMs ?: (selectedTrack.startOffsetMs + selectedTrack.duration)
        } else {
            selectedTrack?.duration ?: 3600000L
        }
    }

    val maxDuration = maxBound

    val canAddPrevious = remember(trackCues, currentStartCueIndex, startMs) {
        if (trackCues.isEmpty()) false
        else {
            val sIdx = currentStartCueIndex ?: trackCues.indexOfFirst { it.startMs >= startMs }.takeIf { it >= 0 } ?: 0
            sIdx > 0
        }
    }

    val canAddNext = remember(trackCues, currentEndCueIndex, endMs) {
        if (trackCues.isEmpty()) false
        else {
            val eIdx = currentEndCueIndex ?: trackCues.indexOfLast { it.startMs <= endMs }.takeIf { it >= 0 } ?: 0
            eIdx < trackCues.size - 1
        }
    }

    val handleAddPreviousCue: () -> Unit = {
        if (trackCues.isNotEmpty()) {
            val sIdx = currentStartCueIndex ?: trackCues.indexOfFirst { it.startMs >= startMs }.takeIf { it >= 0 } ?: 0
            val targetIdx = sIdx - 1
            if (targetIdx in trackCues.indices) {
                val prevCue = trackCues[targetIdx]
                currentStartCueIndex = targetIdx
                if (currentEndCueIndex == null) {
                    currentEndCueIndex = sIdx
                }
                // Update start timestamp to encompass the earlier segment
                startMs = prevCue.startMs.coerceAtLeast(minBound)
                if (endMs < startMs + 500L) {
                    endMs = (startMs + 1000L).coerceAtMost(maxBound)
                }
                // Prepend previous cue text
                val prevText = prevCue.text.trim()
                val currText = noteText.trim()
                noteText = if (currText.isEmpty()) prevText else "$prevText $currText"
                Toast.makeText(context, Loc.getText("added_previous_cue"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val handleAddNextCue: () -> Unit = {
        if (trackCues.isNotEmpty()) {
            val eIdx = currentEndCueIndex ?: trackCues.indexOfLast { it.startMs <= endMs || it.endMs <= endMs }.takeIf { it >= 0 } ?: (currentStartCueIndex ?: 0)
            val targetIdx = eIdx + 1
            if (targetIdx in trackCues.indices) {
                val nextCue = trackCues[targetIdx]
                currentEndCueIndex = targetIdx
                if (currentStartCueIndex == null) {
                    currentStartCueIndex = eIdx
                }
                // Update end timestamp to encompass the later segment
                val newEnd = if (nextCue.endMs > nextCue.startMs) nextCue.endMs else nextCue.startMs + 4000L
                endMs = newEnd.coerceAtMost(maxBound)
                if (startMs > endMs) {
                    startMs = nextCue.startMs.coerceAtLeast(minBound)
                }
                // Append next cue text
                val nextText = nextCue.text.trim()
                val currText = noteText.trim()
                noteText = if (currText.isEmpty()) nextText else "$currText $nextText"
                Toast.makeText(context, Loc.getText("added_next_cue"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val triggerAiExplanationGeneration = {
        if (!isGeneratingAiExplanation) {
            val currentPrompt = commentText.trim()
            if (noteText.isBlank() && currentPrompt.isBlank()) {
                val msg = if (Loc.currentLanguage == "ar") "يرجى كتابة كلمة أو تحديد مقطع صوتي أولاً" else "Please enter a word or select an audio quote first"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else {
                isGeneratingAiExplanation = true
                coroutineScope.launch {
                    val result = viewModel.generateNoteExplanation(
                        quoteText = noteText,
                        promptOrInstruction = currentPrompt,
                        trackTitle = selectedTrack?.fileName
                    )
                    isGeneratingAiExplanation = false
                    result.fold(
                        onSuccess = { generatedExplanation ->
                            commentText = generatedExplanation
                            Toast.makeText(context, Loc.getText("ai_generate_note_success"), Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            val msg = if (err.message == "MISSING_API_KEY") {
                                Loc.getText("missing_api_key_prompt")
                            } else {
                                err.localizedMessage ?: "Failed to generate"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = handleDismissAttempt,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { targetValue ->
                if (targetValue == SheetValue.Hidden && commentText.isNotBlank()) {
                    showDiscardConfirmationDialog = true
                    false
                } else {
                    true
                }
            }
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialNote != null) Loc.getText("edit_note") else Loc.getText("add_note"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = handleDismissAttempt) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Note / Quote Text Field with Previous & Next Cue Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Loc.getText("note_text"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (trackCues.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Previous Cue Button
                        OutlinedButton(
                            onClick = handleAddPreviousCue,
                            enabled = canAddPrevious,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("add_prev_cue_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Loc.getText("add_previous_cue"),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = Loc.getText("add_previous_cue"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Next Cue Button
                        OutlinedButton(
                            onClick = handleAddNextCue,
                            enabled = canAddNext,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("add_next_cue_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = Loc.getText("add_next_cue"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = Loc.getText("add_next_cue"),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Note / Quote Text Field
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text(Loc.getText("note_text_placeholder")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_text_input"),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            // Dedicated Subtitle Cue Selector (when a note covers multiple cues)
            if (spannedCues.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = Loc.getText("dedicated_cue"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Loc.getText("dedicated_cue_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(spannedCues) { cue ->
                            val isSelected = originStartMs != null && (
                                originStartMs == cue.startMs || (
                                    originStartMs!! in cue.startMs..(if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 4000L)
                                )
                            )
                            FilterChip(
                                selected = isSelected,
                                onClick = { originStartMs = cue.startMs },
                                label = {
                                    Text(
                                        text = "${cue.text.take(22)} (${formatDuration(cue.startMs)})",
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanation / Comment Field
            Text(
                text = Loc.getText("note_comment"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Explanation / Comment Field (Prompt input or AI output)
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                label = { Text(Loc.getText("note_comment")) },
                placeholder = { Text(Loc.getText("ai_note_comment_placeholder"), fontSize = 12.5.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_comment_input"),
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(
                        onClick = { triggerAiExplanationGeneration() },
                        enabled = !isGeneratingAiExplanation,
                        modifier = Modifier.testTag("ai_generate_note_button")
                    ) {
                        if (isGeneratingAiExplanation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = Loc.getText("ai_generate_note_button"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Audio Segment Time Adjuster (if track is selected)
            if (selectedTrack != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header: Track name + Play / Unlink buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = getTrackFileIcon(selectedTrack),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = selectedTrack.fileName,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val clipDurationSec = ((endMs - startMs) / 1000).coerceAtLeast(1)
                                FilledTonalButton(
                                    onClick = {
                                        if (isCurrentSnippetPlaying) {
                                            NoteAudioPlayer.stop()
                                        } else {
                                            NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                                        }
                                    },
                                    colors = if (isCurrentSnippetPlaying) {
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        ButtonDefaults.filledTonalButtonColors()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentSnippetPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isCurrentSnippetPlaying) "Pause" else "Play",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("${clipDurationSec}s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = { selectedTrackId = null },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(Loc.getText("unlink_audio"), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Start Time Section (Single Row with -1s, Timestamp, +1s)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = Loc.getText("start_time"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        startMs = (startMs - 1000L).coerceAtLeast(minBound)
                                        if (endMs < startMs + 500L) endMs = (startMs + 1000L).coerceAtMost(maxBound)
                                        if (isCurrentSnippetPlaying) {
                                            NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = formatDuration(startMs),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        startMs = (startMs + 1000L).coerceAtMost(maxBound)
                                        if (endMs < startMs + 500L) endMs = (startMs + 1000L).coerceAtMost(maxBound)
                                        if (isCurrentSnippetPlaying) {
                                            NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // End Time Section (Single Row with -1s, Timestamp, +1s)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccessTimeFilled,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = Loc.getText("end_time"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        endMs = (endMs - 1000L).coerceAtLeast(startMs + 500L)
                                        if (isCurrentSnippetPlaying) {
                                            NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = formatDuration(endMs),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        endMs = (endMs + 1000L).coerceAtMost(maxBound)
                                        if (isCurrentSnippetPlaying) {
                                            NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else if (allTracks.isNotEmpty()) {
                // Option to link audio track
                val currentPlayingTrack = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value
                if (currentPlayingTrack != null) {
                    FilledTonalButton(
                        onClick = {
                            selectedTrackId = currentPlayingTrack.id
                            val currentPos = if (currentPlayingTrack.isVirtualScene) {
                                currentPlayingTrack.startOffsetMs + AudioPlayerManager.currentPosition.value
                            } else {
                                AudioPlayerManager.currentPosition.value
                            }
                            val maxLimit = if (currentPlayingTrack.isVirtualScene) {
                                currentPlayingTrack.endOffsetMs ?: (currentPlayingTrack.startOffsetMs + currentPlayingTrack.duration)
                            } else {
                                currentPlayingTrack.duration
                            }
                            startMs = currentPos
                            endMs = (startMs + 5000L).coerceAtMost(maxLimit)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = getTrackFileIcon(currentPlayingTrack),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Loc.getText("link_current_audio"), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tags Section
            Text(
                text = Loc.getText("tags"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Add New Tag Button
                item {
                    AssistChip(
                        onClick = { isNewTagFieldVisible = !isNewTagFieldVisible },
                        label = { Text(Loc.getText("add_new_tag"), fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                // Existing Tags suggestions (unique & active)
                availableTags.forEach { tagStr ->
                    item(key = tagStr) {
                        val isSelected = selectedTags.any { it.equals(tagStr, ignoreCase = true) }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTags = if (isSelected) {
                                    selectedTags.filterNot { it.equals(tagStr, ignoreCase = true) }.toSet()
                                } else {
                                    selectedTags + tagStr
                                }
                            },
                            label = { Text("#$tagStr", fontSize = 11.sp) }
                        )
                    }
                }
            }

            if (isNewTagFieldVisible) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        placeholder = { Text(Loc.getText("new_tag_name"), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            val trimmed = newTagInput.trim()
                            if (trimmed.isNotEmpty()) {
                                if (!isFavoriteTag(trimmed) && selectedTags.none { it.equals(trimmed, ignoreCase = true) }) {
                                    selectedTags = selectedTags + trimmed
                                }
                                newTagInput = ""
                                isNewTagFieldVisible = false
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(Loc.getText("add"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Note Button
            Button(
                onClick = {
                    if (noteText.isBlank()) return@Button
                    if (NoteAudioPlayer.playingNoteId.value == -1L) {
                        NoteAudioPlayer.stop()
                    }
                    viewModel.saveNote(
                        id = initialNote?.id ?: 0L,
                        text = noteText,
                        comment = commentText,
                        trackId = selectedTrackId,
                        startTimestampMs = startMs,
                        endTimestampMs = endMs,
                        originStartMs = originStartMs ?: startMs,
                        tags = selectedTags.toList(),
                        onSuccess = onDismiss
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_note_button"),
                shape = RoundedCornerShape(12.dp),
                enabled = noteText.isNotBlank()
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Loc.getText("save_note"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    if (showDiscardConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmationDialog = false },
            title = {
                Text(
                    text = Loc.getText("discard_note_confirm_title"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = Loc.getText("discard_note_confirm_msg"),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmationDialog = false
                        onDismiss()
                    }
                ) {
                    Text(
                        text = Loc.getText("discard"),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirmationDialog = false }
                ) {
                    Text(text = Loc.getText("keep_editing"), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}
