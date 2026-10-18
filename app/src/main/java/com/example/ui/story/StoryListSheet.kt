package com.example.ui.story

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VocabularyStory
import com.example.ui.Loc
import java.text.SimpleDateFormat
import java.util.*

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
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var storyToDelete by remember { mutableStateOf<VocabularyStory?>(null) }
    var storyToEdit by remember { mutableStateOf<VocabularyStory?>(null) }

    val filteredStories = remember(stories, searchQuery) {
        if (searchQuery.isBlank()) {
            stories
        } else {
            val q = searchQuery.trim().lowercase()
            stories.filter { s ->
                s.title.lowercase().contains(q) ||
                        s.storyText.lowercase().contains(q) ||
                        s.targetWordsJson.lowercase().contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("story_list_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // HEADER: Title, Count, Create button, Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "${Loc.getText("ai_hub_story_saved_stories")} (${stories.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onCreateNewStory()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Loc.getText("ai_hub_story_action"), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = Loc.getText("close"), modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // SEARCH BAR
            if (stories.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search stories or vocabulary...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("story_list_search_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // LIST OF STORIES OR EMPTY STATE
            if (filteredStories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = Loc.getText("story_empty_list"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = {
                                onDismiss()
                                onCreateNewStory()
                            }
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Loc.getText("ai_hub_story_action"))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredStories, key = { it.id }) { item ->
                        val options = remember(item.optionsJson) { item.getOptions() }
                        val targetWords = remember(item.targetWordsJson) { item.getTargetWords() }
                        val missingWords = remember(item.missingWordsJson) { item.getMissingWords() }
                        val dateFormatted = remember(item.createdAt) {
                            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                            sdf.format(Date(item.createdAt))
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenStory(item) }
                                .testTag("story_item_${item.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Top row: Title + Date
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = dateFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Badges Row: Genre, Level, Word Count
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SuggestionChip(
                                        onClick = { onOpenStory(item) },
                                        label = { Text(options.genre, fontSize = 10.sp) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                    SuggestionChip(
                                        onClick = { onOpenStory(item) },
                                        label = { Text(options.level, fontSize = 10.sp) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                    val wordCount = targetWords.size
                                    SuggestionChip(
                                        onClick = { onOpenStory(item) },
                                        label = { Text("$wordCount words", fontSize = 10.sp) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                    if (missingWords.isNotEmpty()) {
                                        SuggestionChip(
                                            onClick = { onOpenStory(item) },
                                            label = { Text("${missingWords.size} missing", fontSize = 10.sp, color = MaterialTheme.colorScheme.error) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }

                                // Story excerpt preview
                                Text(
                                    text = item.storyText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )

                                // Action Buttons Row: Read, Regenerate, Edit, Share, Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { onOpenStory(item) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Read", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        // Regenerate
                                        IconButton(
                                            onClick = { onRegenerateStory(item) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Filled.Refresh, contentDescription = Loc.getText("story_regenerate"), modifier = Modifier.size(16.dp))
                                        }

                                        // Edit
                                        IconButton(
                                            onClick = { storyToEdit = item },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = Loc.getText("story_edit"), modifier = Modifier.size(16.dp))
                                        }

                                        // Share
                                        IconButton(
                                            onClick = {
                                                val shareText = buildString {
                                                    appendLine(item.title)
                                                    appendLine("${options.genre} • ${options.level}")
                                                    appendLine()
                                                    appendLine(item.storyText)
                                                    appendLine()
                                                    appendLine("--- Target Vocabulary ---")
                                                    targetWords.forEachIndexed { i, tw ->
                                                        appendLine("${i + 1}. ${tw.word}: ${tw.meaning}")
                                                    }
                                                }
                                                val intent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TITLE, item.title)
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(intent, Loc.getText("story_share")))
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = Loc.getText("story_share"), modifier = Modifier.size(16.dp))
                                        }

                                        // Delete
                                        IconButton(
                                            onClick = { storyToDelete = item },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = Loc.getText("story_delete"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // EDIT DIALOG
    storyToEdit?.let { story ->
        var editedTitle by remember { mutableStateOf(story.title) }
        var editedText by remember { mutableStateOf(story.storyText) }

        AlertDialog(
            onDismissRequest = { storyToEdit = null },
            title = { Text(Loc.getText("story_edit"), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        label = { Text("Story Text") },
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = story.copy(title = editedTitle.trim(), storyText = editedText.trim())
                    onUpdateStory(updated)
                    storyToEdit = null
                }) {
                    Text(Loc.getText("done"))
                }
            },
            dismissButton = {
                TextButton(onClick = { storyToEdit = null }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    // DELETE CONFIRMATION DIALOG
    storyToDelete?.let { story ->
        AlertDialog(
            onDismissRequest = { storyToDelete = null },
            title = { Text(Loc.getText("story_delete"), fontWeight = FontWeight.Bold) },
            text = { Text(Loc.getText("story_delete_confirm")) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStory(story)
                        storyToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("story_delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { storyToDelete = null }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }
}
