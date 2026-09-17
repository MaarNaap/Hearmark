package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun VocabularyReviewScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    val context = LocalContext.current
    val allVocabQuestions by viewModel.allVocabularyQuestions.collectAsStateWithLifecycle()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = Loc.getText("vocab_review_title"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = Loc.getText("vocab_review_subtitle"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("vocab_review_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row (only shown when not in active question test session)
            if (!isSessionActive) {
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
            }

            // MAIN CONTENT
            if (allVocabQuestions.isEmpty()) {
                // Empty state across all lessons
                VocabReviewEmptyState(onNavigateToLibrary = onNavigateToLibrary)
            } else if (isSessionActive) {
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
                    // Active Question Card
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
                when (selectedTab) {
                    0 -> {
                        // Overview Hub & Session Launchpad
                        VocabReviewHubView(
                            totalCount = totalCount,
                            masteredCount = masteredCount,
                            needsPracticeCount = needsPracticeCount,
                            untestedCount = untestedCount,
                            allQuestions = allVocabQuestions,
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
                        // Word Bank Browser
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
                        Toast.makeText(context, "Question deleted", Toast.LENGTH_SHORT).show()
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

@Composable
fun VocabReviewHubView(
    totalCount: Int,
    masteredCount: Int,
    needsPracticeCount: Int,
    untestedCount: Int,
    allQuestions: List<QuizQuestion>,
    onStartTestAll: () -> Unit,
    onStartPracticeWeak: () -> Unit,
    onStartQuickQuiz: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Clean, minimal description
        item {
            Text(
                text = Loc.getText("vocab_review_desc"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action Cards (Launch tests)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStartTestAll() }
                    .testTag("btn_vocab_test_all"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Spellcheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Loc.getText("vocab_review_test_all"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${allQuestions.size} questions available across lessons and notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (needsPracticeCount > 0 || untestedCount > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStartPracticeWeak() }
                        .testTag("btn_vocab_practice_weak"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(Color(0xFFE65100).copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Loc.getText("vocab_review_practice_weak"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${needsPracticeCount + untestedCount} questions to review or learn",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (allQuestions.size >= 5) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStartQuickQuiz() }
                        .testTag("btn_vocab_quick_quiz"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Loc.getText("vocab_review_quick_quiz"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "A focused, bite-sized 10-question flash drill",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatPill(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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

@Composable
fun VocabReviewSessionResults(
    questions: List<QuizQuestion>,
    userAnswers: Map<Int, Int>,
    trackMap: Map<Long, AudioTrack>,
    onRetake: () -> Unit,
    onPracticeMistakes: () -> Unit,
    onBackToHub: () -> Unit,
    onPlaySnippet: (Long, Long?) -> Unit,
    currentlyPlayingTs: Long?,
    isPlayingAudio: Boolean
) {
    val total = questions.size
    val correctCount = questions.filterIndexed { index, q -> userAnswers[index] == q.correctIndex }.size
    val percent = if (total > 0) (correctCount * 100) / total else 0
    val mistakeCount = total - correctCount

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (percent >= 70) Icons.Filled.EmojiEvents else Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = Loc.getText("vocab_review_completed_title"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = String.format(Loc.getText("quiz_score_summary"), correctCount, total, percent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRetake,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Loc.getText("quiz_retake"))
                        }
                        if (mistakeCount > 0) {
                            OutlinedButton(
                                onClick = onPracticeMistakes,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Review ($mistakeCount)")
                            }
                        }
                    }
                    TextButton(onClick = onBackToHub) {
                        Text(Loc.getText("vocab_review_back_overview"))
                    }
                }
            }
        }

        item {
            Text(
                text = "Question Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        itemsIndexed(questions) { index, q ->
            val userChoice = userAnswers[index]
            val isCorrect = userChoice == q.correctIndex
            val options = q.getOptions()
            val track = trackMap[q.trackId]

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, if (isCorrect) Color(0xFF2E7D32).copy(alpha = 0.5f) else Color(0xFFC62828).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Q${index + 1}: ${q.question}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isCorrect) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }

                    if (userChoice != null && options.indices.contains(userChoice)) {
                        Text(
                            text = "Your answer: ${options[userChoice]}",
                            fontSize = 12.sp,
                            color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                    if (!isCorrect && options.indices.contains(q.correctIndex)) {
                        Text(
                            text = "Correct answer: ${options[q.correctIndex]}",
                            fontSize = 12.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (track != null) {
                        Text(
                            text = "Lesson: ${track.getDisplayTitle()}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (q.noteId != null) {
                        Text(
                            text = Loc.getText("notebook_quiz_from_notebook_badge"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

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
    val filteredList = remember(allQuestions, searchQuery, filterMode) {
        allQuestions.filter { q ->
            val matchesQuery = searchQuery.isBlank() ||
                    q.question.contains(searchQuery, ignoreCase = true) ||
                    (q.targetWord?.contains(searchQuery, ignoreCase = true) == true) ||
                    (q.meaning?.contains(searchQuery, ignoreCase = true) == true) ||
                    q.getOptions().any { it.contains(searchQuery, ignoreCase = true) } ||
                    q.explanation.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (filterMode) {
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
                "ALL" to "All (${allQuestions.size})",
                "MASTERED" to Loc.getText("vocab_review_mastered"),
                "NEEDS_PRACTICE" to Loc.getText("vocab_review_needs_practice"),
                "UNTESTED" to Loc.getText("vocab_review_untested")
            )
            items(filters) { (key, label) ->
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
                    text = "No questions match your filter.",
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
                                Surface(
                                    color = when {
                                        accuracy == null -> MaterialTheme.colorScheme.surfaceVariant
                                        accuracy >= 75 -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                        else -> Color(0xFFE65100).copy(alpha = 0.15f)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (accuracy != null) "Accuracy: $accuracy% (${q.timesCorrect}/${q.timesAnswered})" else Loc.getText("quiz_never_answered"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            accuracy == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                            accuracy >= 75 -> Color(0xFF2E7D32)
                                            else -> Color(0xFFE65100)
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Row {
                                    if (q.trackId != null) {
                                        IconButton(
                                            onClick = { onPlaySnippet(q.trackId, q.timestampMs) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingAudio && currentlyPlayingTs == q.timestampMs) Icons.Filled.Pause else Icons.Filled.VolumeUp,
                                                contentDescription = "Play context audio",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteQuestion(q) },
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

                            if (!q.targetWord.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Target Word: ${q.targetWord}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
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

@Composable
fun VocabReviewEmptyState(onNavigateToLibrary: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = Loc.getText("vocab_review_empty_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = Loc.getText("vocab_review_empty_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onNavigateToLibrary,
                    modifier = Modifier.testTag("vocab_empty_to_library_btn")
                ) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Loc.getText("vocab_review_explore_lessons"))
                }
            }
        }
    }
}
