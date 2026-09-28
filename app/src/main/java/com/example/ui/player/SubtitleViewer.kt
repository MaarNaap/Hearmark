package com.example.ui

import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.WindowManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.RectangleShape
import com.example.data.*
import com.example.player.*
import com.example.ui.theme.*
import com.example.R
import com.example.util.AudioMetadataExtractor
import com.example.util.TrackMetadata
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
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
        // Top Action Header with Expandable Search and Three-Dotted Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDarkBg) Color(0xFF1E2430) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, if (isDarkBg) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = if (isDarkBg) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(if (isDarkBg) Color.White else MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = Loc.getText("search_subtitles_hint"),
                                        color = if (isDarkBg) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (searchQuery.isNotBlank()) {
                            if (matchingCueIndices.isNotEmpty()) {
                                Text(
                                    text = "${currentMatchIndex + 1}/${matchingCueIndices.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDarkBg) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = {
                                        if (matchingCueIndices.isNotEmpty()) {
                                            val prev = if (currentMatchIndex <= 0) matchingCueIndices.size - 1 else currentMatchIndex - 1
                                            currentMatchIndex = prev
                                            coroutineScope.launch {
                                                listState.animateScrollToItem((matchingCueIndices[prev] - 1).coerceAtLeast(0))
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowUp,
                                        contentDescription = "Previous Match",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (matchingCueIndices.isNotEmpty()) {
                                            val next = (currentMatchIndex + 1) % matchingCueIndices.size
                                            currentMatchIndex = next
                                            coroutineScope.launch {
                                                listState.animateScrollToItem((matchingCueIndices[next] - 1).coerceAtLeast(0))
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Next Match",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                Text(
                                    text = Loc.getText("no_matches_found"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                } else {
                                    isSearchExpanded = false
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Search",
                                modifier = Modifier.size(18.dp),
                                tint = if (isDarkBg) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!isSearchExpanded) {
                Spacer(modifier = Modifier.weight(1f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isSearchExpanded) {
                    IconButton(
                        onClick = { isSearchExpanded = true },
                        modifier = Modifier.size(36.dp),
                        enabled = cues.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search Subtitles",
                            tint = if (isDarkBg) {
                                if (cues.isNotEmpty()) Color(0xFFE2E8F0) else Color(0xFF64748B)
                            } else {
                                if (cues.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Subtitle Controls",
                            tint = if (isDarkBg) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.widthIn(min = 160.dp)
                    ) {
                        // 0. Tap to Sync (المزامنة الحية)
                        DropdownMenuItem(
                            text = { Text(Loc.getText("tap_to_sync"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                showMenu = false
                                onTapToSyncClick()
                            }
                        )

                        // 0.5 Toggle Timestamps Visibility (إظهار / إخفاء التوقيت)
                        if (hasTimings) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (showTimestamps) Loc.getText("hide_timestamps") else Loc.getText("show_timestamps"),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleTimestamps()
                                }
                            )
                        }

                        // 1. Copy All Subtitles / Text
                        DropdownMenuItem(
                            text = { Text(Loc.getText("copy_full_subtitles"), fontSize = 13.sp) },
                            enabled = cues.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                onCopyAllSubtitlesClick()
                            }
                        )

                        // 2. Edit Subtitles / Text
                        DropdownMenuItem(
                            text = { Text(Loc.getText("edit_subtitles"), fontSize = 13.sp) },
                            enabled = cues.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                onEditSubtitlesClick()
                            }
                        )

                        // 3. Import Subtitles
                        DropdownMenuItem(
                            text = { Text(Loc.getText("load_subtitles"), fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onImportSrtClick()
                            }
                        )

                        // 4. Paste Subtitles / Lyrics
                        DropdownMenuItem(
                            text = { Text(Loc.getText("paste_subtitles"), fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onPasteLyricsClick()
                            }
                        )

                        // 5. Delete Subtitles
                        DropdownMenuItem(
                            text = { Text(Loc.getText("clear_subtitles"), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                            enabled = cues.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                onDeleteSubtitlesClick()
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // 6. Font Size Controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${Loc.getText("subtitles_size")} (${fontSize.toInt()})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onFontSizeChange((fontSize - 2f).coerceAtLeast(10f)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("A-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onFontSizeChange((fontSize + 2f).coerceAtMost(32f)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("A+", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (hasTimings) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // 7. Sync Delay Controls (Open-ended adjustment with reset)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = Loc.getText("subtitles_offset"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val offsetSec = offsetMs / 1000.0
                                    val formattedOffset = if (offsetMs == 0L) "0.0s" else String.format(Locale.US, "%+.1fs", offsetSec)
                                    Surface(
                                        color = if (offsetMs != 0L) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = formattedOffset,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (offsetMs != 0L) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                // Fast Adjust (+/- 0.5s)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onAdjustOffset(-500L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("-0.5s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustOffset(500L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("+0.5s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Fine Adjust (+/- 0.1s)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onAdjustOffset(-100L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("-0.1s", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustOffset(100L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("+0.1s", fontSize = 10.sp)
                                    }
                                }

                                if (offsetMs != 0L) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FilledTonalButton(
                                        onClick = { onResetOffset() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(30.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(Loc.getText("reset_offset"), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (cues.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = Loc.getText("no_subtitles_found"),
                        fontSize = 13.sp,
                        color = if (isDarkBg) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = onTapToSyncClick,
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TouchApp,
                                contentDescription = Loc.getText("tap_to_sync"),
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalIconButton(
                            onClick = onImportSrtClick,
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = Loc.getText("load_subtitles"),
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalIconButton(
                            onClick = onPasteLyricsClick,
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EditNote,
                                contentDescription = Loc.getText("paste_subtitles"),
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
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
