package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.example.data.Note
import com.example.data.QuizQuestion
import com.example.player.AudioPlayerManager
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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
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
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = Loc.getText("unified_quiz_hub_title"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (activeTabMode) {
                                QuizTabMode.TRACK -> targetTrack?.getDisplayTitle() ?: Loc.getText("unified_quiz_tab_track")
                                QuizTabMode.NOTEBOOK -> Loc.getText("notebook_quiz_sheet_subtitle")
                                QuizTabMode.BANK -> if (isSessionActive) Loc.getText("unified_quiz_active_session") else Loc.getText("unified_quiz_tab_bank")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onOpenVocabularyReview != null) {
                        IconButton(
                            onClick = {
                                onDismiss()
                                onOpenVocabularyReview()
                            },
                            modifier = Modifier.testTag("unified_quiz_vocab_review_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Spellcheck,
                                contentDescription = Loc.getText("vocab_review_title"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_unified_quiz_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Mode Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tab 0: Subtitles
                FilterChip(
                    selected = activeTabMode == QuizTabMode.TRACK,
                    onClick = { quizViewModel.setQuizTabMode(QuizTabMode.TRACK) },
                    label = { Text(Loc.getText("unified_quiz_tab_track"), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Subtitles,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                // Tab 1: Notebook Notes
                FilterChip(
                    selected = activeTabMode == QuizTabMode.NOTEBOOK,
                    onClick = { quizViewModel.setQuizTabMode(QuizTabMode.NOTEBOOK) },
                    label = { Text(Loc.getText("unified_quiz_tab_notes"), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                // Tab 2: Practice
                FilterChip(
                    selected = activeTabMode == QuizTabMode.BANK,
                    onClick = { quizViewModel.setQuizTabMode(QuizTabMode.BANK) },
                    label = {
                        Text(Loc.getText("unified_quiz_tab_bank"), fontSize = 12.sp)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Quiz,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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

// -------------------------------------------------------------
// TAB 0: TRACK QUIZ TAB CONTENT
// -------------------------------------------------------------
@Composable
private fun TrackQuizTabContent(
    targetTrack: AudioTrack?,
    allTracks: List<AudioTrack>,
    trackBankCount: Int,
    isGenerating: Boolean,
    errorMessage: String?,
    onSelectTrack: (AudioTrack) -> Unit,
    onGenerateQuiz: (Int) -> Unit,
    onStartPractice: () -> Unit
) {
    var isTrackMenuExpanded by remember { mutableStateOf(false) }
    var requestedTrackQuestionCount by remember { mutableIntStateOf(4) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        // Track selection card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = Loc.getText("unified_quiz_select_track"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Audiotrack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = targetTrack?.getDisplayTitle() ?: Loc.getText("unified_quiz_no_track_selected"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (allTracks.size > 1) {
                            Box {
                                OutlinedButton(
                                    onClick = { isTrackMenuExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(Loc.getText("unified_quiz_switch_track"), fontSize = 11.5.sp)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = isTrackMenuExpanded,
                                    onDismissRequest = { isTrackMenuExpanded = false }
                                ) {
                                    allTracks.forEach { track ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    track.getDisplayTitle(),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = if (track.id == targetTrack?.id) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                isTrackMenuExpanded = false
                                                onSelectTrack(track)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = Loc.getText("unified_quiz_track_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Status / In-progress generating card
        if (isGenerating) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text(
                            text = Loc.getText("quiz_generating_status"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Error banner
        if (errorMessage != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Number of questions stepper
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = Loc.getText("notebook_quiz_question_count"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = Loc.getText("notebook_quiz_count_range_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { if (requestedTrackQuestionCount > 1) requestedTrackQuestionCount-- },
                            enabled = requestedTrackQuestionCount > 1,
                            modifier = Modifier.size(38.dp).testTag("dec_track_quiz_count_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "Decrease count",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.widthIn(min = 44.dp)
                        ) {
                            Text(
                                text = "$requestedTrackQuestionCount",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { if (requestedTrackQuestionCount < 20) requestedTrackQuestionCount++ },
                            enabled = requestedTrackQuestionCount < 20,
                            modifier = Modifier.size(38.dp).testTag("inc_track_quiz_count_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Increase count",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onGenerateQuiz(requestedTrackQuestionCount) },
                    enabled = targetTrack != null && !isGenerating,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_audio_quiz_btn")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Loc.getText("unified_quiz_generate_audio_action"), fontWeight = FontWeight.Bold)
                }

                if (trackBankCount > 0) {
                    OutlinedButton(
                        onClick = onStartPractice,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("practice_track_bank_btn")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(String.format(Loc.getText("unified_quiz_practice_track_badge"), trackBankCount), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: NOTEBOOK QUIZ TAB CONTENT
// -------------------------------------------------------------
@Composable
private fun NotebookQuizTabContent(
    allNotes: List<Note>,
    initialNotes: List<Note>?,
    isGenerating: Boolean,
    errorMessage: String?,
    generatedQuestions: List<com.example.ai.GeneratedNoteQuizItem>,
    onGenerateQuiz: (List<Note>, Int) -> Unit,
    onStartPracticeGenerated: (List<com.example.ai.GeneratedNoteQuizItem>) -> Unit,
    onOpenVocabReview: (() -> Unit)?
) {
    var selectedNoteIds by remember(initialNotes) {
        mutableStateOf(
            if (!initialNotes.isNullOrEmpty()) initialNotes.map { it.id }.toSet()
            else allNotes.map { it.id }.toSet()
        )
    }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var requestedCount by remember { mutableIntStateOf(4) }

    val allTags = remember(allNotes) {
        allNotes.flatMap { it.getTagsList() }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    val filteredNotes = remember(allNotes, selectedTagFilter, searchQuery) {
        allNotes.filter { note ->
            val matchTag = selectedTagFilter == null || note.getTagsList().any { it.equals(selectedTagFilter, ignoreCase = true) }
            val matchSearch = searchQuery.isBlank() || note.text.contains(searchQuery, ignoreCase = true) || note.comment.contains(searchQuery, ignoreCase = true)
            matchTag && matchSearch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        // Generated results banner
        if (generatedQuestions.isNotEmpty() && !isGenerating) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = String.format(Loc.getText("notebook_quiz_saved_success"), generatedQuestions.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { onStartPracticeGenerated(generatedQuestions) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Loc.getText("unified_quiz_start_session"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Tag filter chips
        if (allTags.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedTagFilter == null,
                            onClick = { selectedTagFilter = null },
                            label = { Text(Loc.getText("notebook_quiz_all_tags"), fontSize = 11.5.sp) }
                        )
                    }
                    items(allTags) { tag ->
                        FilterChip(
                            selected = selectedTagFilter == tag,
                            onClick = { selectedTagFilter = if (selectedTagFilter == tag) null else tag },
                            label = { Text("#$tag", fontSize = 11.5.sp) }
                        )
                    }
                }
            }
        }

        // Count picker
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = Loc.getText("notebook_quiz_question_count"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = Loc.getText("notebook_quiz_count_range_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { if (requestedCount > 1) requestedCount-- },
                            enabled = requestedCount > 1,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("dec_quiz_count_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "Decrease count",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.widthIn(min = 44.dp)
                        ) {
                            Text(
                                text = "$requestedCount",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { if (requestedCount < 20) requestedCount++ },
                            enabled = requestedCount < 20,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("inc_quiz_count_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Increase count",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Note selection list
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${Loc.getText("notebook_quiz_target_notes")} (${selectedNoteIds.size}/${allNotes.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { selectedNoteIds = allNotes.map { it.id }.toSet() },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(Loc.getText("notebook_quiz_select_all"), fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = { selectedNoteIds = emptySet() },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(Loc.getText("notebook_quiz_deselect_all"), fontSize = 11.sp)
                    }
                }
            }
        }

        // Notes items
        items(filteredNotes.take(12), key = { it.id }) { note ->
            val isChecked = selectedNoteIds.contains(note.id)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(0.5.dp, if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedNoteIds = if (isChecked) selectedNoteIds - note.id else selectedNoteIds + note.id
                    }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            selectedNoteIds = if (checked) selectedNoteIds + note.id else selectedNoteIds - note.id
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(note.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (note.comment.isNotBlank()) {
                            Text(note.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Generate Action Button
        item {
            Button(
                onClick = {
                    val targetNotes = allNotes.filter { selectedNoteIds.contains(it.id) }
                    if (targetNotes.isNotEmpty()) {
                        onGenerateQuiz(targetNotes, requestedCount)
                    }
                },
                enabled = selectedNoteIds.isNotEmpty() && !isGenerating,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("notebook_quiz_generate_action_btn")
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Loc.getText("notebook_quiz_generating"))
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Loc.getText("notebook_quiz_generate_action"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: ACTIVE QUIZ RUNNER COMPONENT
// -------------------------------------------------------------
@Composable
private fun QuizSessionActiveRunner(
    questions: List<QuizQuestion>,
    currentIndex: Int,
    userAnswers: Map<Int, Int>,
    onAnswerSelected: (Int, Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayAudio: (Long, Long?) -> Unit,
    isPlayingAudio: Boolean,
    currentlyPlayingTs: Long?,
    onExitSession: () -> Unit
) {
    val currentQuestion = questions.getOrNull(currentIndex) ?: return
    val selectedOption = userAnswers[currentIndex]
    val isAnswered = selectedOption != null

    val options = remember(currentQuestion) {
        try {
            val arr = JSONArray(currentQuestion.optionsJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList<String>()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            // Counter & exit bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Loc.getText("unified_quiz_question_counter"), currentIndex + 1, questions.size),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onExitSession, contentPadding = PaddingValues(0.dp)) {
                        Text(Loc.getText("cancel"), fontSize = 12.sp)
                    }
                }
            }

            // Question card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = currentQuestion.question,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Audio context pill if timestamp is available
                        if (currentQuestion.timestampMs != null && currentQuestion.timestampMs!! > 0) {
                            FilledTonalButton(
                                onClick = { onTogglePlayAudio(currentQuestion.timestampMs!!, currentQuestion.trackId) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                val isThisPlaying = isPlayingAudio && currentlyPlayingTs == currentQuestion.timestampMs
                                Icon(if (isThisPlaying) Icons.Filled.Pause else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(Loc.getText("vocab_review_listen_snippet"), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Options
            itemsIndexed(options) { idx, optionText ->
                val isSelected = selectedOption == idx
                val isCorrect = idx == currentQuestion.correctIndex
                val containerColor = when {
                    !isAnswered -> MaterialTheme.colorScheme.surface
                    isCorrect -> MaterialTheme.colorScheme.primaryContainer
                    isSelected -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                val borderColor = when {
                    !isAnswered && isSelected -> MaterialTheme.colorScheme.primary
                    isAnswered && isCorrect -> MaterialTheme.colorScheme.primary
                    isAnswered && isSelected -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = containerColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isAnswered) { onAnswerSelected(currentIndex, idx) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isAnswered && isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ('A' + idx).toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isAnswered && isCorrect) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(text = optionText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Explanation Card
            if (isAnswered && currentQuestion.explanation.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(Loc.getText("note_comment"), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                            Text(currentQuestion.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Navigation Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, enabled = currentIndex > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
            }

            Button(
                onClick = onNext,
                enabled = isAnswered,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (currentIndex < questions.size - 1) Loc.getText("next") else Loc.getText("finish"))
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: RESULTS VIEW
// -------------------------------------------------------------
@Composable
private fun QuizSessionResultsView(
    questions: List<QuizQuestion>,
    userAnswers: Map<Int, Int>,
    onRetake: () -> Unit,
    onExitSession: () -> Unit,
    onTogglePlayAudio: (Long, Long?) -> Unit,
    isPlayingAudio: Boolean,
    currentlyPlayingTs: Long?
) {
    val total = questions.size
    val correct = questions.indices.count { idx -> userAnswers[idx] == questions[idx].correctIndex }
    val percentage = if (total > 0) (correct.toFloat() / total * 100).toInt() else 0

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("$percentage%", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }

        Text(
            text = Loc.getText("unified_quiz_results_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "$correct / $total ${Loc.getText("correct_label")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRetake,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Loc.getText("unified_quiz_retake"))
            }

            OutlinedButton(
                onClick = onExitSession,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(Loc.getText("done"))
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: BANK BROWSER VIEW
// -------------------------------------------------------------
@Composable
private fun QuizBankBrowserView(
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
    val displayList = if (viewAllVocab) allVocabQuestions else trackQuestions

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
                        label = { Text(String.format(Loc.getText("unified_quiz_current_track_bank"), trackQuestions.size), fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = viewAllVocab,
                        onClick = { viewAllVocab = true },
                        label = { Text(String.format(Loc.getText("unified_quiz_all_vocab_bank"), allVocabQuestions.size), fontSize = 11.5.sp) },
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
                                if (q.timestampMs != null && q.timestampMs!! > 0) {
                                    val isPlaying = isPlayingAudio && currentlyPlayingTs == q.timestampMs
                                    IconButton(
                                        onClick = { onTogglePlayAudio(q.timestampMs!!, q.trackId) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteQuestion(q.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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
