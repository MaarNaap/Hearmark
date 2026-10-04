package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.SubtitleCue

@Composable
fun NoteCueSelector(
    spannedCues: List<SubtitleCue>,
    originStartMs: Long?,
    onSelectCueStart: (Long) -> Unit
) {
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
                items(spannedCues, key = { "${it.startMs}_${it.endMs}" }) { cue ->
                    val isSelected = originStartMs != null && (
                        originStartMs == cue.startMs || (
                            originStartMs in cue.startMs..(if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 4000L)
                        )
                    )
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCueStart(cue.startMs) },
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
}
