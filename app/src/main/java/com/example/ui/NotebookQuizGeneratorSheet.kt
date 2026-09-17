package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.data.Note

/**
 * Unified Notebook Quiz Entry Point wrapper for backwards compatibility.
 * Opens the unified quiz sheet in NOTEBOOK mode via QuizViewModel.
 */
@Composable
fun NotebookQuizGeneratorSheet(
    viewModel: AppViewModel,
    allNotes: List<Note> = emptyList(),
    initialSelectedNotes: List<Note>? = null,
    onDismiss: () -> Unit = {},
    onOpenVocabularyReview: () -> Unit = {}
) {
    LaunchedEffect(initialSelectedNotes) {
        viewModel.openNotebookQuizSheet(initialSelectedNotes)
    }
}

