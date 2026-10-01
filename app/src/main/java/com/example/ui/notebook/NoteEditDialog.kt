package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
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

    val canAddPrevious = remember(trackCues, currentStartCueIndex, startMs) {
        canAddPreviousCue(trackCues, currentStartCueIndex, startMs)
    }

    val canAddNext = remember(trackCues, currentEndCueIndex, endMs) {
        canAddNextCue(trackCues, currentEndCueIndex, endMs)
    }

    val handleAddPreviousCue: () -> Unit = {
        val merged = mergePreviousCue(
            trackCues = trackCues,
            currentStartCueIndex = currentStartCueIndex,
            currentEndCueIndex = currentEndCueIndex,
            startMs = startMs,
            endMs = endMs,
            noteText = noteText,
            minBound = minBound,
            maxBound = maxBound
        )
        if (merged != null) {
            currentStartCueIndex = merged.newStartCueIndex
            currentEndCueIndex = merged.newEndCueIndex
            startMs = merged.newStartMs
            endMs = merged.newEndMs
            noteText = merged.newNoteText
            Toast.makeText(context, Loc.getText("added_previous_cue"), Toast.LENGTH_SHORT).show()
        }
    }

    val handleAddNextCue: () -> Unit = {
        val merged = mergeNextCue(
            trackCues = trackCues,
            currentStartCueIndex = currentStartCueIndex,
            currentEndCueIndex = currentEndCueIndex,
            startMs = startMs,
            endMs = endMs,
            noteText = noteText,
            minBound = minBound,
            maxBound = maxBound
        )
        if (merged != null) {
            currentStartCueIndex = merged.newStartCueIndex
            currentEndCueIndex = merged.newEndCueIndex
            startMs = merged.newStartMs
            endMs = merged.newEndMs
            noteText = merged.newNoteText
            Toast.makeText(context, Loc.getText("added_next_cue"), Toast.LENGTH_SHORT).show()
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

            NoteCueSelector(
                spannedCues = spannedCues,
                originStartMs = originStartMs,
                onSelectCueStart = { originStartMs = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            NoteExplanationSection(
                commentText = commentText,
                isGeneratingAiExplanation = isGeneratingAiExplanation,
                onCommentTextChange = { commentText = it },
                onTriggerAiGeneration = { triggerAiExplanationGeneration() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTrack != null) {
                NoteTimeAdjuster(
                    selectedTrack = selectedTrack,
                    startMs = startMs,
                    endMs = endMs,
                    minBound = minBound,
                    maxBound = maxBound,
                    isCurrentSnippetPlaying = isCurrentSnippetPlaying,
                    context = context,
                    onUpdateRange = { newStart, newEnd ->
                        startMs = newStart
                        endMs = newEnd
                    },
                    onUnlinkTrack = { selectedTrackId = null }
                )
            } else if (allTracks.isNotEmpty()) {
                NoteLinkTrackRow(
                    onLinkTrack = { linkedId, newStart, newEnd ->
                        selectedTrackId = linkedId
                        startMs = newStart
                        endMs = newEnd
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            NoteTagsSection(
                availableTags = availableTags,
                selectedTags = selectedTags,
                newTagInput = newTagInput,
                isNewTagFieldVisible = isNewTagFieldVisible,
                isFavoriteTag = isFavoriteTag,
                onSelectedTagsChange = { selectedTags = it },
                onNewTagInputChange = { newTagInput = it },
                onNewTagFieldVisibleChange = { isNewTagFieldVisible = it }
            )

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
