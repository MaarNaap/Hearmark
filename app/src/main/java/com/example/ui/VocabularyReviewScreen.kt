package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.QuizQuestion
import com.example.player.AudioPlayerManager
import com.example.ui.vocab.VocabActiveQuestionView
import com.example.ui.vocab.VocabBankBrowserView
import com.example.ui.vocab.VocabReviewEmptyState
import com.example.ui.vocab.VocabReviewHubView
import com.example.ui.vocab.VocabReviewSessionResults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyReviewSheet(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.96f)
    ) {
        VocabularyReviewContent(
            viewModel = viewModel,
            onClose = onDismiss,
            onNavigateToLibrary = {
                onDismiss()
                onNavigateToLibrary()
            },
            isOverlay = true
        )
    }
}

@Composable
fun VocabularyReviewScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    VocabularyReviewContent(
        viewModel = viewModel,
        onClose = onBack,
        onNavigateToLibrary = onNavigateToLibrary,
        isOverlay = false
    )
}

@Composable
fun VocabularyReviewContent(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    isOverlay: Boolean = false
) {
    val context = LocalContext.current
    val allVocabQuestions by viewModel.allVocabularyQuestions.collectAsStateWithLifecycle()
    val dueVocabQuestions by viewModel.dueVocabularyQuestions.collectAsStateWithLifecycle()
    val dueVocabCount by viewModel.dueVocabularyCount.collectAsStateWithLifecycle()
    val pendingAutoStartDueVocab by viewModel.pendingAutoStartDueVocab.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()

    // Map for quick track lookup
    val trackMap = remember(allTracks) {
        allTracks.associateBy { it.id }
    }

    // Main Tab Selection: 0: Review & Test Session, 1: Vocabulary Bank
    var selectedTab by remember { mutableIntStateOf(0) }
    var isShuffleActive by remember { mutableStateOf(false) }

    // Session State
    var isSessionActive by remember { mutableStateOf(false) }
    var activeSessionQuestions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var sessionAnswers by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) } // questionIndex -> selectedOptionIndex
    var isSessionFinished by remember { mutableStateOf(false) }

    // Handle back button when in active session: cleanly exit session back to hub first
    BackHandler(enabled = isSessionActive) {
        isSessionActive = false
        isSessionFinished = false
    }

    // Bank Search & Filter
    var bankSearchQuery by remember { mutableStateOf("") }
    var bankFilterMode by remember { mutableStateOf("ALL") } // ALL, MASTERED, NEEDS_PRACTICE, UNTESTED
    var questionToDelete by remember { mutableStateOf<QuizQuestion?>(null) }

    // Audio Playback
    val isPlayingAudio by AudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    var currentlyPlayingTs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(isPlayingAudio) {
        if (!isPlayingAudio) {
            currentlyPlayingTs = null
        }
    }

    val playTrackSnippet: (Long, Long?) -> Unit = { trackId, timestampMs ->
        val track = trackMap[trackId]
        if (track != null) {
            val ts = timestampMs ?: 0L
            if (isPlayingAudio && currentlyPlayingTs == ts && AudioPlayerManager.currentTrack.value?.id == trackId) {
                AudioPlayerManager.pause()
                currentlyPlayingTs = null
            } else {
                if (AudioPlayerManager.currentTrack.value?.id != trackId) {
                    viewModel.selectAndPlay(track)
                }
                AudioPlayerManager.seekTo(ts, isPhysicalTimestamp = true)
                AudioPlayerManager.resume()
                currentlyPlayingTs = ts
                Toast.makeText(context, Loc.getText("vocab_review_listen_snippet"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Stats calculations
    val totalCount = allVocabQuestions.size
    val masteredCount = remember(allVocabQuestions) {
        allVocabQuestions.count { it.timesAnswered > 0 && (it.timesCorrect.toFloat() / it.timesAnswered) >= 0.75f }
    }
    val needsPracticeCount = remember(allVocabQuestions) {
        allVocabQuestions.count { it.timesAnswered > 0 && (it.timesCorrect.toFloat() / it.timesAnswered) < 0.75f }
    }
    val untestedCount = remember(allVocabQuestions) {
        allVocabQuestions.count { it.timesAnswered == 0 }
    }

    // Helper to launch test session
    val startSession: (List<QuizQuestion>) -> Unit = { list ->
        if (list.isNotEmpty()) {
            val finalQuestions = if (isShuffleActive) list.shuffled() else list
            activeSessionQuestions = finalQuestions
            currentQuestionIndex = 0
            sessionAnswers = emptyMap()
            isSessionFinished = false
            isSessionActive = true
            selectedTab = 0
        }
    }

    LaunchedEffect(pendingAutoStartDueVocab, dueVocabQuestions) {
        if (pendingAutoStartDueVocab && dueVocabQuestions.isNotEmpty()) {
            val dueBatch = dueVocabQuestions.take(com.example.util.SpacedRepetition.SESSION_CARD_CAP)
            startSession(dueBatch)
            viewModel.consumePendingAutoStartDueVocab()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        if (isSessionActive) {
            if (isSessionFinished) {
                // Session Results Summary
                VocabReviewSessionResults(
                    questions = activeSessionQuestions,
                    userAnswers = sessionAnswers,
                    trackMap = trackMap,
                    onRetake = { startSession(activeSessionQuestions) },
                    onPracticeMistakes = {
                        val mistakes = activeSessionQuestions.filterIndexed { index, q ->
                            sessionAnswers[index] != q.correctIndex
                        }
                        if (mistakes.isNotEmpty()) {
                            startSession(mistakes)
                        } else {
                            isSessionActive = false
                        }
                    },
                    onBackToHub = {
                        isSessionActive = false
                        isSessionFinished = false
                    },
                    onPlaySnippet = playTrackSnippet,
                    currentlyPlayingTs = currentlyPlayingTs,
                    isPlayingAudio = isPlayingAudio
                )
            } else {
                // Active Question View - takes FULL HEIGHT, with no redundant headers on top!
                val currentQuestion = activeSessionQuestions.getOrNull(currentQuestionIndex)
                if (currentQuestion != null) {
                    VocabActiveQuestionView(
                        question = currentQuestion,
                        questionIndex = currentQuestionIndex,
                        totalQuestions = activeSessionQuestions.size,
                        selectedOptionIndex = sessionAnswers[currentQuestionIndex],
                        sourceTrackTitle = trackMap[currentQuestion.trackId]?.getDisplayTitle(),
                        onSelectOption = { chosenIdx ->
                            if (sessionAnswers[currentQuestionIndex] == null) {
                                sessionAnswers = sessionAnswers + (currentQuestionIndex to chosenIdx)
                                val isCorrect = chosenIdx == currentQuestion.correctIndex
                                viewModel.recordQuizAnswer(currentQuestion, isCorrect)
                            }
                        },
                        onNext = {
                            if (currentQuestionIndex < activeSessionQuestions.size - 1) {
                                currentQuestionIndex++
                            } else {
                                isSessionFinished = true
                            }
                        },
                        onPrevious = {
                            if (currentQuestionIndex > 0) {
                                currentQuestionIndex--
                            }
                        },
                        onExitSession = {
                            isSessionActive = false
                            isSessionFinished = false
                        },
                        onPlaySnippet = {
                            currentQuestion.trackId?.let { tid ->
                                playTrackSnippet(tid, currentQuestion.timestampMs)
                            }
                        },
                        isPlayingAudio = isPlayingAudio && currentlyPlayingTs == currentQuestion.timestampMs
                    )
                }
            }
        } else {
            // Header Bar (Hub & Bank Mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("vocab_review_back")
                    ) {
                        Icon(
                            imageVector = if (isOverlay) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = Loc.getText("vocab_review_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Shuffle Toggle Button
                IconButton(
                    onClick = {
                        isShuffleActive = !isShuffleActive
                        val msg = if (isShuffleActive) "Shuffle ON" else "Shuffle OFF"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("vocab_review_shuffle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = Loc.getText("vocab_review_shuffle"),
                        tint = if (isShuffleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (allVocabQuestions.isEmpty()) {
                VocabReviewEmptyState(onNavigateToLibrary = onNavigateToLibrary)
            } else {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Spellcheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(Loc.getText("vocab_review_tab_session"), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.School,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${Loc.getText("vocab_review_tab_bank")} (${allVocabQuestions.size})",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> {
                            VocabReviewHubView(
                                dueQuestions = dueVocabQuestions,
                                dueCount = dueVocabCount,
                                totalCount = totalCount,
                                masteredCount = masteredCount,
                                needsPracticeCount = needsPracticeCount,
                                untestedCount = untestedCount,
                                allQuestions = allVocabQuestions,
                                onStartDueToday = {
                                    val dueBatch = dueVocabQuestions.take(com.example.util.SpacedRepetition.SESSION_CARD_CAP)
                                    startSession(dueBatch)
                                },
                                onStartTestAll = { startSession(allVocabQuestions) },
                                onStartPracticeWeak = {
                                    val weakOrUntested = allVocabQuestions.filter { q ->
                                        q.timesAnswered == 0 || (q.timesCorrect.toFloat() / q.timesAnswered) < 0.75f
                                    }
                                    startSession(weakOrUntested)
                                },
                                onStartQuickQuiz = {
                                    val sample = allVocabQuestions.shuffled().take(10)
                                    startSession(sample)
                                }
                            )
                        }
                        1 -> {
                            VocabBankBrowserView(
                                allQuestions = allVocabQuestions,
                                trackMap = trackMap,
                                searchQuery = bankSearchQuery,
                                onSearchQueryChange = { bankSearchQuery = it },
                                filterMode = bankFilterMode,
                                onFilterModeChange = { bankFilterMode = it },
                                onDeleteQuestion = { questionToDelete = it },
                                onPlaySnippet = playTrackSnippet,
                                currentlyPlayingTs = currentlyPlayingTs,
                                isPlayingAudio = isPlayingAudio
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (questionToDelete != null) {
        val target = questionToDelete!!
        AlertDialog(
            onDismissRequest = { questionToDelete = null },
            title = { Text(Loc.getText("quiz_delete_question")) },
            text = { Text(Loc.getText("vocab_review_delete_confirm")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteQuizQuestion(target.id)
                        questionToDelete = null
                        Toast.makeText(context, Loc.getText("question_deleted_success"), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("delete_confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = { questionToDelete = null }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }
}
