package com.example.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack
import com.example.data.QuizQuestion
import com.example.ui.Loc
import com.example.ui.QuizTabMode

@Composable
internal fun QuizBankBrowserView(
    targetTrack: AudioTrack?,
    trackQuestions: List<QuizQuestion>,
    allVocabQuestions: List<QuizQuestion>,
    onStartPractice: (List<QuizQuestion>) -> Unit,
    onDeleteQuestion: (Long) -> Unit,
    onClearTrackBank: (Long) -> Unit,
    onTogglePlayAudio: (Long, Long?) -> Unit,
    isPlayingAudio: Boolean,
    currentlyPlayingTs: Long?,
    onNavigateToTab: ((QuizTabMode) -> Unit)? = null
) {
    var viewAllVocab by remember { mutableStateOf(targetTrack == null) }
    var questionIdToDelete by remember { mutableStateOf<Long?>(null) }
    val displayList = if (viewAllVocab) allVocabQuestions else trackQuestions

    if (questionIdToDelete != null) {
        AlertDialog(
            onDismissRequest = { questionIdToDelete = null },
            title = { Text(Loc.getText("delete_question_confirm_title")) },
            text = { Text(Loc.getText("delete_question_confirm_desc")) },
            confirmButton = {
                Button(
                    onClick = {
                        questionIdToDelete?.let { onDeleteQuestion(it) }
                        questionIdToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { questionIdToDelete = null }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        // Toggle view track vs all vocab
        if (targetTrack != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !viewAllVocab,
                        onClick = { viewAllVocab = false },
                        label = { Text(Loc.getFormattedText("unified_quiz_current_track_bank", trackQuestions.size), fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = viewAllVocab,
                        onClick = { viewAllVocab = true },
                        label = { Text(Loc.getFormattedText("unified_quiz_all_vocab_bank", allVocabQuestions.size), fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Start practice button
        if (displayList.isNotEmpty()) {
            item {
                Button(
                    onClick = { onStartPractice(displayList) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${Loc.getText("unified_quiz_start_session")} (${displayList.size})", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (displayList.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Quiz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Text(
                            text = Loc.getText("quiz_empty_bank_prompt"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (onNavigateToTab != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onNavigateToTab(QuizTabMode.TRACK) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(Loc.getText("unified_quiz_tab_track"))
                                }

                                OutlinedButton(
                                    onClick = { onNavigateToTab(QuizTabMode.NOTEBOOK) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(Loc.getText("unified_quiz_tab_notebook"))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(displayList, key = { it.id }) { q ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                val ts = q.timestampMs
                                if (ts != null && ts > 0) {
                                    val isPlaying = isPlayingAudio && currentlyPlayingTs == ts
                                    IconButton(
                                        onClick = { onTogglePlayAudio(ts, q.trackId) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = if (isPlaying) Loc.getText("pause_clip") else Loc.getText("play_clip"),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { questionIdToDelete = q.id },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = Loc.getText("delete"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Text(q.question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (!q.meaning.isNullOrBlank()) {
                            Text(q.meaning ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
