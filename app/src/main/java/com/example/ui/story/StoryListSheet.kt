package com.example.ui.story

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.VocabularyStory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryListSheet(
    stories: List<VocabularyStory>,
    onDismiss: () -> Unit,
    onOpenStory: (VocabularyStory) -> Unit,
    onRegenerateStory: (VocabularyStory) -> Unit,
    onUpdateStory: (VocabularyStory) -> Unit,
    onDeleteStory: (VocabularyStory) -> Unit,
    onCreateNewStory: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("story_list_sheet")
    ) {
        StoryListScreen(
            stories = stories,
            onBack = onDismiss,
            onOpenStory = onOpenStory,
            onRegenerateStory = onRegenerateStory,
            onUpdateStory = onUpdateStory,
            onDeleteStory = onDeleteStory,
            onCreateNewStory = onCreateNewStory
        )
    }
}
