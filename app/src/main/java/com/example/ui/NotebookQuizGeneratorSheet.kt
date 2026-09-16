package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

    // Prevent accidental swipe-down dismissal while generating
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            if (isGenerating && targetValue == SheetValue.Hidden) {
                false
            } else {
                true
            }
        }
    )

    // Tag filtering & search
    val allAvailableTags = remember(allNotes) {
        allNotes.flatMap { it.getTagsList() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var noteSearchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    // Selection of notes to pass to Gemini
    var selectedNoteIds by remember(initialSelectedNotes) {
        mutableStateOf(
            if (!initialSelectedNotes.isNullOrEmpty()) {
                initialSelectedNotes.map { it.id }.toSet()
            } else {
                allNotes.map { it.id }.toSet()
            }
        )
    }

    var showNotePicker by remember {
        mutableStateOf(initialSelectedNotes.isNullOrEmpty())
    }
    var requestedQuestionCount by remember { mutableIntStateOf(4) }

    // Map for fast note lookup
    val noteMap = remember(allNotes) { allNotes.associateBy { it.id } }

    // Compute notes matching active tag filter and search query
    val filteredNotes = remember(allNotes, selectedTagFilter, noteSearchQuery) {
        allNotes.filter { note ->
            val matchesTag = selectedTagFilter == null || note.getTagsList().any { it.equals(selectedTagFilter, ignoreCase = true) }
            val matchesQuery = noteSearchQuery.isBlank() ||
                note.text.contains(noteSearchQuery, ignoreCase = true) ||
                note.comment.contains(noteSearchQuery, ignoreCase = true)
            matchesTag && matchesQuery
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isGenerating) {
                onDismiss()
            }
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
                        if (!isGenerating) {
                            onDismiss()
                        } else {
                            Toast.makeText(context, "AI is currently generating questions in background...", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.testTag("notebook_quiz_close_btn")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // In-Progress Loading Indicator
            if (isGenerating) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = Loc.getText("notebook_quiz_generating"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = Loc.getText("notebook_quiz_auto_saved_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // SUCCESS / RESULTS STATE (Questions are automatically saved to Quiz Bank!)
            if (generatedQuestions.isNotEmpty() && !isGenerating) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Auto-Saved Banner
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = saveSuccessMessage ?: String.format(Loc.getText("notebook_quiz_saved_success"), generatedQuestions.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Text(
                                text = Loc.getText("notebook_quiz_auto_saved_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenVocabularyReview()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("notebook_quiz_practice_now_btn")
                                ) {
                                    Icon(Icons.Filled.Spellcheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(Loc.getText("notebook_quiz_saved_practice"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = { onDismiss() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text(Loc.getText("quiz_dismiss"))
                                }
                            }

                            TextButton(
                                onClick = { viewModel.resetNotebookQuiz() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(Loc.getText("notebook_quiz_generate_more"), fontSize = 13.sp)
                            }
                        }
                    }

                    // Section Title: Review of Auto-Saved Questions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${Loc.getText("notebook_quiz_review_title")} (${generatedQuestions.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = Loc.getText("notebook_quiz_auto_saved_badge"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // List of generated question cards with delete affordance
                    generatedQuestions.forEach { questionItem ->
                        GeneratedQuestionCard(
                            item = questionItem,
                            sourceNote = noteMap[questionItem.sourceNoteId],
                            onDelete = {
                                viewModel.deleteNotebookQuizQuestion(questionItem)
                                Toast.makeText(context, "Question removed from Quiz Bank", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            } else if (!isGenerating) {
                // CONFIGURATION & GENERATION VIEW

                // Error Card if any
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

                // AI Smart Filter Banner
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // If launched for a single specific note, display focused target note highlight
                if (!initialSelectedNotes.isNullOrEmpty() && initialSelectedNotes.size == 1) {
                    val targetNote = initialSelectedNotes.first()
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = Loc.getText("notebook_quiz_target_single_note"),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                TextButton(
                                    onClick = { showNotePicker = !showNotePicker },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showNotePicker) Icons.Filled.ExpandLess else Icons.Filled.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showNotePicker) "Hide Selector" else "Choose More Notes",
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Text(
                                text = targetNote.text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (targetNote.comment.isNotBlank()) {
                                Text(
                                    text = targetNote.comment,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Note Selection Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Header & Toggle
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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isSearchVisible = !isSearchVisible },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search Notes",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isSearchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

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
                                    text = if (showNotePicker) "Hide List" else "Notes (${selectedNoteIds.size}/${allNotes.size})",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Optional Search Bar
                    AnimatedVisibility(visible = isSearchVisible) {
                        OutlinedTextField(
                            value = noteSearchQuery,
                            onValueChange = { noteSearchQuery = it },
                            placeholder = { Text("Search note text or comments...", fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (noteSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { noteSearchQuery = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }

                    // SMART FILTER & SELECTION CONTROLS
                    // 1) Tag Filter Chips Row
                    if (allAvailableTags.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = Loc.getText("notebook_quiz_filter_by_tag"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // All Tags Chip
                                FilterChip(
                                    selected = selectedTagFilter == null,
                                    onClick = { selectedTagFilter = null },
                                    label = { Text("${Loc.getText("notebook_quiz_all_tags")} (${allNotes.size})", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )

                                // Individual Tag Chips
                                allAvailableTags.forEach { tag ->
                                    val tagNoteCount = remember(allNotes, tag) {
                                        allNotes.count { it.getTagsList().any { t -> t.equals(tag, ignoreCase = true) } }
                                    }
                                    val isSelected = selectedTagFilter.equals(tag, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedTagFilter = if (isSelected) null else tag
                                        },
                                        label = { Text("#$tag ($tagNoteCount)", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 2) Selection Actions Bar: Select All / Deselect All / Tag Quick Select
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Checklist,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${selectedNoteIds.size} / ${allNotes.size} notes selected",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Select All
                                    FilledTonalButton(
                                        onClick = {
                                            selectedNoteIds = if (selectedTagFilter != null) {
                                                selectedNoteIds + filteredNotes.map { it.id }.toSet()
                                            } else {
                                                allNotes.map { it.id }.toSet()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(Loc.getText("notebook_quiz_select_all"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Deselect All
                                    OutlinedButton(
                                        onClick = {
                                            selectedNoteIds = if (selectedTagFilter != null) {
                                                selectedNoteIds - filteredNotes.map { it.id }.toSet()
                                            } else {
                                                emptySet()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Deselect, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(Loc.getText("notebook_quiz_deselect_all"), fontSize = 11.sp)
                                    }
                                }
                            }

                            // If a tag is active, provide a dedicated one-tap button to select all notes of this tag
                            if (selectedTagFilter != null) {
                                val tagNotes = filteredNotes
                                val allTagNotesSelected = tagNotes.isNotEmpty() && tagNotes.all { selectedNoteIds.contains(it.id) }
                                Button(
                                    onClick = {
                                        selectedNoteIds = if (allTagNotesSelected) {
                                            selectedNoteIds - tagNotes.map { it.id }.toSet()
                                        } else {
                                            selectedNoteIds + tagNotes.map { it.id }.toSet()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (allTagNotesSelected) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = if (allTagNotesSelected) MaterialTheme.colorScheme.onSecondary
                                        else MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (allTagNotesSelected) Icons.Filled.Clear else Icons.Filled.Checklist,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (allTagNotesSelected) "Deselect all #${selectedTagFilter} notes (${tagNotes.size})"
                                        else String.format(Loc.getText("notebook_quiz_select_tag_notes"), selectedTagFilter),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Collapsible Note Checklist
                    AnimatedVisibility(visible = showNotePicker) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
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
                                    text = if (selectedTagFilter != null) "Notes with #${selectedTagFilter} (${filteredNotes.size}):"
                                    else Loc.getText("notebook_quiz_select_notes_prompt"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (filteredNotes.isEmpty()) {
                                    Text(
                                        text = "No notes match the active tag or search filter.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }

                                filteredNotes.forEach { note ->
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

                                        if (note.getTagsList().isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ) {
                                                Text(
                                                    text = "#${note.getTagsList().first()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // QUESTION COUNT SELECTOR
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Loc.getText("notebook_quiz_question_count"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$requestedQuestionCount Questions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Informational Hint on Multi-Question Variety
                    if (selectedNoteIds.size == 1) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = String.format(Loc.getText("notebook_quiz_multi_q_single_note_hint"), requestedQuestionCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Chips for Question Count: 1, 2, 3, 4, 6, 8, 12
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3, 4, 6, 8, 12).forEach { count ->
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

                // PRIMARY GENERATE & AUTO-SAVE ACTION BUTTON
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
                        .height(54.dp)
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
                            text = "${Loc.getText("notebook_quiz_generate_action")} ($requestedQuestionCount Qs)",
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
private fun GeneratedQuestionCard(
    item: GeneratedNoteQuizItem,
    sourceNote: Note?,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Target Word Chip + Auto-Saved check + Delete button
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = Loc.getText("notebook_quiz_delete_question"),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
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
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "💡", fontSize = 12.sp)
                        Text(
                            text = item.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Derived from note quote
            sourceNote?.let { note ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
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
