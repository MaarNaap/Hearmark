package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun SubtitleViewerTopHeader(
    hasCues: Boolean,
    hasTimings: Boolean,
    isDarkBg: Boolean,
    isSearchExpanded: Boolean,
    searchQuery: String,
    matchingCueCount: Int,
    currentMatchIndex: Int,
    showMenu: Boolean,
    showTimestamps: Boolean,
    fontSize: Float,
    offsetMs: Long,
    focusRequester: FocusRequester,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onShowMenuChange: (Boolean) -> Unit,
    onTapToSyncClick: () -> Unit,
    onToggleTimestamps: () -> Unit,
    onCopyAllSubtitlesClick: () -> Unit,
    onEditSubtitlesClick: () -> Unit,
    onImportSrtClick: () -> Unit,
    onPasteLyricsClick: () -> Unit,
    onDeleteSubtitlesClick: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit
) {
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
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
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
                        if (matchingCueCount > 0) {
                            Text(
                                text = "${currentMatchIndex + 1}/$matchingCueCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkBg) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = onPreviousMatch,
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
                                onClick = onNextMatch,
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
                                onSearchQueryChange("")
                            } else {
                                onSearchExpandedChange(false)
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
                    onClick = { onSearchExpandedChange(true) },
                    modifier = Modifier.size(36.dp),
                    enabled = hasCues
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search Subtitles",
                        tint = if (isDarkBg) {
                            if (hasCues) Color(0xFFE2E8F0) else Color(0xFF64748B)
                        } else {
                            if (hasCues) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { onShowMenuChange(true) },
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
                    onDismissRequest = { onShowMenuChange(false) },
                    modifier = Modifier.widthIn(min = 160.dp)
                ) {
                    // 0. Tap to Sync (المزامنة الحية)
                    DropdownMenuItem(
                        text = { Text(Loc.getText("tap_to_sync"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            onShowMenuChange(false)
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
                                onShowMenuChange(false)
                                onToggleTimestamps()
                            }
                        )
                    }

                    // 1. Copy All Subtitles / Text
                    DropdownMenuItem(
                        text = { Text(Loc.getText("copy_full_subtitles"), fontSize = 13.sp) },
                        enabled = hasCues,
                        onClick = {
                            onShowMenuChange(false)
                            onCopyAllSubtitlesClick()
                        }
                    )

                    // 2. Edit Subtitles / Text
                    DropdownMenuItem(
                        text = { Text(Loc.getText("edit_subtitles"), fontSize = 13.sp) },
                        enabled = hasCues,
                        onClick = {
                            onShowMenuChange(false)
                            onEditSubtitlesClick()
                        }
                    )

                    // 3. Import Subtitles
                    DropdownMenuItem(
                        text = { Text(Loc.getText("load_subtitles"), fontSize = 13.sp) },
                        onClick = {
                            onShowMenuChange(false)
                            onImportSrtClick()
                        }
                    )

                    // 4. Paste Subtitles / Lyrics
                    DropdownMenuItem(
                        text = { Text(Loc.getText("paste_subtitles"), fontSize = 13.sp) },
                        onClick = {
                            onShowMenuChange(false)
                            onPasteLyricsClick()
                        }
                    )

                    // 5. Delete Subtitles
                    DropdownMenuItem(
                        text = { Text(Loc.getText("clear_subtitles"), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                        enabled = hasCues,
                        onClick = {
                            onShowMenuChange(false)
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
                                    onClick = onResetOffset,
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
}

@Composable
internal fun SubtitleViewerEmptyState(
    isDarkBg: Boolean,
    onTapToSyncClick: () -> Unit,
    onImportSrtClick: () -> Unit,
    onPasteLyricsClick: () -> Unit
) {
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
}
