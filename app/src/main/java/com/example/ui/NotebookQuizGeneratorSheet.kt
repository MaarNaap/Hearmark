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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.GeneratedNoteQuizItem
import com.example.data.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookQuizGeneratorSheet(
    viewModel: AppViewModel,
    allNotes: List<Note>,
    initialSelectedNotes: List<Note>? = null,
    onDismiss: () -> Unit,
    onOpenVocabularyReview: () -> Unit
) {
    val context = LocalContext.current
    val isGenerating by viewModel.isGeneratingNotebookQuiz.collectAsStateWithLifecycle()
    val generationError by viewModel.notebookQuizError.collectAsStateWithLifecycle()
    val generatedQuestions by viewModel.notebookQuizGeneratedQuestions.collectAsStateWithLifecycle()
    val saveSuccessMessage by viewModel.notebookQuizSuccessMessage.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Selection of notes to pass to Gemini
    var selectedNoteIds by remember {
        mutableStateOf(
            if (!initialSelectedNotes.isNullOrEmpty()) {
                initialSelectedNotes.map { it.id }.toSet()
            } else {
                allNotes.map { it.id }.toSet()
            }
        )
    }

    var showNotePicker by remember { mutableStateOf(false) }
    var requestedQuestionCount by remember { mutableIntStateOf(8) }

    // Checked status for generated questions review
    var checkedQuestionIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var hasSaved by remember { mutableStateOf(false) }

    // Initialize checked questions when generated
    LaunchedEffect(generatedQuestions) {
        if (generatedQuestions.isNotEmpty()) {
            checkedQuestionIndices = generatedQuestions.indices.toSet()
        }
    }

    val noteMap = remember(allNotes) { allNotes.associateBy { it.id } }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.clearNotebookQuizFeedback()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = Loc.getText("notebook_quiz_sheet_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Loc.getText("notebook_quiz_sheet_subtitle"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        viewModel.clearNotebookQuizFeedback()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("notebook_quiz_close_btn")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // AI Smart Vocabulary Filter Informational Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = Loc.getText("notebook_quiz_ai_filter_badge"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = Loc.getText("notebook_quiz_ai_filter_info"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // SUCCESS STATE (After user saves generated questions)
            if (hasSaved && !saveSuccessMessage.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = saveSuccessMessage ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                viewModel.clearNotebookQuizFeedback()
                                onDismiss()
                                onOpenVocabularyReview()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notebook_quiz_practice_now_btn")
                        ) {
                            Icon(Icons.Filled.Spellcheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Loc.getText("notebook_quiz_saved_practice"), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.clearNotebookQuizFeedback()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(Loc.getText("quiz_dismiss"))
                        }
                    }
                }
            } else if (generatedQuestions.isNotEmpty() && !isGenerating) {
                // STEP 2: REVIEW & SAVE GENERATED QUESTIONS
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = Loc.getText("notebook_quiz_review_title"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${checkedQuestionIndices.size} / ${generatedQuestions.size} selected to save",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { checkedQuestionIndices = generatedQuestions.indices.toSet() }
                            ) {
                                Text(Loc.getText("notebook_quiz_select_all"), fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { checkedQuestionIndices = emptySet() }
                            ) {
                                Text(Loc.getText("notebook_quiz_deselect_all"), fontSize = 12.sp)
                            }
                        }
                    }

                    Text(
                        text = Loc.getText("notebook_quiz_review_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Generated Question Cards
                    generatedQuestions.forEachIndexed { index, questionItem ->
                        val isChecked = checkedQuestionIndices.contains(index)
                        GeneratedQuestionReviewCard(
                            item = questionItem,
                            sourceNote = noteMap[questionItem.sourceNoteId],
                            isChecked = isChecked,
                            onToggle = {
                                checkedQuestionIndices = if (isChecked) {
                                    checkedQuestionIndices - index
                                } else {
                                    checkedQuestionIndices + index
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Save to Global Quizzes Button
                    Button(
                        onClick = {
                            val selectedItems = generatedQuestions.filterIndexed { idx, _ ->
                                checkedQuestionIndices.contains(idx)
                            }
                            if (selectedItems.isNotEmpty()) {
                                viewModel.saveNotebookQuizQuestions(
                                    items = selectedItems,
                                    notesMap = noteMap,
                                    onSuccess = { count ->
                                        hasSaved = true
                                    }
                                )
                            }
                        },
                        enabled = checkedQuestionIndices.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("notebook_quiz_save_btn")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(Loc.getText("notebook_quiz_save_to_global"), checkedQuestionIndices.size),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.notebookQuizGeneratedQuestions.value = emptyList()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Regenerate Questions")
                    }
                }
            } else {
                // STEP 1: CONFIGURATION & GENERATION
                if (generationError != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = generationError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Source Notes Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Loc.getText("notebook_quiz_target_notes"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { showNotePicker = !showNotePicker }
                        ) {
                            Icon(
                                imageVector = if (showNotePicker) Icons.Filled.ExpandLess else Icons.Filled.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showNotePicker) "Hide Note Selector" else "Select Notes (${selectedNoteIds.size}/${allNotes.size})",
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Note selection count summary chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedNoteIds.size == allNotes.size) {
                                    String.format(Loc.getText("notebook_quiz_all_notes"), allNotes.size)
                                } else {
                                    String.format(Loc.getText("notebook_quiz_selected_notes"), selectedNoteIds.size)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )

                            if (selectedNoteIds.size < allNotes.size) {
                                TextButton(
                                    onClick = { selectedNoteIds = allNotes.map { it.id }.toSet() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Reset to All", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Optional Note Picker List
                    AnimatedVisibility(visible = showNotePicker) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = Loc.getText("notebook_quiz_select_notes_prompt"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                allNotes.forEach { note ->
                                    val isSelected = selectedNoteIds.contains(note.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedNoteIds = if (isSelected) {
                                                    selectedNoteIds - note.id
                                                } else {
                                                    selectedNoteIds + note.id
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedNoteIds = if (checked) {
                                                    selectedNoteIds + note.id
                                                } else {
                                                    selectedNoteIds - note.id
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = note.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (note.comment.isNotBlank()) {
                                                Text(
                                                    text = note.comment,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Question Count Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = Loc.getText("notebook_quiz_question_count"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(4, 8, 12, 16).forEach { count ->
                            val isSelected = requestedQuestionCount == count
                            FilterChip(
                                selected = isSelected,
                                onClick = { requestedQuestionCount = count },
                                label = { Text("$count Qs") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Primary Generate Button
                Button(
                    onClick = {
                        val notesToProcess = allNotes.filter { selectedNoteIds.contains(it.id) }
                        if (notesToProcess.isEmpty()) {
                            Toast.makeText(context, "Please select at least one note.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.generateQuizFromNotes(
                                notes = notesToProcess,
                                maxQuestions = requestedQuestionCount
                            )
                        }
                    },
                    enabled = !isGenerating && selectedNoteIds.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("notebook_quiz_generate_submit_btn")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = Loc.getText("notebook_quiz_generating"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Loc.getText("notebook_quiz_generate_action"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratedQuestionReviewCard(
    item: GeneratedNoteQuizItem,
    sourceNote: Note?,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = BorderStroke(
            1.dp,
            if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Target Word + Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Spellcheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = item.targetWord,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.size(24.dp)
                )
            }

            // Question Text
            Text(
                text = item.question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Options preview
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.options.forEachIndexed { optIdx, optText ->
                    val isCorrect = optIdx == item.correctIndex
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCorrect) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (isCorrect) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${('A' + optIdx)}.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = optText,
                                fontSize = 13.sp,
                                color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCorrect) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCorrect) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Correct Option",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Explanation
            if (item.explanation.isNotBlank()) {
                Text(
                    text = "💡 ${item.explanation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            // Derived from note quote
            sourceNote?.let { note ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${Loc.getText("notebook_quiz_derived_from")} \"${note.text}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
