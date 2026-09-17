package com.example.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Note
import com.example.player.AudioPlayerManager

/**
 * Unified Notebook Quiz Entry Point wrapper for backwards compatibility.
 * Delegates to UnifiedQuizSheet with QuizViewModel and sets mode to NOTEBOOK.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookQuizGeneratorSheet(
    viewModel: AppViewModel,
    allNotes: List<Note>,
    initialSelectedNotes: List<Note>? = null,
    onDismiss: () -> Unit,
    onOpenVocabularyReview: () -> Unit
) {
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()

    LaunchedEffect(initialSelectedNotes) {
        viewModel.quizViewModel.openNotebookQuizSheet(initialSelectedNotes)
    }

    UnifiedQuizSheet(
        quizViewModel = viewModel.quizViewModel,
        allNotes = allNotes,
        allTracks = allTracks,
        onDismiss = onDismiss,
        onOpenVocabularyReview = onOpenVocabularyReview,
        onPlayTrackInMainPlayer = { track, ts ->
            AudioPlayerManager.playTrack(track)
            AudioPlayerManager.seekTo(ts)
            AudioPlayerManager.resume()
        }
    )
}
