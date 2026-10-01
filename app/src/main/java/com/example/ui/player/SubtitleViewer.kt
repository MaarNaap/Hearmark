package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Note
import com.example.player.SubtitleCue
import com.example.player.SubtitleParser
import kotlinx.coroutines.launch

@Composable
fun HighlightedSubtitleText(
    text: String,
    query: String,
    isCurrentMatch: Boolean,
    fontSize: Float,
    lineHeightMultiplier: Double,
    fontWeight: FontWeight,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank() || !text.contains(cleanQuery, ignoreCase = true)) {
        Text(
            text = text,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp,
            fontWeight = fontWeight,
            color = textColor,
            modifier = modifier
        )
    } else {
        val annotatedString = remember(text, cleanQuery, isCurrentMatch) {
            buildAnnotatedString {
                val lowerText = text.lowercase()
                val lowerQuery = cleanQuery.lowercase()
                var startIndex = 0
                while (startIndex < text.length) {
                    val index = lowerText.indexOf(lowerQuery, startIndex)
                    if (index == -1) {
                        append(text.substring(startIndex))
                        break
                    }
                    if (index > startIndex) {
                        append(text.substring(startIndex, index))
                    }
                    val endIndex = index + lowerQuery.length
                    pushStyle(
                        SpanStyle(
                            background = if (isCurrentMatch) Color(0xFFFFD54F) else Color(0x77FFE082),
                            color = Color(0xFF1A1A1A),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    append(text.substring(index, endIndex))
                    pop()
                    startIndex = endIndex
                }
            }
        }
        Text(
            text = annotatedString,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp,
            fontWeight = fontWeight,
            color = textColor,
            modifier = modifier
        )
    }
}

@Composable
fun SubtitlesPageContent(
    cues: List<SubtitleCue>,
    activeCue: SubtitleCue?,
    currentPositionMs: Long,
    fontSize: Float,
    offsetMs: Long,
    showTimestamps: Boolean = true,
    isInFocusOrPracticeMode: Boolean = false,
    onToggleTimestamps: () -> Unit = {},
    onCopyAllSubtitlesClick: () -> Unit,
    onEditSubtitlesClick: () -> Unit,
    onImportSrtClick: () -> Unit,
    onPasteLyricsClick: () -> Unit,
    onTapToSyncClick: () -> Unit,
    onDeleteSubtitlesClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onAddNoteFromCue: ((cueText: String, startMs: Long, endMs: Long) -> Unit)? = null,
    onAskAiAboutCue: ((cueText: String, startMs: Long, endMs: Long) -> Unit)? = null,
    notes: List<Note> = emptyList(),
    onOpenNotes: ((List<Note>) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val isDarkBg = isInFocusOrPracticeMode || isSystemInDarkTheme()
    val hasTimings = remember(cues) { cues.any { it.isTimed && it.startMs >= 0L } }

    val matchingCueIndices = remember(cues, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) {
            emptyList()
        } else {
            cues.mapIndexedNotNull { index, cue ->
                if (cue.text.lowercase().contains(q)) index else null
            }
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(matchingCueIndices, currentMatchIndex) {
        if (matchingCueIndices.isNotEmpty() && currentMatchIndex in matchingCueIndices.indices) {
            val targetIdx = matchingCueIndices[currentMatchIndex]
            listState.animateScrollToItem(index = targetIdx, scrollOffset = 0)
        }
    }

    LaunchedEffect(activeCue?.startMs) {
        if (!isSearchExpanded && hasTimings && activeCue != null) {
            val index = cues.indexOfFirst { it.startMs == activeCue.startMs }
            if (index >= 0) {
                listState.animateScrollToItem(index = index, scrollOffset = 0)
            }
        }
    }

    val cueNotesMap = remember(cues, notes) {
        val map = mutableMapOf<SubtitleCue, MutableList<Note>>()
        if (notes.isNotEmpty() && cues.isNotEmpty()) {
            for (note in notes) {
                val dedicatedCue = SubtitleParser.findDedicatedCueForNote(note, cues)
                if (dedicatedCue != null) {
                    map.getOrPut(dedicatedCue) { mutableListOf() }.add(note)
                }
            }
        }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        SubtitleViewerTopHeader(
            hasCues = cues.isNotEmpty(),
            hasTimings = hasTimings,
            isDarkBg = isDarkBg,
            isSearchExpanded = isSearchExpanded,
            searchQuery = searchQuery,
            matchingCueCount = matchingCueIndices.size,
            currentMatchIndex = currentMatchIndex,
            showMenu = showMenu,
            showTimestamps = showTimestamps,
            fontSize = fontSize,
            offsetMs = offsetMs,
            focusRequester = focusRequester,
            onSearchExpandedChange = { isSearchExpanded = it },
            onSearchQueryChange = { searchQuery = it },
            onPreviousMatch = {
                if (matchingCueIndices.isNotEmpty()) {
                    val prev = if (currentMatchIndex <= 0) matchingCueIndices.size - 1 else currentMatchIndex - 1
                    currentMatchIndex = prev
                    coroutineScope.launch {
                        listState.animateScrollToItem((matchingCueIndices[prev] - 1).coerceAtLeast(0))
                    }
                }
            },
            onNextMatch = {
                if (matchingCueIndices.isNotEmpty()) {
                    val next = (currentMatchIndex + 1) % matchingCueIndices.size
                    currentMatchIndex = next
                    coroutineScope.launch {
                        listState.animateScrollToItem((matchingCueIndices[next] - 1).coerceAtLeast(0))
                    }
                }
            },
            onShowMenuChange = { showMenu = it },
            onTapToSyncClick = onTapToSyncClick,
            onToggleTimestamps = onToggleTimestamps,
            onCopyAllSubtitlesClick = onCopyAllSubtitlesClick,
            onEditSubtitlesClick = onEditSubtitlesClick,
            onImportSrtClick = onImportSrtClick,
            onPasteLyricsClick = onPasteLyricsClick,
            onDeleteSubtitlesClick = onDeleteSubtitlesClick,
            onFontSizeChange = onFontSizeChange,
            onAdjustOffset = onAdjustOffset,
            onResetOffset = onResetOffset
        )

        if (cues.isEmpty()) {
            SubtitleViewerEmptyState(
                isDarkBg = isDarkBg,
                onTapToSyncClick = onTapToSyncClick,
                onImportSrtClick = onImportSrtClick,
                onPasteLyricsClick = onPasteLyricsClick
            )
        } else {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(if (hasTimings) 8.dp else 12.dp),
                    contentPadding = PaddingValues(top = 40.dp, bottom = 220.dp)
                ) {
                    itemsIndexed(
                        items = cues,
                        key = { index, cue -> if (cue.isTimed && cue.startMs >= 0L) "cue_${cue.id}_${cue.startMs}" else "cue_idx_$index" }
                    ) { index, cue ->
                        val isCurrentSearchMatch = matchingCueIndices.getOrNull(currentMatchIndex) == index
                        if (hasTimings) {
                            val isTimedCue = cue.isTimed && cue.startMs >= 0L
                            val isActive = isTimedCue && activeCue != null && cue.startMs == activeCue.startMs
                            val isMatched = matchingCueIndices.contains(index)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(
                                        if (isTimedCue) Modifier.clickable { onSeekTo(cue.startMs) } else Modifier
                                    ),
                                color = when {
                                    isCurrentSearchMatch -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                    isActive -> if (isDarkBg) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    isMatched -> if (isDarkBg) Color(0xFF242C38) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else -> if (isDarkBg) Color(0xFF141820) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                },
                                border = when {
                                    isCurrentSearchMatch -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                                    isActive -> BorderStroke(1.2.dp, if (isDarkBg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                    isMatched -> BorderStroke(1.dp, if (isDarkBg) Color(0xFF475569) else MaterialTheme.colorScheme.outlineVariant)
                                    else -> if (isDarkBg) BorderStroke(0.6.dp, Color(0xFF334155).copy(alpha = 0.35f)) else null
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = if (showTimestamps && isTimedCue) 8.dp else 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (showTimestamps && isTimedCue) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isDarkBg) {
                                                    Color.Black.copy(alpha = 0.25f)
                                                } else {
                                                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                                                },
                                                border = BorderStroke(
                                                    0.5.dp,
                                                    if (isDarkBg) {
                                                        Color(0xFF1E293B).copy(alpha = 0.50f)
                                                    } else {
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                                                    }
                                                )
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    val timestampColor = if (isDarkBg) {
                                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else Color(0xFF475569)
                                                    } else {
                                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.60f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Filled.AccessTime,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(9.dp),
                                                        tint = timestampColor
                                                    )
                                                    Text(
                                                        text = formatDuration(cue.startMs),
                                                        fontSize = 9.5.sp,
                                                        fontWeight = if (isCurrentSearchMatch) FontWeight.Bold else FontWeight.Normal,
                                                        color = timestampColor
                                                    )
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.width(1.dp))
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            if (notes.isNotEmpty()) {
                                                val matchingNotes = cueNotesMap[cue] ?: emptyList()
                                                if (matchingNotes.isNotEmpty()) {
                                                    val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + 5000L)
                                                    IconButton(
                                                        onClick = {
                                                            if (onOpenNotes != null) {
                                                                onOpenNotes(matchingNotes)
                                                            } else if (onAddNoteFromCue != null) {
                                                                onAddNoteFromCue(cue.text, cue.startMs, cueEnd)
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Note,
                                                                contentDescription = Loc.getText("view_note"),
                                                                modifier = Modifier.size(15.dp),
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                            if (matchingNotes.size > 1) {
                                                                Text(
                                                                    text = "${matchingNotes.size}",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            if (onAskAiAboutCue != null) {
                                                IconButton(
                                                    onClick = {
                                                        onAskAiAboutCue(cue.text, cue.startMs, if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + 5000L))
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.AutoAwesome,
                                                        contentDescription = Loc.getText("gemini_ai_assistant"),
                                                        modifier = Modifier.size(15.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }

                                            if (onAddNoteFromCue != null) {
                                                IconButton(
                                                    onClick = {
                                                        onAddNoteFromCue(cue.text, cue.startMs, if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + 5000L))
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.EditNote,
                                                        contentDescription = Loc.getText("add_note"),
                                                        modifier = Modifier.size(16.dp),
                                                        tint = if (isDarkBg) {
                                                            if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
                                                        } else {
                                                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    HighlightedSubtitleText(
                                        text = cue.text,
                                        query = searchQuery,
                                        isCurrentMatch = isCurrentSearchMatch,
                                        fontSize = fontSize,
                                        lineHeightMultiplier = 1.4,
                                        fontWeight = if (isActive || isCurrentSearchMatch) FontWeight.SemiBold else FontWeight.Normal,
                                        textColor = if (isDarkBg) {
                                            if (isActive) Color.White else Color(0xFFF8FAFC)
                                        } else {
                                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            // Non-timed continuous plain text reading mode
                            val isMatched = matchingCueIndices.contains(index)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                color = when {
                                    isCurrentSearchMatch -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    isMatched -> if (isDarkBg) Color(0xFF242C38) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    else -> if (isDarkBg) Color(0xFF141820) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                },
                                border = when {
                                    isCurrentSearchMatch -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                                    isMatched -> BorderStroke(1.dp, if (isDarkBg) Color(0xFF475569) else MaterialTheme.colorScheme.outlineVariant)
                                    else -> if (isDarkBg) BorderStroke(0.6.dp, Color(0xFF334155).copy(alpha = 0.35f)) else null
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                HighlightedSubtitleText(
                                    text = cue.text,
                                    query = searchQuery,
                                    isCurrentMatch = isCurrentSearchMatch,
                                    fontSize = fontSize,
                                    lineHeightMultiplier = 1.5,
                                    fontWeight = FontWeight.Normal,
                                    textColor = if (isDarkBg) Color(0xFFF8FAFC) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
