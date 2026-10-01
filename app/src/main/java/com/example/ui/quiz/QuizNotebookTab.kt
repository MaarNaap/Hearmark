package com.example.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Note
import com.example.ui.Loc

@Composable
internal fun NotebookQuizTabContent(
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
    var isNotesExpanded by remember { mutableStateOf(false) }

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

        // Note selector items
        quizNoteSelectorItems(
            allNotes = allNotes,
            filteredNotes = filteredNotes,
            allTags = allTags,
            selectedNoteIds = selectedNoteIds,
            selectedTagFilter = selectedTagFilter,
            isNotesExpanded = isNotesExpanded,
            onToggleExpanded = { isNotesExpanded = !isNotesExpanded },
            onSelectAll = { selectedNoteIds = allNotes.map { it.id }.toSet() },
            onDeselectAll = { selectedNoteIds = emptySet() },
            onSelectTagFilter = { tag -> selectedTagFilter = tag },
            onToggleNoteSelection = { noteId, checked ->
                selectedNoteIds = if (checked) selectedNoteIds + noteId else selectedNoteIds - noteId
            }
        )

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
