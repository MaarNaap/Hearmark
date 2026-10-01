package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack
import com.example.player.AudioPlayerManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlayerQueueSheet(
    track: AudioTrack,
    currentQueueState: List<AudioTrack>,
    selectedQueueIndices: Set<Int>,
    onUpdateSelectedIndices: (Set<Int>) -> Unit
) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        modifier = Modifier.padding(vertical = 4.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // Header bar with title and clear button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${Loc.getText("queue")} (${currentQueueState.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (currentQueueState.size > 1) {
                TextButton(
                    onClick = { AudioPlayerManager.clearQueue() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ClearAll,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = Loc.getText("clear_queue"),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Action bar for selected queue tracks
        if (selectedQueueIndices.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp, top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedQueueIndices.size} ${Loc.getText("selected")}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onUpdateSelectedIndices(emptySet()) }) {
                        Text(Loc.getText("cancel"), fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = {
                            AudioPlayerManager.removeTracksFromQueue(selectedQueueIndices.toList())
                            onUpdateSelectedIndices(emptySet())
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Remove chosen files from queue",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (currentQueueState.isEmpty()) {
            Text(
                text = Loc.getText("empty_queue"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            // Scrollable container bounded to comfortable height so all 20+ items are scrollable & visible
            val queueScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(queueScrollState)
            ) {
                currentQueueState.forEachIndexed { idx, qTrack ->
                    val isRunning = qTrack.id == track.id
                    val isChosen = selectedQueueIndices.contains(idx)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isChosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else if (isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selectedQueueIndices.isNotEmpty()) {
                                        onUpdateSelectedIndices(
                                            if (isChosen) {
                                                selectedQueueIndices - idx
                                            } else {
                                                selectedQueueIndices + idx
                                            }
                                        )
                                    } else {
                                        AudioPlayerManager.playTrack(qTrack, currentQueueState)
                                    }
                                },
                                onLongClick = {
                                    onUpdateSelectedIndices(selectedQueueIndices + idx)
                                }
                            )
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}.",
                                fontSize = 12.sp,
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = if (isRunning) qTrack.getDisplayTitle() else middleEllipse(qTrack.getDisplayTitle(), 44),
                                modifier = if (isRunning) Modifier.basicMarquee() else Modifier,
                                fontSize = 12.5.sp,
                                fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isChosen) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected indicator",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                )
                            }
                            Text(
                                text = formatDuration(qTrack.duration),
                                fontSize = 12.sp,
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•",
                                fontSize = 12.sp,
                                color = if (isRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${qTrack.getProgressPercent()}%",
                                fontSize = 12.sp,
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (idx < currentQueueState.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}
