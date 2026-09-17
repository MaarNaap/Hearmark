package com.example.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.AudioPlayerManager

/**
 * Unified Quiz Entry Point wrapper for backwards compatibility.
 * Delegates to UnifiedQuizSheet with QuizViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSheet(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onOpenVocabularyReview: (() -> Unit)? = null
) {
    val allNotes by viewModel.notes.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()

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
