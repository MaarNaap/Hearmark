package com.example.ui.story

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StoryTargetWord
import com.example.data.VocabularyStory
import com.example.ui.Loc
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var showVocabDrawer by remember { mutableStateOf(false) }

    // Adjustable font size scale for reading comfort (0.9x, 1.0x, 1.15x, 1.3x)
    var fontScaleIndex by remember { mutableIntStateOf(1) }
    val fontScales = listOf(0.9f, 1.0f, 1.15f, 1.3f)
    val currentFontScale = fontScales[fontScaleIndex % fontScales.size]

    val formattedDate = remember(story.createdAt) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
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
                            fontWeight = FontWeight.SemiBold,
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.testTag("story_reader_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 4.dp)
        ) {
            // TOP CONTROLS & ACTIONS BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Identity Icon & Label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = Loc.getText("story_reader_title"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action Buttons Row (Font size, Share, Edit, Regenerate, Delete, Close)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Font Size Toggle
                    IconButton(
                        onClick = { fontScaleIndex = (fontScaleIndex + 1) % fontScales.size },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FormatSize,
                            contentDescription = Loc.getText("story_font_size"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

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
                        modifier = Modifier.size(36.dp).testTag("story_share_btn")
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = Loc.getText("story_share"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Edit button
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(36.dp).testTag("story_edit_btn")
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = Loc.getText("story_edit"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Regenerate button
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(36.dp).testTag("story_regenerate_btn")
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = Loc.getText("story_regenerate"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.size(36.dp).testTag("story_delete_btn")
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = Loc.getText("story_delete"),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = Loc.getText("close"),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
            )

            // EDITORIAL STORY TITLE
            Text(
                text = story.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (22 * currentFontScale).sp,
                    lineHeight = (28 * currentFontScale).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // REVISED METADATA & REFINED VOCABULARY PILL
            // Instead of a prominent horizontal list of words dominating before reading,
            // we present clean metadata and a compact, expandable vocabulary pill.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Genre badge
                SuggestionChip(
                    onClick = {},
                    label = { Text(options.genre, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.height(28.dp)
                )

                // Level badge
                SuggestionChip(
                    onClick = {},
                    label = { Text(options.level, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.height(28.dp)
                )

                // Expandable Vocabulary Pill: Clean & unobtrusive
                AssistChip(
                    onClick = { showVocabDrawer = !showVocabDrawer },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Spellcheck,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    label = {
                        Text(
                            text = Loc.getFormattedText("story_review_words", targetWords.size),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    trailingIcon = {
                        Icon(
                            if (showVocabDrawer) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (showVocabDrawer) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                    ),
                    border = null,
                    modifier = Modifier.height(28.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Date label
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // EXPANDABLE VOCABULARY DRAWER (Only shows when user intentionally opens it)
            AnimatedVisibility(
                visible = showVocabDrawer,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = Loc.getText("story_vocabulary_title"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = Loc.getText("story_tap_word_hint"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            targetWords.forEach { tw ->
                                val isPresent = !missingWords.contains(tw.word)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isPresent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isPresent) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.clickable { selectedWordForDetails = tw }
                                ) {
                                    Text(
                                        text = tw.word,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MISSING WORDS WARNING (if any)
            if (missingWords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = Loc.getFormattedText("story_missing_words_notice", missingWords.joinToString(", ")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // READING TIP HINT (Subtle indicator that words in story are interactive)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Text(
                    text = Loc.getText("story_tap_word_hint"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }

            // NATURAL STORY TEXT (Rendered directly on main container - NO artificial empty boxed surface)
            SelectionContainer {
                Text(
                    text = annotatedStory,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (17 * currentFontScale).sp,
                        lineHeight = (28 * currentFontScale).sp,
                        letterSpacing = 0.2.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 28.dp)
                        .testTag("story_body_text")
                )
            }

            // ELEGANT END-OF-STORY COMPLETION FOOTER
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = Loc.getText("story_completed_badge"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = Loc.getFormattedText("story_words_included", targetWords.size - missingWords.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showVocabDrawer = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Spellcheck, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Loc.getFormattedText("story_review_words", targetWords.size), fontSize = 12.sp)
                        }

                        Button(
                            onClick = onRegenerate,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Loc.getText("story_regenerate"), fontSize = 12.sp)
                        }
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Spellcheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (targetWord.meaning.isNotBlank()) {
                        Column {
                            Text(
                                text = Loc.getText("story_word_meaning"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(3.dp))
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
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = targetWord.contextSentence,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
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
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        maxLines = 14,
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
