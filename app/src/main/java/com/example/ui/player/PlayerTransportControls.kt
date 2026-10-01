package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AudioPlayerManager

@Composable
fun PlayerTransportControls(
    isPlayingState: Boolean,
    arePlaybackButtonsActive: Boolean,
    skipSecondsSetting: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { AudioPlayerManager.playPreviousTrack() },
            enabled = arePlaybackButtonsActive,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous Track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }

        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { AudioPlayerManager.skipBackward() },
                enabled = arePlaybackButtonsActive
            ) {
                Icon(
                    imageVector = Icons.Filled.FastRewind,
                    contentDescription = "Rewind",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "$skipSecondsSetting",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = 12.dp)
            )
        }

        IconButton(
            onClick = {
                if (isPlayingState) AudioPlayerManager.pause() else AudioPlayerManager.resume()
            },
            enabled = arePlaybackButtonsActive,
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play pause overlay",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { AudioPlayerManager.skipForward() },
                enabled = arePlaybackButtonsActive
            ) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = "ForwardFast",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "$skipSecondsSetting",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = 12.dp)
            )
        }

        IconButton(
            onClick = { AudioPlayerManager.playNextTrack() },
            enabled = arePlaybackButtonsActive,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next Track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
