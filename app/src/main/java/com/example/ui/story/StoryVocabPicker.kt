package com.example.ui.story

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    initialTrack: AudioTrack? = null,
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

    // Folder and File filter state (pre-populate with initialTrack if present)
    var selectedFolderIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedTrackIds by remember(initialTrack) {
        mutableStateOf(if (initialTrack != null) setOf(initialTrack.id) else emptySet())
    }
    var showFolderFileFilterDialog by remember { mutableStateOf(false) }

    // Tag filter state
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val allTags = remember(allNotes) {
        allNotes.flatMap { it.getTagsList() }.distinct().sorted()
    }

    val trackById = remember(allTracks) { allTracks.associateBy { it.id } }
    val folderById = remember(allFolders) { allFolders.associateBy { it.id } }
    val noteById = remember(allNotes) { allNotes.associateBy { it.id } }

    // Helper to extract de-duplicated StoryTargetWords from a list of notes
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

    // Dynamic filtering logic: matches notes to selected folder(s) or file(s), tags, and search
    fun computeFilteredNotes(
        folderIds: Set<Long>,
        trackIds: Set<Long>,
        tag: String?,
        query: String
    ): List<Note> {
        val allSubfolderIds = if (folderIds.isEmpty()) emptySet() else {
            folderIds.flatMap { folderId ->
                fun getSubs(id: Long): Set<Long> {
                    val direct = allFolders.filter { it.parentFolderId == id }.map { it.id }.toSet()
                    return setOf(id) + direct.flatMap { getSubs(it) }
                }
                getSubs(folderId)
            }.toSet()
        }

        return allNotes.filter { note ->
            val targetWord = note.getIsolatedTargetWord()
            if (targetWord.isBlank()) return@filter false

            // Tag filter
            if (tag != null && !note.getTagsList().contains(tag)) {
                return@filter false
            }

            // Search query filter
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                val match = note.text.lowercase().contains(q) ||
                        note.comment.lowercase().contains(q) ||
                        targetWord.lowercase().contains(q)
                if (!match) return@filter false
            }

            // Folder & Track filter (MATCH FOLDERS BY ID ONLY)
            val hasFolderOrTrackFilter = folderIds.isNotEmpty() || trackIds.isNotEmpty()
            if (hasFolderOrTrackFilter) {
                val noteTrack = note.trackId?.let { trackById[it] }

                val matchesFolder = if (folderIds.isEmpty()) false else {
                    (note.folderId != null && allSubfolderIds.contains(note.folderId)) ||
                            (noteTrack?.parentFolderId != null && allSubfolderIds.contains(noteTrack.parentFolderId))
                }

                val matchesTrack = if (trackIds.isEmpty()) false else {
                    (note.trackId != null && trackIds.contains(note.trackId)) ||
                            (noteTrack?.id != null && trackIds.contains(noteTrack.id))
                }

                if (!matchesFolder && !matchesTrack) return@filter false
            }

            true
        }
    }

    // Candidate notes based on current active filters
    val candidateNotes = remember(
        allNotes, allFolders, allTracks,
        selectedFolderIds, selectedTrackIds, selectedTag, searchQuery
    ) {
        computeFilteredNotes(selectedFolderIds, selectedTrackIds, selectedTag, searchQuery)
    }

    // Auto-select candidate words on initial load if selectedWords is empty
    LaunchedEffect(Unit) {
        if (selectedWords.isEmpty()) {
            val initialFiltered = computeFilteredNotes(selectedFolderIds, selectedTrackIds, selectedTag, searchQuery)
            onWordsChanged(extractTargetWords(initialFiltered))
        }
    }

    var showOptionsDialog by remember { mutableStateOf(false) }

    // Human-readable summary of current source filter
    val currentSourceLabel = remember(selectedFolderIds, selectedTrackIds, folderById, trackById) {
        when {
            selectedFolderIds.isEmpty() && selectedTrackIds.isEmpty() ->
                Loc.getText("story_source_all")
            selectedFolderIds.size == 1 && selectedTrackIds.isEmpty() -> {
                val fName = folderById[selectedFolderIds.first()]?.folderName ?: "Folder"
                "📁 $fName"
            }
            selectedFolderIds.isEmpty() && selectedTrackIds.size == 1 -> {
                val tName = trackById[selectedTrackIds.first()]?.getDisplayTitle() ?: "Audio Track"
                "🎵 $tName"
            }
            selectedFolderIds.isNotEmpty() && selectedTrackIds.isEmpty() ->
                "📁 ${selectedFolderIds.size} ${Loc.getText("selected_folders_label")}"
            selectedFolderIds.isEmpty() && selectedTrackIds.isNotEmpty() ->
                "🎵 ${selectedTrackIds.size} ${Loc.getText("selected_files_label")}"
            else ->
                "${selectedFolderIds.size + selectedTrackIds.size} ${Loc.getText("selected_folders_files_label")}"
        }
    }

    val isSpecificSourceFiltered = selectedFolderIds.isNotEmpty() || selectedTrackIds.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("story_vocab_picker"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // TOP BAR: Saved Stories button + Story Options Configure button
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

        // PROMINENT SOURCE FILTER CARD (Explicitly filter by file or folder)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSpecificSourceFiltered) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            border = BorderStroke(
                1.dp,
                if (isSpecificSourceFiltered) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isSpecificSourceFiltered) Icons.Filled.FilterAlt else Icons.Filled.Source,
                            contentDescription = null,
                            tint = if (isSpecificSourceFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = Loc.getText("story_source_label"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currentSourceLabel,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSpecificSourceFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Change Filter Button
                        Button(
                            onClick = { showFolderFileFilterDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Loc.getText("story_filter_by_folder_file"), fontSize = 11.5.sp)
                        }

                        // Reset Filter Button (if active)
                        if (isSpecificSourceFiltered) {
                            IconButton(
                                onClick = {
                                    selectedFolderIds = emptySet()
                                    selectedTrackIds = emptySet()
                                    val resetNotes = computeFilteredNotes(emptySet(), emptySet(), selectedTag, searchQuery)
                                    onWordsChanged(extractTargetWords(resetNotes))
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = Loc.getText("story_clear_source_filter"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // SEARCH & TAG TOOLBAR
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
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
                        // Word Count Badge
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
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(Loc.getText("select_all"), fontSize = 11.5.sp)
                            }

                            // Deselect All
                            TextButton(
                                onClick = { onWordsChanged(emptyList()) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(Loc.getText("deselect_all"), fontSize = 11.5.sp)
                            }
                        }
                    }
                }

                // TAGS FILTER CHIPS ROW (if tags exist)
                if (allTags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = {
                                    selectedTag = null
                                    val updatedNotes = computeFilteredNotes(selectedFolderIds, selectedTrackIds, null, searchQuery)
                                    onWordsChanged(extractTargetWords(updatedNotes))
                                },
                                label = { Text("All Tags", fontSize = 10.5.sp) },
                                modifier = Modifier.height(26.dp)
                            )
                        }
                        items(allTags) { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = {
                                    val newTag = if (selectedTag == tag) null else tag
                                    selectedTag = newTag
                                    val updatedNotes = computeFilteredNotes(selectedFolderIds, selectedTrackIds, newTag, searchQuery)
                                    onWordsChanged(extractTargetWords(updatedNotes))
                                },
                                label = { Text(tag, fontSize = 10.5.sp) },
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }
                }
            }
        }

        // CAP WARNING & BATCH SPLIT (If > 20 words selected)
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

        // WORD REVIEW LIST WITH SOURCE FILE BADGE
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            if (selectedWords.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No words found in this source. Change the file filter or reset to All Notes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(selectedWords, key = { it.word.lowercase() }) { item ->
                        val originNote = item.noteId?.let { noteById[it] }
                        val originSource = originNote?.trackName?.ifBlank { null }
                            ?: originNote?.folderName?.ifBlank { null }

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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.word,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (originSource != null) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(10.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = originSource,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

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

    // FOLDER / FILE TREE FILTER DIALOG (Hierarchical selection of specific files or folders)
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
                val updatedNotes = computeFilteredNotes(newFolders, newTracks, selectedTag, searchQuery)
                onWordsChanged(extractTargetWords(updatedNotes))
                showFolderFileFilterDialog = false
            }
        )
    }

    // STORY OPTIONS CONFIGURATION DIALOG
    if (showOptionsDialog) {
        StoryOptionsDialog(
            currentOptions = options,
            onDismiss = { showOptionsDialog = false },
            onSave = { newOptions ->
                onOptionsChanged(newOptions)
                showOptionsDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryOptionsDialog(
    currentOptions: StoryOptions,
    onDismiss: () -> Unit,
    onSave: (StoryOptions) -> Unit
) {
    var selectedLength by remember { mutableStateOf(currentOptions.length) }
    var selectedLevel by remember { mutableStateOf(currentOptions.level) }
    var selectedGenre by remember { mutableStateOf(currentOptions.genre) }

    val lengths = listOf("SHORT", "MEDIUM", "LONG")
    val levels = listOf("BEGINNER", "INTERMEDIATE", "ADVANCED")
    val genres = listOf(
        "SLICE_OF_LIFE", "MYSTERY", "ADVENTURE", "SCI_FI", "HUMOROUS", "INSPIRATIONAL", "HISTORICAL"
    )

    fun lengthLabel(l: String) = when (l) {
        "SHORT" -> Loc.getText("story_length_short")
        "LONG" -> Loc.getText("story_length_long")
        else -> Loc.getText("story_length_medium")
    }

    fun levelLabel(lv: String) = when (lv) {
        "BEGINNER" -> Loc.getText("story_level_beginner")
        "ADVANCED" -> Loc.getText("story_level_advanced")
        else -> Loc.getText("story_level_intermediate")
    }

    fun genreLabel(g: String) = when (g) {
        "MYSTERY" -> Loc.getText("story_genre_mystery")
        "ADVENTURE" -> Loc.getText("story_genre_adventure")
        "SCI_FI" -> Loc.getText("story_genre_sci_fi")
        "HUMOROUS" -> Loc.getText("story_genre_humorous")
        "INSPIRATIONAL" -> Loc.getText("story_genre_inspirational")
        "HISTORICAL" -> Loc.getText("story_genre_historical")
        else -> Loc.getText("story_genre_slice_of_life")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(Loc.getText("story_options_title"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Length
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Loc.getText("story_length"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        lengths.forEach { l ->
                            FilterChip(
                                selected = selectedLength == l,
                                onClick = { selectedLength = l },
                                label = { Text(lengthLabel(l), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Level
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Loc.getText("story_level"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        levels.forEach { lv ->
                            FilterChip(
                                selected = selectedLevel == lv,
                                onClick = { selectedLevel = lv },
                                label = { Text(levelLabel(lv), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Genre
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Loc.getText("story_genre"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        genres.forEach { g ->
                            FilterChip(
                                selected = selectedGenre == g,
                                onClick = { selectedGenre = g },
                                label = { Text(genreLabel(g), fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    currentOptions.copy(
                        length = selectedLength,
                        level = selectedLevel,
                        genre = selectedGenre
                    )
                )
            }) {
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
