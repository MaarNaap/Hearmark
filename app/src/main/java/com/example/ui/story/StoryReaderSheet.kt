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

    // Sanitize title and story text to eliminate any unwanted trailing newlines
    val cleanTitle = remember(story.title) {
        story.title
            .replace("\r", "")
            .lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: "A Vocabulary Story"
    }

    val cleanStoryText = remember(story.storyText) {
        story.storyText.replace("\r", "").trim()
    }

    var selectedWordForDetails by remember { mutableStateOf<StoryTargetWord?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showVocabListSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Adjustable font size scale for reading comfort (0.9x, 1.0x, 1.15x, 1.3x)
    var fontScaleIndex by remember { mutableIntStateOf(1) }
    val fontScales = listOf(0.9f, 1.0f, 1.15f, 1.3f)
    val currentFontScale = fontScales[fontScaleIndex % fontScales.size]

    val formattedDate = remember(story.createdAt) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(story.createdAt))
    }

    // Helper functions for human-readable labels
    val formattedGenre = remember(options.genre) {
        when (options.genre.uppercase()) {
            "SLICE_OF_LIFE" -> Loc.getText("story_genre_slice_of_life")
            "MYSTERY" -> Loc.getText("story_genre_mystery")
            "ADVENTURE" -> Loc.getText("story_genre_adventure")
            "SCI_FI" -> Loc.getText("story_genre_sci_fi")
            "HUMOROUS" -> Loc.getText("story_genre_humorous")
            "INSPIRATIONAL" -> Loc.getText("story_genre_inspirational")
            "HISTORICAL" -> Loc.getText("story_genre_historical")
            else -> options.genre.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    val formattedLevel = remember(options.level) {
        when (options.level.uppercase()) {
            "BEGINNER" -> Loc.getText("story_level_beginner")
            "INTERMEDIATE" -> Loc.getText("story_level_intermediate")
            "ADVANCED" -> Loc.getText("story_level_advanced")
            else -> options.level.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    // Build AnnotatedString with interactive highlights for target vocabulary words
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)

    val annotatedStory = remember(cleanStoryText, targetWords, primaryColor, primaryContainerColor) {
        buildAnnotatedString {
            val text = cleanStoryText
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

                // Check exact word or regex match with boundary and common English inflection endings
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
                .padding(horizontal = 24.dp, vertical = 2.dp)
        ) {
            // TOP BAR: Clean & uncluttered
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Close Button & Reader Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = Loc.getText("close"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = Loc.getText("story_reader_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Right: Text Size, Vocab Words Action, Overflow Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Font Size Toggle
                    IconButton(
                        onClick = { fontScaleIndex = (fontScaleIndex + 1) % fontScales.size },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FormatSize,
                            contentDescription = Loc.getText("story_font_size"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }


                    // Share button
                    IconButton(
                        onClick = {
                            val shareBody = buildString {
                                appendLine(cleanTitle)
                                appendLine("$formattedGenre • $formattedLevel")
                                appendLine()
                                appendLine(cleanStoryText)
                                appendLine()
                                appendLine("--- Target Vocabulary ---")
                                targetWords.forEachIndexed { idx, tw ->
                                    appendLine("${idx + 1}. ${tw.word}: ${tw.meaning}")
                                }
                            }
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TITLE, cleanTitle)
                                putExtra(Intent.EXTRA_TEXT, shareBody)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, Loc.getText("story_share")))
                        },
                        modifier = Modifier.size(40.dp).testTag("story_share_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = Loc.getText("story_share"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Overflow Menu (Edit, Regenerate, Delete)
                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(Loc.getText("story_edit")) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    showOptionsMenu = false
                                    showEditDialog = true
                                },
                                modifier = Modifier.testTag("story_edit_btn")
                            )
                            DropdownMenuItem(
                                text = { Text(Loc.getText("story_regenerate")) },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = {
                                    showOptionsMenu = false
                                    onRegenerate()
                                },
                                modifier = Modifier.testTag("story_regenerate_btn")
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        Loc.getText("story_delete"),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showDeleteConfirmDialog = true
                                },
                                modifier = Modifier.testTag("story_delete_btn")
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // STORY TITLE (Immediate, prominent, strictly sanitized without trailing blank space)
            Text(
                text = cleanTitle,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (22 * currentFontScale).sp,
                    lineHeight = (28 * currentFontScale).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            // EDITORIAL METADATA SUBTITLE (Clean, single-line date & style without redundant word count)
            Text(
                text = "$formattedGenre • $formattedLevel • $formattedDate",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp)
            )

            // MISSING WORDS WARNING (if any)
            if (missingWords.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = Loc.getFormattedText("story_missing_words_notice", missingWords.joinToString(", ")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // STORY TEXT (Rendered DIRECTLY on the main container - NO artificial empty boxes, NO clutter)
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
                        .padding(vertical = 4.dp)
                        .testTag("story_body_text")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // END-OF-STORY COMPLETION FOOTER (Review words in-depth, regenerate, or share)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = Loc.getText("story_completed_badge"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = Loc.getFormattedText("story_words_included", targetWords.size - missingWords.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Target words chip review row at the bottom of the story
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        targetWords.forEach { tw ->
                            val isPresent = !missingWords.contains(tw.word)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isPresent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = { showVocabListSheet = true },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Filled.Spellcheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Loc.getFormattedText("story_review_words", targetWords.size), fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // VOCABULARY LIST BOTTOM SHEET (Opened cleanly on demand from top bar or completion card)
    if (showVocabListSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVocabListSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Spellcheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = Loc.getText("story_vocabulary_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { showVocabListSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = Loc.getText("close"))
                    }
                }

                Text(
                    text = Loc.getText("story_tap_word_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                targetWords.forEach { wordItem ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedWordForDetails = wordItem }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = wordItem.word,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (wordItem.meaning.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = wordItem.meaning,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (wordItem.contextSentence.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "“${wordItem.contextSentence}”",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // INDIVIDUAL WORD MEANING DIALOG
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
        var editedTitle by remember { mutableStateOf(cleanTitle) }
        var editedText by remember { mutableStateOf(cleanStoryText) }

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
