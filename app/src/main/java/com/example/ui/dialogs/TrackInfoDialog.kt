package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AudioTrack

@Composable
fun TrackInfoDialog(
    track: AudioTrack,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = Loc.getText("file_info_title"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoItemRow(label = Loc.getText("name_header"), value = track.getDisplayTitle())
                InfoItemRow(label = Loc.getText("file_path"), value = track.filePath)
                InfoItemRow(label = Loc.getText("duration"), value = formatDuration(track.duration))
                InfoItemRow(label = Loc.getText("sort_progress"), value = "${track.getProgressPercent()}%")
                InfoItemRow(label = Loc.getText("plays_count"), value = "${track.playCount}")
                if (track.isVirtualScene) {
                    InfoItemRow(label = Loc.getText("virtual_scene_badge"), value = Loc.getText("yes"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("close"))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun InfoItemRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
