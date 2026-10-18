package com.example.ui.story

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.FolderFileTreeFilterDialog
import com.example.ui.Loc

private const val MAX_WORDS_PER_STORY_CAP = 20

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryVocabPicker(
    allNotes: List<Note>,
    allFolders: List<Folder>,
    allTracks: List<AudioTrack>,
    selectedWords: List<StoryTargetWord>,
    options: StoryOptions,
    onOptionsChanged: (StoryOptions) -> Unit,
    onWordsChanged: (List<StoryTargetWord>) -> Unit,
    onOpenSavedStories: () -> Unit,
    savedStoriesCount: Int = 0
) {
    // Search state
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Folder and File filter state (matching folders by ID only)
    var selectedFolderIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showFolderFileFilterDialog by remember { mutableStateOf(false) }

    // Tag filter state
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val allTags = remember(allNotes) {
        allNotes.flatMap { it.getTagsList() }.distinct().sorted()
    }

    // Filter notes according to user criteria:
    // 1. Folders: match by folder ID only (including subfolders)
    // 2. Tracks / Lessons / Virtual Scenes
    // 3. Tag
    // 4. Search query
    val candidateNotes = remember(allNotes, allFolders, allTracks, selectedFolderIds, selectedTrackIds, selectedTag, searchQuery) {
        val allSubfolderIds = if (selectedFolderIds.isEmpty()) emptySet() else {
            selectedFolderIds.flatMap { folderId ->
                fun getSubs(id: Long): Set<Long> {
                    val direct = allFolders.filter { it.parentFolderId == id }.map { it.id }.toSet()
                    return setOf(id) + direct.flatMap { getSubs(it) }
                }
                getSubs(folderId)
            }.toSet()
        }

        val trackById = allTracks.associateBy { it.id }

        allNotes.filter { note ->
            // Must have a non-empty isolated target word
            val targetWord = note.getIsolatedTargetWord()
            if (targetWord.isBlank()) return@filter false

            // Tag filter
            if (selectedTag != null && !note.getTagsList().contains(selectedTag)) {
                return@filter false
            }

            // Search query filter
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                val match = note.text.lowercase().contains(q) ||
                        note.comment.lowercase().contains(q) ||
                        targetWord.lowercase().contains(q)
                if (!match) return@filter false
            }

            // Folder & Track filter (MATCH FOLDERS BY ID ONLY)
            val hasFolderOrTrackFilter = selectedFolderIds.isNotEmpty() || selectedTrackIds.isNotEmpty()
            if (hasFolderOrTrackFilter) {
                val noteTrack = note.trackId?.let { trackById[it] }

                val matchesFolder = if (selectedFolderIds.isEmpty()) false else {
                    (note.folderId != null && allSubfolderIds.contains(note.folderId)) ||
                            (noteTrack?.parentFolderId != null && allSubfolderIds.contains(noteTrack.parentFolderId))
                }

                val matchesTrack = if (selectedTrackIds.isEmpty()) false else {
                    (note.trackId != null && selectedTrackIds.contains(note.trackId)) ||
                            (noteTrack?.id != null && selectedTrackIds.contains(noteTrack.id))
                }

                if (!matchesFolder && !matchesTrack) return@filter false
            }

            true
        }
    }

    // Convert notes into StoryTargetWords and de-duplicate by lowercase word
    fun extractTargetWords(notes: List<Note>): List<StoryTargetWord> {
        val wordMap = linkedMapOf<String, StoryTargetWord>()
        notes.forEach { note ->
            val w = note.getIsolatedTargetWord().trim()
            if (w.isNotBlank()) {
                val key = w.lowercase()
                if (!wordMap.containsKey(key)) {
                    wordMap[key] = StoryTargetWord(
                        word = w,
                        meaning = note.getIsolatedMeaning().trim(),
                        contextSentence = note.getIsolatedContextSentence().trim(),
                        noteId = note.id
                    )
                }
            }
        }
        return wordMap.values.toList()
    }

    // Auto-select candidate words on initial load if selectedWords is empty
    LaunchedEffect(candidateNotes) {
        if (selectedWords.isEmpty() && candidateNotes.isNotEmpty()) {
            val extracted = extractTargetWords(candidateNotes)
            onWordsChanged(extracted)
        }
    }

    var showOptionsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("story_vocab_picker"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // TOP CONTROLS: Saved Stories library button + Options summary button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Saved Stories button
            OutlinedButton(
                onClick = onOpenSavedStories,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("story_saved_library_btn"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${Loc.getText("ai_hub_story_saved_stories")} ($savedStoriesCount)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Story Options Configure button
            FilledTonalButton(
                onClick = { showOptionsDialog = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("story_configure_options_btn"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${options.genre} • ${options.level}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // FILTER & SEARCH TOOLBAR
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearchExpanded) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .testTag("story_search_input"),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { isSearchExpanded = false }),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(Loc.getText("ai_hub_search_notes"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                isSearchExpanded = false
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                        }
                    } else {
                        // Title + Count
                        Text(
                            text = Loc.getFormattedText("story_review_words", selectedWords.size),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val totalFilterCount = selectedFolderIds.size + selectedTrackIds.size
                            // Filter by Folder / Files Dialog Button
                            IconButton(
                                onClick = { showFolderFileFilterDialog = true },
                                modifier = Modifier.size(28.dp).testTag("story_folder_filter_btn")
                            ) {
                                BadgedBox(badge = {
                                    if (totalFilterCount > 0) {
                                        Badge { Text(totalFilterCount.toString(), fontSize = 9.sp) }
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = "Filter Folders/Files",
                                        tint = if (totalFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            // Search Toggle
                            IconButton(
                                onClick = { isSearchExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(17.dp))
                            }

                            // Select All from current candidate notes
                            TextButton(
                                onClick = {
                                    val candidateWords = extractTargetWords(candidateNotes)
                                    val combined = (selectedWords + candidateWords).distinctBy { it.word.lowercase() }
                                    onWordsChanged(combined)
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(Loc.getText("select_all"), fontSize = 11.sp)
                            }

                            // Deselect All
                            TextButton(
                                onClick = { onWordsChanged(emptyList()) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(Loc.getText("deselect_all"), fontSize = 11.sp)
                            }
                        }
                    }
                }

                // TAGS FILTER CHIPS ROW (if notes have tags)
                if (allTags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = { selectedTag = null },
                                label = { Text("All Tags", fontSize = 10.5.sp) },
                                modifier = Modifier.height(26.dp)
                            )
                        }
                        items(allTags) { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                label = { Text(tag, fontSize = 10.5.sp) },
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }
                }

                // ACTIVE FOLDER / FILE FILTER INDICATOR CHIP
                val totalFilterCount = selectedFolderIds.size + selectedTrackIds.size
                if (totalFilterCount > 0) {
                    val label = when {
                        selectedFolderIds.isNotEmpty() && selectedTrackIds.isEmpty() ->
                            "${selectedFolderIds.size} ${Loc.getText("selected_folders_label")}"
                        selectedFolderIds.isEmpty() && selectedTrackIds.isNotEmpty() ->
                            "${selectedTrackIds.size} ${Loc.getText("selected_files_label")}"
                        else ->
                            "$totalFilterCount ${Loc.getText("selected_folders_files_label")}"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = true,
                            onClick = { showFolderFileFilterDialog = true },
                            label = { Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(12.dp)) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        selectedFolderIds = emptySet()
                                        selectedTrackIds = emptySet()
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(11.dp))
                                }
                            },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }
        }

        // CAP WARNING & BATCH SPLIT OFFER (If > 20 words)
        if (selectedWords.size > MAX_WORDS_PER_STORY_CAP) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = Loc.getFormattedText("story_cap_warning", MAX_WORDS_PER_STORY_CAP),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                onWordsChanged(selectedWords.take(MAX_WORDS_PER_STORY_CAP))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(Loc.getFormattedText("story_take_first", MAX_WORDS_PER_STORY_CAP), fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val batches = selectedWords.chunked(MAX_WORDS_PER_STORY_CAP)
                                if (batches.isNotEmpty()) {
                                    onWordsChanged(batches.first())
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(Loc.getText("story_split_batches"), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // WORD REVIEW LIST: The user can review the resulting word list and remove any word before generating
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            if (selectedWords.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No words selected. Pick from notes or select all above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(selectedWords, key = { it.word.lowercase() }) { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.word,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.meaning.isNotBlank()) {
                                        Text(
                                            text = item.meaning,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        onWordsChanged(selectedWords.filter { it.word.lowercase() != item.word.lowercase() })
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = Loc.getText("story_remove_word"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // FOLDER / FILE TREE FILTER DIALOG (Matching folders by ID only)
    if (showFolderFileFilterDialog) {
        FolderFileTreeFilterDialog(
            allFolders = allFolders,
            allTracks = allTracks,
            initialSelectedFolderIds = selectedFolderIds,
            initialSelectedTrackIds = selectedTrackIds,
            defaultExpanded = false,
            onDismiss = { showFolderFileFilterDialog = false },
            onApply = { newFolders, newTracks ->
                selectedFolderIds = newFolders
                selectedTrackIds = newTracks
                showFolderFileFilterDialog = false
            }
        )
    }

    // STORY OPTIONS CONFIGURATION DIALOG
    if (showOptionsDialog) {
        StoryOptionsDialog(
            currentOptions = options,
            onDismiss = { showOptionsDialog = false },
            onApply = { updated ->
                onOptionsChanged(updated)
                showOptionsDialog = false
            }
        )
    }
}

@Composable
fun StoryOptionsDialog(
    currentOptions: StoryOptions,
    onDismiss: () -> Unit,
    onApply: (StoryOptions) -> Unit
) {
    var length by remember { mutableStateOf(currentOptions.length) }
    var level by remember { mutableStateOf(currentOptions.level) }
    var genre by remember { mutableStateOf(currentOptions.genre) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(Loc.getText("story_options_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Length Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Loc.getText("story_length"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SHORT" to "story_length_short", "MEDIUM" to "story_length_medium", "LONG" to "story_length_long").forEach { (key, locKey) ->
                            FilterChip(
                                selected = length == key,
                                onClick = { length = key },
                                label = { Text(Loc.getText(locKey), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. CEFR Level Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Loc.getText("story_level"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BEGINNER" to "story_level_beginner", "INTERMEDIATE" to "story_level_intermediate", "ADVANCED" to "story_level_advanced").forEach { (key, locKey) ->
                            FilterChip(
                                selected = level == key,
                                onClick = { level = key },
                                label = { Text(Loc.getText(locKey), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. Genre / Tone Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Loc.getText("story_genre"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    val genres = listOf(
                        "SLICE_OF_LIFE" to "story_genre_slice_of_life",
                        "MYSTERY" to "story_genre_mystery",
                        "ADVENTURE" to "story_genre_adventure",
                        "SCI_FI" to "story_genre_sci_fi",
                        "HUMOROUS" to "story_genre_humorous",
                        "INSPIRATIONAL" to "story_genre_inspirational",
                        "HISTORICAL" to "story_genre_historical"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        genres.chunked(2).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { (gKey, locKey) ->
                                    FilterChip(
                                        selected = genre == gKey,
                                        onClick = { genre = gKey },
                                        label = { Text(Loc.getText(locKey), fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(StoryOptions(length = length, level = level, genre = genre)) }) {
                Text(Loc.getText("done"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}
