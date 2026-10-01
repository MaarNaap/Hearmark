package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.AudioPlayerManager

@Composable
fun NoteLinkTrackRow(
    onLinkTrack: (trackId: Long, startMs: Long, endMs: Long) -> Unit
) {
    val currentPlayingTrack = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value
    if (currentPlayingTrack != null) {
        FilledTonalButton(
            onClick = {
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
                val newEnd = (currentPos + 5000L).coerceAtMost(maxLimit)
                onLinkTrack(currentPlayingTrack.id, currentPos, newEnd)
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
