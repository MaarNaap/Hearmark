package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import com.example.data.Note
import com.example.data.QuizQuestion
import com.example.player.AudioPlayerManager
import com.example.ui.quiz.*
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedQuizSheet(
    quizViewModel: QuizViewModel,
    allNotes: List<Note>,
    allTracks: List<AudioTrack>,
    onDismiss: () -> Unit,
    onOpenVocabularyReview: (() -> Unit)? = null,
    onPlayTrackInMainPlayer: ((AudioTrack, Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Current Mode / Tab from ViewModel
    val activeTabMode by quizViewModel.activeQuizTabMode.collectAsStateWithLifecycle()
    val targetTrack by quizViewModel.activeQuizTargetTrack.collectAsStateWithLifecycle()
    val initialNotes by quizViewModel.unifiedQuizInitialNotes.collectAsStateWithLifecycle()

    // Track Quiz State
    val isGeneratingTrackQuiz by quizViewModel.isGeneratingQuiz.collectAsStateWithLifecycle()
    val trackQuizError by quizViewModel.quizGenerationError.collectAsStateWithLifecycle()
    val trackQuizSuccess by quizViewModel.quizGenerationSuccessMessage.collectAsStateWithLifecycle()
    val trackQuizBank by quizViewModel.currentTrackQuizQuestions.collectAsStateWithLifecycle()
    val allVocabQuestions by quizViewModel.allVocabularyQuestions.collectAsStateWithLifecycle()

    // Notebook Quiz State
    val isGeneratingNotebookQuiz by quizViewModel.isGeneratingNotebookQuiz.collectAsStateWithLifecycle()
    val notebookQuizError by quizViewModel.notebookQuizError.collectAsStateWithLifecycle()
    val notebookQuizGeneratedQuestions by quizViewModel.notebookQuizGeneratedQuestions.collectAsStateWithLifecycle()
    val notebookQuizSuccessMessage by quizViewModel.notebookQuizSuccessMessage.collectAsStateWithLifecycle()

    // Audio Playback
    val isPlayingAudio by AudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    var currentlyPlayingTs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(isPlayingAudio) {
        if (!isPlayingAudio) currentlyPlayingTs = null
    }

    val togglePlayAudio: (Long, Long?) -> Unit = { ts, trackId ->
        if (isPlayingAudio && currentlyPlayingTs == ts) {
            AudioPlayerManager.pause()
            currentlyPlayingTs = null
        } else {
            val track = if (trackId != null) allTracks.find { it.id == trackId } else targetTrack
            if (track != null && AudioPlayerManager.currentTrack.value?.id != track.id) {
                onPlayTrackInMainPlayer?.invoke(track, ts) ?: AudioPlayerManager.seekTo(ts)
            } else {
                AudioPlayerManager.seekTo(ts)
                AudioPlayerManager.resume()
            }
            currentlyPlayingTs = ts
        }
    }

    // Active Quiz Session Runner State (shared across all modes, managed by ViewModel)
    val isSessionActive by quizViewModel.isSessionActive.collectAsStateWithLifecycle()
    val sessionQuestions by quizViewModel.sessionQuestions.collectAsStateWithLifecycle()
    val currentQuestionIndex by quizViewModel.currentQuestionIndex.collectAsStateWithLifecycle()
    val userAnswers by quizViewModel.userAnswers.collectAsStateWithLifecycle()
    val isQuizFinished by quizViewModel.isQuizFinished.collectAsStateWithLifecycle()

    // Auto-launch session when track quiz generation succeeds
    LaunchedEffect(trackQuizBank) {
        if (trackQuizBank.isNotEmpty() && !isSessionActive && !isQuizFinished && activeTabMode == QuizTabMode.TRACK) {
            // keep bank ready
        }
    }

    // Feedback toasts
    LaunchedEffect(trackQuizError) {
        trackQuizError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(trackQuizSuccess) {
        trackQuizSuccess?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(notebookQuizError) {
        notebookQuizError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(notebookQuizSuccessMessage) {
        notebookQuizSuccessMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            QuizSheetHeader(
                activeTabMode = activeTabMode,
                targetTrack = targetTrack,
                isSessionActive = isSessionActive,
                onOpenVocabularyReview = onOpenVocabularyReview,
                onDismiss = onDismiss
            )

            QuizModeTabs(
                activeTabMode = activeTabMode,
                onSelectMode = { mode -> quizViewModel.setQuizTabMode(mode) }
            )

            // Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (activeTabMode) {
                    QuizTabMode.TRACK -> {
                        TrackQuizTabContent(
                            targetTrack = targetTrack,
                            allTracks = allTracks,
                            trackBankCount = trackQuizBank.size,
                            isGenerating = isGeneratingTrackQuiz,
                            errorMessage = trackQuizError,
                            onSelectTrack = { track -> quizViewModel.selectTrack(track) },
                            onGenerateQuiz = { count ->
                                targetTrack?.let { quizViewModel.generateQuizForTrack(it, count) }
                            },
                            onStartPractice = {
                                if (trackQuizBank.isNotEmpty()) {
                                    quizViewModel.startQuizSession(trackQuizBank)
                                }
                            }
                        )
                    }

                    QuizTabMode.NOTEBOOK -> {
                        NotebookQuizTabContent(
                            allNotes = allNotes,
                            initialNotes = initialNotes,
                            isGenerating = isGeneratingNotebookQuiz,
                            errorMessage = notebookQuizError,
                            generatedQuestions = notebookQuizGeneratedQuestions,
                            onGenerateQuiz = { selectedNotes, count ->
                                quizViewModel.generateQuizFromNotes(selectedNotes, count) {
                                    // When done, switch to bank or auto start session
                                }
                            },
                            onStartPracticeGenerated = { items ->
                                val entities = items.map { item ->
                                    QuizQuestion(
                                        id = item.savedQuestionId ?: 0L,
                                        trackId = item.trackId,
                                        noteId = item.sourceNoteId,
                                        questionType = item.questionType,
                                        category = "VOCABULARY",
                                        question = item.question,
                                        optionsJson = JSONArray(item.options).toString(),
                                        correctIndex = item.correctIndex,
                                        explanation = item.explanation,
                                        timestampMs = item.timestampMs,
                                        targetWord = item.targetWord,
                                        meaning = item.meaning,
                                        contextSentence = item.contextSentence
                                    )
                                }
                                quizViewModel.startQuizSession(entities)
                            },
                            onOpenVocabReview = onOpenVocabularyReview
                        )
                    }

                    QuizTabMode.BANK -> {
                        if (isSessionActive && sessionQuestions.isNotEmpty()) {
                            if (isQuizFinished) {
                                QuizSessionResultsView(
                                    questions = sessionQuestions,
                                    userAnswers = userAnswers,
                                    onRetake = { quizViewModel.retakeSession() },
                                    onExitSession = { quizViewModel.exitSession() },
                                    onTogglePlayAudio = togglePlayAudio,
                                    isPlayingAudio = isPlayingAudio,
                                    currentlyPlayingTs = currentlyPlayingTs
                                )
                            } else {
                                QuizSessionActiveRunner(
                                    questions = sessionQuestions,
                                    currentIndex = currentQuestionIndex,
                                    userAnswers = userAnswers,
                                    onAnswerSelected = { qIndex, optIndex ->
                                        quizViewModel.recordUserAnswer(qIndex, optIndex)
                                    },
                                    onNext = { quizViewModel.nextQuestion() },
                                    onPrevious = { quizViewModel.previousQuestion() },
                                    onTogglePlayAudio = togglePlayAudio,
                                    isPlayingAudio = isPlayingAudio,
                                    currentlyPlayingTs = currentlyPlayingTs,
                                    onExitSession = { quizViewModel.exitSession() }
                                )
                            }
                        } else {
                            QuizBankBrowserView(
                                targetTrack = targetTrack,
                                trackQuestions = trackQuizBank,
                                allVocabQuestions = allVocabQuestions,
                                onStartPractice = { list -> quizViewModel.startQuizSession(list) },
                                onDeleteQuestion = { id -> quizViewModel.deleteQuizQuestion(id) },
                                onClearTrackBank = { id -> quizViewModel.clearQuizBankForTrack(id) },
                                onTogglePlayAudio = togglePlayAudio,
                                isPlayingAudio = isPlayingAudio,
                                currentlyPlayingTs = currentlyPlayingTs,
                                onNavigateToTab = { mode -> quizViewModel.setQuizTabMode(mode) }
                            )
                        }
                    }
                }
            }
        }
    }
}
