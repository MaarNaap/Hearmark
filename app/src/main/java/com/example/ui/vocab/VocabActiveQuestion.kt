package com.example.ui.vocab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.QuizQuestion
import com.example.ui.Loc

@Composable
fun VocabActiveQuestionView(
    question: QuizQuestion,
    questionIndex: Int,
    totalQuestions: Int,
    selectedOptionIndex: Int?,
    sourceTrackTitle: String?,
    onSelectOption: (Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExitSession: () -> Unit,
    onPlaySnippet: () -> Unit,
    isPlayingAudio: Boolean
) {
    val options = remember(question) { question.getOptions() }
    val isAnswered = selectedOptionIndex != null
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vocab_active_question_screen")
    ) {
        // Minimal Top Bar: Exit button, question counter, audio context button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onExitSession,
                modifier = Modifier.testTag("vocab_exit_session_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Exit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${questionIndex + 1} / $totalQuestions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (question.trackId != null) {
                IconButton(
                    onClick = onPlaySnippet,
                    modifier = Modifier.testTag("vocab_listen_snippet_btn")
                ) {
                    Icon(
                        imageVector = if (isPlayingAudio) Icons.Filled.Pause else Icons.Filled.VolumeUp,
                        contentDescription = "Listen to audio context",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // Sleek thin progress indicator
        LinearProgressIndicator(
            progress = { (questionIndex + 1).toFloat() / totalQuestions },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )

        // SCROLLABLE CONTENT COLUMN (Ensures all options & explanations are always reachable and clearly visible)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Optional subtle source note/lesson label without any bulky 'Vocabulary' badge
            if (!sourceTrackTitle.isNullOrBlank()) {
                Text(
                    text = sourceTrackTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (question.noteId != null) {
                Text(
                    text = Loc.getText("notebook_quiz_from_notebook_badge"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Question Text Card (Clean, modern typography, spacious)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = question.question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(18.dp),
                    lineHeight = 24.sp
                )
            }

            // Options List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                options.forEachIndexed { idx, optionText ->
                    val isSelected = selectedOptionIndex == idx
                    val isCorrect = idx == question.correctIndex

                    val containerColor = when {
                        !isAnswered -> MaterialTheme.colorScheme.surface
                        isSelected && isCorrect -> if (isDark) Color(0xFF1B3820) else Color(0xFFE8F5E9)
                        isSelected && !isCorrect -> if (isDark) Color(0xFF381B1B) else Color(0xFFFFEBEE)
                        isCorrect -> if (isDark) Color(0xFF1B3820) else Color(0xFFE8F5E9)
                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    }

                    val borderColor = when {
                        !isAnswered && isSelected -> MaterialTheme.colorScheme.primary
                        !isAnswered -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        isSelected && isCorrect -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                        isSelected && !isCorrect -> if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
                        isCorrect -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                        else -> Color.Transparent
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isAnswered) { onSelectOption(idx) }
                            .testTag("vocab_option_$idx"),
                        shape = RoundedCornerShape(14.dp),
                        color = containerColor,
                        border = BorderStroke(1.5.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val letter = ('A' + idx).toString()
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        when {
                                            isSelected && isCorrect -> if (isDark) Color(0xFF2E7D32) else Color(0xFF388E3C)
                                            isSelected && !isCorrect -> if (isDark) Color(0xFFC62828) else Color(0xFFD32F2F)
                                            isCorrect && isAnswered -> if (isDark) Color(0xFF2E7D32) else Color(0xFF388E3C)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAnswered && (isSelected || isCorrect)) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = optionText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            if (isAnswered) {
                                if (isCorrect) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Correct",
                                        tint = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Cancel,
                                        contentDescription = "Incorrect",
                                        tint = if (isDark) Color(0xFFE57373) else Color(0xFFC62828),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Explanation Card (Visible after answering)
            if (isAnswered && question.explanation.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = question.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Fixed Clean Bottom Controls
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onPrevious,
                    enabled = questionIndex > 0
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Loc.getText("quiz_prev_question"))
                }

                Button(
                    onClick = onNext,
                    enabled = isAnswered,
                    modifier = Modifier.testTag("vocab_btn_next")
                ) {
                    Text(
                        text = if (questionIndex == totalQuestions - 1) Loc.getText("quiz_finish_action") else Loc.getText("quiz_next_question")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}
