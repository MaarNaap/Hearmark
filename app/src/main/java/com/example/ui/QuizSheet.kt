package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import com.example.data.QuizQuestion
import com.example.player.AudioPlayerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSheet(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val targetTrack by viewModel.activeQuizTargetTrack.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingQuiz.collectAsStateWithLifecycle()
    val generationError by viewModel.quizGenerationError.collectAsStateWithLifecycle()
    val quizBank by viewModel.currentTrackQuizQuestions.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Quiz Session State
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Quiz Session, 1: Question Bank
    var activeQuizQuestions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var selectedAnswers by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) } // questionIndex -> selectedOptionIndex
    var isQuizFinished by remember { mutableStateOf(false) }

    // Audio Playback Toggle State
    val isPlayingAudio by AudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    var currentlyPlayingTimestamp by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(isPlayingAudio) {
        if (!isPlayingAudio) {
            currentlyPlayingTimestamp = null
        }
    }

    val togglePlayAudio: (Long) -> Unit = { ts ->
        if (isPlayingAudio && currentlyPlayingTimestamp == ts) {
            AudioPlayerManager.pause()
            currentlyPlayingTimestamp = null
        } else {
            AudioPlayerManager.seekTo(ts)
            AudioPlayerManager.resume()
            currentlyPlayingTimestamp = ts
        }
    }

    // Start or restart a quiz session from available bank
    val startQuizSession: (List<QuizQuestion>) -> Unit = { questions ->
        if (questions.isNotEmpty()) {
            activeQuizQuestions = questions.shuffled().take(5)
            currentQuestionIndex = 0
            selectedAnswers = emptyMap()
            isQuizFinished = false
            selectedTab = 0
        }
    }

    var previousBankSize by remember { mutableIntStateOf(quizBank.size) }

    // Automatically initialize active session if questions become available or new batch is generated
    LaunchedEffect(quizBank) {
        if (quizBank.isNotEmpty()) {
            if (activeQuizQuestions.isEmpty() && !isQuizFinished) {
                startQuizSession(quizBank)
            } else if (isQuizFinished && quizBank.size > previousBankSize) {
                // When new questions are generated after finishing a quiz, immediately start with the new questions
                val newlyAddedCount = quizBank.size - previousBankSize
                val newlyAdded = quizBank.takeLast(newlyAddedCount)
                startQuizSession(if (newlyAdded.isNotEmpty()) newlyAdded else quizBank)
            }
        }
        previousBankSize = quizBank.size
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Quiz,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = Loc.getText("ai_quiz_title"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = targetTrack?.getDisplayTitle() ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_quiz_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tab Selector Row (Practice vs Bank)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text(Loc.getText("quiz_start_practice")) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = {
                        Text(
                            String.format(Loc.getText("quiz_bank_badge"), quizBank.size)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Content Area based on Tab
            if (selectedTab == 1) {
                // QUESTION BANK VIEW
                QuizBankTabContent(
                    quizBank = quizBank,
                    targetTrack = targetTrack,
                    isGenerating = isGenerating,
                    onStartPractice = { startQuizSession(quizBank) },
                    onGenerateNew = {
                        targetTrack?.let { viewModel.generateQuizForTrack(it) }
                    },
                    onDeleteQuestion = { questionId ->
                        viewModel.deleteQuizQuestion(questionId)
                    },
                    onClearBank = {
                        targetTrack?.let { viewModel.clearQuizBankForTrack(it.id) }
                    },
                    onTogglePlayAudio = togglePlayAudio,
                    currentlyPlayingTimestamp = currentlyPlayingTimestamp,
                    isAudioPlaying = isPlayingAudio
                )
            } else {
                // ACTIVE QUIZ SESSION VIEW
                when {
                    isGenerating -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = if (quizBank.isNotEmpty())
                                        Loc.getText("quiz_generating_more_status")
                                    else
                                        Loc.getText("quiz_generating_status"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    generationError != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = generationError ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = {
                                            targetTrack?.let { viewModel.generateQuizForTrack(it) }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text(Loc.getText("quiz_generate_with_ai"))
                                    }
                                }
                            }
                        }
                    }

                    quizBank.isEmpty() -> {
                        // Empty State - Generate Questions
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = Loc.getText("quiz_empty_bank_prompt"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = {
                                            targetTrack?.let { viewModel.generateQuizForTrack(it) }
                                        },
                                        modifier = Modifier.testTag("generate_first_quiz_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(Loc.getText("quiz_generate_new_batch"))
                                    }
                                }
                            }
                        }
                    }

                    isQuizFinished -> {
                        // QUIZ COMPLETED / RESULTS VIEW
                        QuizResultsSummaryView(
                            questions = activeQuizQuestions,
                            userAnswers = selectedAnswers,
                            onRetake = { startQuizSession(quizBank) },
                            onGenerateFresh = {
                                targetTrack?.let { viewModel.generateQuizForTrack(it) }
                            },
                            onViewBank = { selectedTab = 1 },
                            onTogglePlayAudio = togglePlayAudio,
                            currentlyPlayingTimestamp = currentlyPlayingTimestamp,
                            isAudioPlaying = isPlayingAudio,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    activeQuizQuestions.isNotEmpty() -> {
                        // ACTIVE QUESTION VIEW
                        val currentQuestion = activeQuizQuestions.getOrNull(currentQuestionIndex)
                        if (currentQuestion != null) {
                            ActiveQuizQuestionCard(
                                question = currentQuestion,
                                questionIndex = currentQuestionIndex,
                                totalQuestions = activeQuizQuestions.size,
                                selectedOptionIndex = selectedAnswers[currentQuestionIndex],
                                onSelectOption = { optionIdx ->
                                    if (selectedAnswers[currentQuestionIndex] == null) {
                                        selectedAnswers = selectedAnswers + (currentQuestionIndex to optionIdx)
                                        val isCorrect = optionIdx == currentQuestion.correctIndex
                                        viewModel.recordQuizAnswer(currentQuestion, isCorrect)
                                    }
                                },
                                onNext = {
                                    if (currentQuestionIndex < activeQuizQuestions.size - 1) {
                                        currentQuestionIndex++
                                    } else {
                                        isQuizFinished = true
                                    }
                                },
                                onPrevious = {
                                    if (currentQuestionIndex > 0) {
                                        currentQuestionIndex--
                                    }
                                },
                                onTogglePlayAudio = togglePlayAudio,
                                isAudioPlayingForThis = isPlayingAudio && currentlyPlayingTimestamp == currentQuestion.timestampMs,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveQuizQuestionCard(
    question: QuizQuestion,
    questionIndex: Int,
    totalQuestions: Int,
    selectedOptionIndex: Int?,
    onSelectOption: (Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayAudio: (Long) -> Unit,
    isAudioPlayingForThis: Boolean,
    modifier: Modifier = Modifier
) {
    val options = remember(question) { question.getOptions() }
    val isAnswered = selectedOptionIndex != null
    val isCorrect = selectedOptionIndex == question.correctIndex
    val scrollState = rememberScrollState()

    // Smoothly reveal feedback and audio button when the question is answered
    LaunchedEffect(isAnswered) {
        if (isAnswered) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        // Scrollable content area: Counter, Question, Options, and Feedback
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Progress Bar & Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Loc.getText("quiz_question_counter"), questionIndex + 1, totalQuestions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { (questionIndex + 1).toFloat() / totalQuestions },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )

            // Question Text Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = question.question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(14.dp),
                    lineHeight = 22.sp
                )
            }

            // Options List (Interactive Click-to-Answer)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEachIndexed { index, optionText ->
                    val isSelected = selectedOptionIndex == index
                    val isOptionCorrect = index == question.correctIndex

                    val containerColor = when {
                        !isAnswered -> MaterialTheme.colorScheme.surface
                        isSelected && isOptionCorrect -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                        isSelected && !isOptionCorrect -> Color(0xFFC62828).copy(alpha = 0.15f)
                        isOptionCorrect -> Color(0xFF2E7D32).copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    }

                    val borderColor = when {
                        !isAnswered -> MaterialTheme.colorScheme.outlineVariant
                        isSelected && isOptionCorrect -> Color(0xFF2E7D32)
                        isSelected && !isOptionCorrect -> Color(0xFFC62828)
                        isOptionCorrect -> Color(0xFF2E7D32)
                        else -> Color.Transparent
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isAnswered) {
                                onSelectOption(index)
                            }
                            .testTag("quiz_option_$index"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        border = BorderStroke(if (isAnswered && (isSelected || isOptionCorrect)) 1.5.dp else 1.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Letter Tag (A, B, C, D)
                                Surface(
                                    color = when {
                                        isSelected && isOptionCorrect -> Color(0xFF2E7D32)
                                        isSelected && !isOptionCorrect -> Color(0xFFC62828)
                                        isAnswered && isOptionCorrect -> Color(0xFF2E7D32)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val letter = when (index) {
                                            0 -> "A"
                                            1 -> "B"
                                            2 -> "C"
                                            3 -> "D"
                                            else -> "${index + 1}"
                                        }
                                        Text(
                                            text = letter,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isAnswered && (isSelected || isOptionCorrect)) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = optionText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isAnswered && (isSelected || isOptionCorrect)) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            if (isAnswered) {
                                if (isSelected && isOptionCorrect) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Correct",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (isSelected && !isOptionCorrect) {
                                    Icon(
                                        imageVector = Icons.Filled.Cancel,
                                        contentDescription = "Incorrect",
                                        tint = Color(0xFFC62828),
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (isOptionCorrect) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Correct Answer",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Feedback & Audio Link Area
            AnimatedVisibility(
                visible = isAnswered,
                enter = fadeIn() + expandVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCorrect)
                            Color(0xFF2E7D32).copy(alpha = 0.08f)
                        else
                            Color(0xFFC62828).copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isCorrect) Color(0xFF2E7D32).copy(alpha = 0.3f) else Color(0xFFC62828).copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isCorrect) Icons.Filled.CheckCircle else Icons.Filled.Lightbulb,
                                contentDescription = null,
                                tint = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isCorrect)
                                    Loc.getText("quiz_feedback_correct")
                                else
                                    Loc.getText("quiz_feedback_incorrect"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }

                        if (question.explanation.isNotBlank()) {
                            Text(
                                text = question.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Audio Timestamp Link Button (Play / Pause Toggle)
                        if (question.timestampMs != null && question.timestampMs >= 0) {
                            OutlinedButton(
                                onClick = { onTogglePlayAudio(question.timestampMs) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("quiz_jump_audio_button"),
                                colors = if (isAudioPlayingForThis) {
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAudioPlayingForThis) Icons.Filled.Pause else Icons.Filled.Headphones,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isAudioPlayingForThis) {
                                        String.format(
                                            Loc.getText("quiz_pause_line"),
                                            formatTimestampDisplay(question.timestampMs)
                                        )
                                    } else {
                                        String.format(
                                            Loc.getText("quiz_jump_and_listen"),
                                            formatTimestampDisplay(question.timestampMs)
                                        )
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Pinned Bottom Navigation Footer with subtle top divider (Always visible on screen)
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (questionIndex > 0) {
                OutlinedButton(
                    onClick = onPrevious,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Loc.getText("quiz_prev_question"))
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = onNext,
                enabled = isAnswered,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("quiz_next_button")
            ) {
                Text(
                    text = if (questionIndex == totalQuestions - 1)
                        Loc.getText("quiz_finish_action")
                    else
                        Loc.getText("quiz_next_question")
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun QuizResultsSummaryView(
    questions: List<QuizQuestion>,
    userAnswers: Map<Int, Int>,
    onRetake: () -> Unit,
    onGenerateFresh: () -> Unit,
    onViewBank: () -> Unit,
    onTogglePlayAudio: (Long) -> Unit,
    currentlyPlayingTimestamp: Long?,
    isAudioPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val total = questions.size
    val correctCount = questions.indices.count { idx ->
        userAnswers[idx] == questions[idx].correctIndex
    }
    val percentage = if (total > 0) ((correctCount.toFloat() / total) * 100).toInt() else 0

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // Score Celebration Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                if (percentage >= 70) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color(0xFFE65100).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (percentage >= 70) Icons.Filled.EmojiEvents else Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = if (percentage >= 70) Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Text(
                        text = Loc.getText("quiz_completed_title"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = String.format(Loc.getText("quiz_score_summary"), correctCount, total, percentage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Loc.getText("quiz_retake"))
                }

                OutlinedButton(
                    onClick = onGenerateFresh,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Loc.getText("quiz_generate_new_batch"))
                }
            }
        }

        // Review list of questions in this round
        itemsIndexed(questions) { index, q ->
            val userAns = userAnswers[index]
            val isQCorrect = userAns == q.correctIndex
            val options = q.getOptions()

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isQCorrect) Color(0xFF2E7D32).copy(alpha = 0.4f) else Color(0xFFC62828).copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Q${index + 1}: ${q.question}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isQCorrect) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = if (isQCorrect) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (userAns != null && options.indices.contains(userAns)) {
                        Text(
                            text = "Your answer: ${options[userAns]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isQCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }

                    if (!isQCorrect && options.indices.contains(q.correctIndex)) {
                        Text(
                            text = "Correct answer: ${options[q.correctIndex]}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    if (q.timestampMs != null && q.timestampMs >= 0) {
                        val isThisPlaying = isAudioPlaying && currentlyPlayingTimestamp == q.timestampMs
                        TextButton(
                            onClick = { onTogglePlayAudio(q.timestampMs) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.Headphones,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isThisPlaying) Loc.getText("pause") else String.format(Loc.getText("quiz_listen_at"), formatTimestampDisplay(q.timestampMs)),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizBankTabContent(
    quizBank: List<QuizQuestion>,
    targetTrack: AudioTrack?,
    isGenerating: Boolean,
    onStartPractice: () -> Unit,
    onGenerateNew: () -> Unit,
    onDeleteQuestion: (Long) -> Unit,
    onClearBank: () -> Unit,
    onTogglePlayAudio: (Long) -> Unit,
    currentlyPlayingTimestamp: Long?,
    isAudioPlaying: Boolean
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(Loc.getText("quiz_clear_bank")) },
            text = { Text(Loc.getText("quiz_clear_bank_confirm")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearBank()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Bank Stats & Action Controls Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = Loc.getText("quiz_view_bank"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${quizBank.size} questions saved offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (quizBank.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Clear Bank",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onGenerateNew,
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Loc.getText("quiz_generate_more_button"), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (isGenerating) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = Loc.getText("quiz_generating_more_status"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (quizBank.isNotEmpty()) {
            Button(
                onClick = onStartPractice,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(Loc.getText("quiz_start_practice"))
            }
        }

        // List of stored questions in the bank
        if (quizBank.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Loc.getText("quiz_empty_bank_prompt"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(quizBank, key = { _, item -> item.id }) { index, q ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = q.question,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )

                                        val accuracyPercent = if (q.timesAnswered > 0) {
                                            ((q.timesCorrect.toFloat() / q.timesAnswered) * 100).toInt()
                                        } else null

                                        Text(
                                            text = if (accuracyPercent != null)
                                                String.format(Loc.getText("quiz_accuracy_rate"), accuracyPercent, q.timesCorrect, q.timesAnswered)
                                            else
                                                Loc.getText("quiz_never_answered"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteQuestion(q.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (q.timestampMs != null && q.timestampMs >= 0) {
                                val isThisPlaying = isAudioPlaying && currentlyPlayingTimestamp == q.timestampMs
                                TextButton(
                                    onClick = { onTogglePlayAudio(q.timestampMs) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.Headphones,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isThisPlaying) Loc.getText("pause") else formatTimestampDisplay(q.timestampMs),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestampDisplay(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
