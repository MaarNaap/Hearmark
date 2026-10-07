package com.example.ui.vocab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack
import com.example.data.QuizQuestion
import com.example.ui.Loc
import com.example.ui.toWesternDigits

@Composable
fun VocabBankBrowserView(
    allQuestions: List<QuizQuestion>,
    trackMap: Map<Long, AudioTrack>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterMode: String,
    onFilterModeChange: (String) -> Unit,
    onDeleteQuestion: (QuizQuestion) -> Unit,
    onPlaySnippet: (Long, Long?) -> Unit,
    currentlyPlayingTs: Long?,
    isPlayingAudio: Boolean
) {
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dueColor = if (isDarkSurface) Color(0xFFFFB74D) else Color(0xFFE65100)
    val scheduledColor = if (isDarkSurface) Color(0xFF81C784) else Color(0xFF2E7D32)
    val badgeBgAlpha = if (isDarkSurface) 0.18f else 0.15f
    var questionToDelete by remember { mutableStateOf<QuizQuestion?>(null) }

    if (questionToDelete != null) {
        AlertDialog(
            onDismissRequest = { questionToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = Loc.getText("delete_question_confirm_title"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = Loc.getText("delete_question_confirm_desc"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        questionToDelete?.let { onDeleteQuestion(it) }
                        questionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_vocab_question_btn")
                ) {
                    Text(Loc.getText("delete"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { questionToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_vocab_question_btn")
                ) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    val dueCount = remember(allQuestions) {
        allQuestions.count { com.example.util.SpacedRepetition.isDue(it.srNextReviewAt) }
    }

    val filteredList = remember(allQuestions, searchQuery, filterMode) {
        allQuestions.filter { q ->
            val matchesQuery = searchQuery.isBlank() ||
                    q.question.contains(searchQuery, ignoreCase = true) ||
                    (q.targetWord?.contains(searchQuery, ignoreCase = true) == true) ||
                    (q.meaning?.contains(searchQuery, ignoreCase = true) == true) ||
                    q.getOptions().any { it.contains(searchQuery, ignoreCase = true) } ||
                    q.explanation.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (filterMode) {
                "DUE" -> com.example.util.SpacedRepetition.isDue(q.srNextReviewAt)
                "MASTERED" -> q.timesAnswered > 0 && (q.timesCorrect.toFloat() / q.timesAnswered) >= 0.75f
                "NEEDS_PRACTICE" -> q.timesAnswered > 0 && (q.timesCorrect.toFloat() / q.timesAnswered) < 0.75f
                "UNTESTED" -> q.timesAnswered == 0
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vocab_bank_search"),
            placeholder = { Text(Loc.getText("vocab_review_search_hint")) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val filters = listOf(
                "DUE" to Loc.getFormattedText("vocab_filter_due", dueCount),
                "ALL" to Loc.getFormattedText("vocab_filter_all_count", allQuestions.size),
                "MASTERED" to Loc.getText("vocab_review_mastered"),
                "NEEDS_PRACTICE" to Loc.getText("vocab_review_needs_practice"),
                "UNTESTED" to Loc.getText("vocab_review_untested")
            )
            items(filters, key = { it.first }) { (key, label) ->
                FilterChip(
                    selected = filterMode == key,
                    onClick = { onFilterModeChange(key) },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Loc.getText("vocab_no_questions_match_filter"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredList, key = { it.id }) { q ->
                    val options = q.getOptions()
                    val track = trackMap[q.trackId]
                    val accuracy = if (q.timesAnswered > 0) (q.timesCorrect * 100) / q.timesAnswered else null
                    val srBadge = com.example.util.SpacedRepetition.getStatusBadgeInfo(q.srNextReviewAt)
                    val srBadgeLabel = when (srBadge.badgeType) {
                        com.example.util.SpacedRepetition.BadgeType.NEW -> Loc.getText("vocab_sr_status_new")
                        com.example.util.SpacedRepetition.BadgeType.DUE -> Loc.getText("vocab_sr_status_due_today")
                        com.example.util.SpacedRepetition.BadgeType.SCHEDULED ->
                            Loc.getFormattedText("vocab_sr_status_in_days", srBadge.daysRemaining).toWesternDigits()
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Spaced Repetition Schedule Status Badge
                                    Surface(
                                        color = when (srBadge.badgeType) {
                                            com.example.util.SpacedRepetition.BadgeType.DUE -> dueColor.copy(alpha = badgeBgAlpha)
                                            com.example.util.SpacedRepetition.BadgeType.SCHEDULED -> scheduledColor.copy(alpha = badgeBgAlpha)
                                            com.example.util.SpacedRepetition.BadgeType.NEW -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = srBadgeLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (srBadge.badgeType) {
                                                com.example.util.SpacedRepetition.BadgeType.DUE -> dueColor
                                                com.example.util.SpacedRepetition.BadgeType.SCHEDULED -> scheduledColor
                                                com.example.util.SpacedRepetition.BadgeType.NEW -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    // Historical Accuracy Badge
                                    Surface(
                                        color = when {
                                            accuracy == null -> MaterialTheme.colorScheme.surfaceVariant
                                            accuracy >= 75 -> scheduledColor.copy(alpha = badgeBgAlpha)
                                            else -> dueColor.copy(alpha = badgeBgAlpha)
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (accuracy != null) Loc.getFormattedText("vocab_accuracy_format", accuracy, q.timesCorrect, q.timesAnswered) else Loc.getText("quiz_never_answered"),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                accuracy == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                                accuracy >= 75 -> scheduledColor
                                                else -> dueColor
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Row {
                                    if (q.trackId != null) {
                                        IconButton(
                                            onClick = { onPlaySnippet(q.trackId, q.timestampMs) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingAudio && currentlyPlayingTs == q.timestampMs) Icons.Filled.Pause else Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "Play context audio",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { questionToDelete = q },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = q.question,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (options.indices.contains(q.correctIndex)) {
                                Text(
                                    text = "Correct: ${options[q.correctIndex]}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (track != null) {
                                Text(
                                    text = "Lesson: ${track.getDisplayTitle()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (q.noteId != null) {
                                Text(
                                    text = Loc.getText("notebook_quiz_from_notebook_badge"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
