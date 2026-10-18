package com.example.ui.story

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.GeminiStory
import com.example.data.StoryTargetWord
import com.example.data.VocabularyStory
import com.example.ui.Loc
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryReaderSheet(
    story: VocabularyStory,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit,
    onUpdate: (VocabularyStory) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val targetWords = remember(story.targetWordsJson) { story.getTargetWords() }
    val options = remember(story.optionsJson) { story.getOptions() }
    val missingWords = remember(story.missingWordsJson) { story.getMissingWords() }

    var selectedWordForDetails by remember { mutableStateOf<StoryTargetWord?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val formattedDate = remember(story.createdAt) {
        val sdf = SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.getDefault())
        sdf.format(Date(story.createdAt))
    }

    // Build AnnotatedString with interactive highlights for target vocabulary words
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)

    val annotatedStory = remember(story.storyText, targetWords, primaryColor, primaryContainerColor) {
        buildAnnotatedString {
            val text = story.storyText
            if (text.isEmpty() || targetWords.isEmpty()) {
                append(text)
                return@buildAnnotatedString
            }

            // Find all word match ranges
            data class MatchRange(val start: Int, val end: Int, val word: StoryTargetWord)
            val matches = mutableListOf<MatchRange>()

            for (tw in targetWords) {
                val cleanWord = tw.word.trim()
                if (cleanWord.isBlank()) continue

                // Check exact word or regex match
                val pattern = Regex("""(?i)\b${Regex.escape(cleanWord)}(s|es|ed|d|ing|ly)?\b""")
                for (match in pattern.findAll(text)) {
                    matches.add(MatchRange(match.range.first, match.range.last + 1, tw))
                }
            }

            // Sort matches and eliminate overlaps
            val nonOverlapping = mutableListOf<MatchRange>()
            matches.sortBy { it.start }
            var lastEnd = 0
            for (m in matches) {
                if (m.start >= lastEnd) {
                    nonOverlapping.add(m)
                    lastEnd = m.end
                }
            }

            var currentIndex = 0
            for (m in nonOverlapping) {
                if (m.start > currentIndex) {
                    append(text.substring(currentIndex, m.start))
                }
                val matchedText = text.substring(m.start, m.end)
                val link = LinkAnnotation.Clickable(
                    tag = m.word.word,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            background = primaryContainerColor
                        )
                    ),
                    linkInteractionListener = {
                        selectedWordForDetails = m.word
                    }
                )
                withLink(link) {
                    append(matchedText)
                }
                currentIndex = m.end
            }

            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("story_reader_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // HEADER BAR: Title, Actions (Share, Edit, Regenerate, Delete, Close)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = story.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Share button
                    IconButton(
                        onClick = {
                            val shareBody = buildString {
                                appendLine(story.title)
                                appendLine("${options.genre} • ${options.level} • ${options.length}")
                                appendLine()
                                appendLine(story.storyText)
                                appendLine()
                                appendLine("--- Target Vocabulary ---")
                                targetWords.forEachIndexed { idx, tw ->
                                    appendLine("${idx + 1}. ${tw.word}: ${tw.meaning}")
                                }
                            }
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TITLE, story.title)
                                putExtra(Intent.EXTRA_TEXT, shareBody)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, Loc.getText("story_share")))
                        },
                        modifier = Modifier.size(32.dp).testTag("story_share_btn")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = Loc.getText("story_share"), modifier = Modifier.size(18.dp))
                    }

                    // Edit button
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(32.dp).testTag("story_edit_btn")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = Loc.getText("story_edit"), modifier = Modifier.size(18.dp))
                    }

                    // Regenerate button
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(32.dp).testTag("story_regenerate_btn")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = Loc.getText("story_regenerate"), modifier = Modifier.size(18.dp))
                    }

                    // Delete button
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.size(32.dp).testTag("story_delete_btn")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = Loc.getText("story_delete"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }

                    // Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = Loc.getText("close"), modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // METADATA BADGES ROW: Genre, Level, Length, Target words count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(options.genre, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(options.level, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(options.length, fontSize = 11.sp) }
                )

                val includedCount = targetWords.size - missingWords.size
                SuggestionChip(
                    onClick = {},
                    label = { Text(Loc.getFormattedText("story_words_included", includedCount), fontSize = 11.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (missingWords.isEmpty()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                )
            }

            // MISSING WORDS WARNING (if any are still missing after retry)
            if (missingWords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Text(
                            text = Loc.getFormattedText("story_missing_words_notice", missingWords.joinToString(", ")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // QUICK VOCABULARY CHIPS ROW: Tap any word to view meaning
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap any highlighted word below to see its meaning:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(targetWords, key = { it.word.lowercase() }) { tw ->
                    val isPresent = !missingWords.contains(tw.word)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isPresent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            0.5.dp,
                            if (isPresent) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.clickable { selectedWordForDetails = tw }
                    ) {
                        Text(
                            text = tw.word,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // STORY TEXT CONTAINER (Rich reading surface with comfortable line height)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = annotatedStory,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.5.sp,
                                lineHeight = 27.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("story_body_text")
                        )
                    }
                }
            }
        }
    }

    // WORD MEANING BOTTOM SHEET / DIALOG
    selectedWordForDetails?.let { targetWord ->
        AlertDialog(
            onDismissRequest = { selectedWordForDetails = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Spellcheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            title = {
                Text(
                    text = targetWord.word,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (targetWord.meaning.isNotBlank()) {
                        Column {
                            Text(
                                text = Loc.getText("story_word_meaning"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = targetWord.meaning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (targetWord.contextSentence.isNotBlank()) {
                        Column {
                            Text(
                                text = Loc.getText("story_context_in_audio"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = targetWord.contextSentence,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedWordForDetails = null }) {
                    Text(Loc.getText("close"))
                }
            }
        )
    }

    // EDIT STORY DIALOG
    if (showEditDialog) {
        var editedTitle by remember { mutableStateOf(story.title) }
        var editedText by remember { mutableStateOf(story.storyText) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
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
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = story.copy(title = editedTitle.trim(), storyText = editedText.trim())
                    onUpdate(updated)
                    showEditDialog = false
                }) {
                    Text(Loc.getText("done"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    // DELETE CONFIRMATION DIALOG
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(Loc.getText("story_delete"), fontWeight = FontWeight.Bold) },
            text = { Text(Loc.getText("story_delete_confirm")) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("story_delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }
}
