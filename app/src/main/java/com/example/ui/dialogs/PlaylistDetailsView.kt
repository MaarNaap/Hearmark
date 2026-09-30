package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import com.example.player.AudioPlayerManager

@Composable
fun PlaylistDetailsView(
    playlistId: Long,
    viewModel: AppViewModel,
    backPressed: () -> Unit,
    onCreateTaskForPlaylist: (String, Long) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlist = allPlaylists.find { it.id == playlistId }
    val playlistName = playlist?.name ?: ""

    val tracksFlow = remember(playlistId) { viewModel.repository.getTracksForPlaylistFlow(playlistId) }
    val rawTracks by tracksFlow.collectAsStateWithLifecycle(emptyList())

    var playlistSortBy by remember { mutableStateOf("name") }
    var playlistIsAscending by remember { mutableStateOf(true) }

    val tracks = remember(rawTracks, playlistSortBy, playlistIsAscending) {
        getSortedTracks(rawTracks, playlistSortBy, playlistIsAscending)
    }

    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()

    var showAddTracksDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<AudioTrack?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = backPressed) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(Loc.getText("play_next")) },
                        onClick = {
                            showMenu = false
                            viewModel.addTracksToPlayNext(tracks)
                            Toast.makeText(context, Loc.getText("added_to_queue"), Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("choose_files")) },
                        onClick = {
                            showMenu = false
                            showAddTracksDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("create_task")) },
                        onClick = {
                            showMenu = false
                            onCreateTaskForPlaylist("PLAYLIST", playlistId)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("show_associated_tasks")) },
                        onClick = {
                            showMenu = false
                            onShowAssociatedTasks("PLAYLIST", playlistId, playlistName)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("delete_history"), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            viewModel.deletePlaylist(playlistId, false)
                            backPressed()
                        }
                    )
                }
            }
        }

        // Playlist Name in separate row with generous space and prominent typography
        Text(
            text = playlistName,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Text(
                        text = Loc.getText("empty_playlists_desc"),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Button(onClick = { showAddTracksDialog = true }) {
                        Text(Loc.getText("choose_files"))
                    }
                }
            }
        } else {
            TrackListSortHeader(
                totalCount = tracks.size,
                sortBy = playlistSortBy,
                onSortByChange = { playlistSortBy = it },
                isAscending = playlistIsAscending,
                onIsAscendingChange = { playlistIsAscending = it }
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tracks) { track ->
                    val isCurrentExecuting = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value?.id == track.id
                    val isPlaying = AudioPlayerManager.isPlaying.collectAsStateWithLifecycle().value

                    UnifiedAudioTrackRow(
                        track = track,
                        isCurrentExecuting = isCurrentExecuting,
                        isPlaying = isPlaying,
                        viewModel = viewModel,
                        playlistId = playlistId,
                        onTrackPlaylistMenuClicked = { showAddToPlaylistDialogForTrack = it },
                        onCreateTask = { type, id -> onCreateTaskForPlaylist(type, id) },
                        onShowAssociatedTasks = { type, id, name -> onShowAssociatedTasks(type, id, name) },
                        playlistTracks = tracks
                    )
                }
            }
        }
    }

    // Tracks Selection Dialog for adding to Playlist
    if (showAddTracksDialog) {
        val attachedTrackIds = tracks.map { it.id }.toSet()
        val eligibleTracks = allTracks.filter { it.id !in attachedTrackIds }
        var selectedIds by remember { mutableStateOf(setOf<Long>()) }

        AlertDialog(
            onDismissRequest = { showAddTracksDialog = false },
            title = { Text(Loc.getText("select_tracks_dialog_title")) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (eligibleTracks.isEmpty()) {
                        Text(
                            text = Loc.getText("no_tracks_to_add"),
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(eligibleTracks) { track ->
                                val isChecked = selectedIds.contains(track.id)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIds = if (isChecked) {
                                                selectedIds - track.id
                                            } else {
                                                selectedIds + track.id
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked == true) {
                                                    selectedIds + track.id
                                                } else {
                                                    selectedIds - track.id
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = middleEllipse(track.getDisplayTitle(), maxLength = 26),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val parentFolderName = if (track.parentFolderId != null) "📁 " + Loc.getText("library") else "🎵 " + Loc.getText("independent_track")
                                            Text(
                                                text = "$parentFolderName | ${formatDuration(track.duration)} | ${track.getProgressPercent()}%",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedIds.isNotEmpty()) {
                            viewModel.addTracksToPlaylist(playlistId, selectedIds.toList())
                        }
                        showAddTracksDialog = false
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text(Loc.getText("add_selected"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTracksDialog = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    if (showAddToPlaylistDialogForTrack != null) {
        AddToPlaylistDialog(
            track = showAddToPlaylistDialogForTrack!!,
            playlists = allPlaylists,
            onDismiss = { showAddToPlaylistDialogForTrack = null },
            onPlaylistSelected = { playlist ->
                viewModel.addTrackToPlaylist(playlist.id, showAddToPlaylistDialogForTrack!!.id)
                Toast.makeText(context, String.format(Loc.getText("added_to_playlist_success"), playlist.name), Toast.LENGTH_SHORT).show()
            },
            onCreatePlaylistClicked = {}
        )
    }
}
