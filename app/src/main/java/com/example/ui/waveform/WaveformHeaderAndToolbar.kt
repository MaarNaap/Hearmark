package com.example.ui.waveform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Loc

@Composable
internal fun WaveformEditorHeader(
    fileName: String,
    onDismiss: () -> Unit,
    onSaveAndApply: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = Loc.getText("manual_segments_title"),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = fileName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_waveform_cancel")
            ) {
                Text(Loc.getText("cancel"), fontSize = 12.sp)
            }

            Button(
                onClick = onSaveAndApply,
                modifier = Modifier.testTag("btn_waveform_save_apply"),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(Loc.getText("save_and_apply"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun WaveformEditorToolbar(
    zoomIndex: Int,
    zoomLevels: List<WaveformZoomSetting>,
    cutsCount: Int,
    followPlayhead: Boolean,
    onChangeZoom: (Int) -> Unit,
    onToggleFollowPlayhead: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Zoom Stepper Controls: Minus, Chip, Plus
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = Loc.getText("waveform_zoom") + ":",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledTonalIconButton(
                    onClick = { if (zoomIndex > 0) onChangeZoom(zoomIndex - 1) },
                    enabled = zoomIndex > 0,
                    modifier = Modifier.size(30.dp).testTag("btn_zoom_out")
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = Loc.getText("zoom_out"), modifier = Modifier.size(15.dp))
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable {
                        onChangeZoom((zoomIndex + 1) % zoomLevels.size)
                    }
                ) {
                    val zoomLabel = zoomLevels[zoomIndex].displayLabel
                    Text(
                        text = zoomLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = { if (zoomIndex < zoomLevels.lastIndex) onChangeZoom(zoomIndex + 1) },
                    enabled = zoomIndex < zoomLevels.lastIndex,
                    modifier = Modifier.size(30.dp).testTag("btn_zoom_in")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = Loc.getText("zoom_in"), modifier = Modifier.size(15.dp))
                }
            }

            // Right side: Cut Count & Follow playhead chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "$cutsCount ${Loc.getText("cuts_label")}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                FilterChip(
                    selected = followPlayhead,
                    onClick = onToggleFollowPlayhead,
                    label = {
                        Text(
                            text = Loc.getText("follow_playhead"),
                            fontSize = 11.sp,
                            fontWeight = if (followPlayhead) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = if (followPlayhead) {
                        { Icon(Icons.Filled.MyLocation, contentDescription = Loc.getText("follow_playhead_desc"), modifier = Modifier.size(12.dp)) }
                    } else null,
                    modifier = Modifier.height(30.dp)
                )
            }
        }
    }
}
