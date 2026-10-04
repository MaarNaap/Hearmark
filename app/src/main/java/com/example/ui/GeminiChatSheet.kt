package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AudioContextSummary
import com.example.ai.ChatMessage
import com.example.player.AudioPlayerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun FloatingAiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .testTag("floating_ai_btn")
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .shadow(10.dp, CircleShape),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = glowAlpha),
                            MaterialTheme.colorScheme.secondary
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = Loc.getText("gemini_ai_assistant"),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiChatSheet(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onSaveToNotebookRequested: (quoteText: String, explanationText: String, trackId: Long?, startMs: Long?, endMs: Long?) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingAiResponse.collectAsStateWithLifecycle()
    val isGeneratingSubtitles by viewModel.isGeneratingSubtitles.collectAsStateWithLifecycle()
    val subtitleGenerationStatus by viewModel.subtitleGenerationStatus.collectAsStateWithLifecycle()
    val activeContext by viewModel.activeChatContext.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var isContextCardExpanded by remember { mutableStateOf(true) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Auto-scroll to latest message
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = Loc.getText("gemini_ai_assistant"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "3.5-Flash",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = Loc.getText("ai_voice_chat_title"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearChatHistory() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = Loc.getText("ai_clear_chat"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Current Context Banner (if available)
            val currentContext = activeContext
            if (currentContext != null && (currentContext.trackTitle != null || currentContext.activeSubtitleLine != null || currentContext.activeTaskTitle != null)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Headphones,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = Loc.getText("ai_context_badge"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Icon(
                                imageVector = if (isContextCardExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { isContextCardExpanded = !isContextCardExpanded }
                            )
                        }

                        if (isContextCardExpanded) {
                            Spacer(modifier = Modifier.height(4.dp))
                            if (!currentContext.trackTitle.isNullOrBlank()) {
                                Text(
                                    text = "🎵 ${currentContext.trackTitle}${if (currentContext.formattedPosition != null) " (${currentContext.formattedPosition})" else ""}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (currentContext.isFullSubtitlesContext || !currentContext.fullSubtitlesText.isNullOrBlank()) {
                                val charCount = currentContext.fullSubtitlesText?.length ?: 0
                                Text(
                                    text = "📜 " + String.format(Loc.getText("ai_full_transcript_ready"), charCount),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            } else if (!currentContext.activeSubtitleLine.isNullOrBlank()) {
                                Text(
                                    text = "💬 \"${currentContext.activeSubtitleLine}\"",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (!currentContext.activeTaskTitle.isNullOrBlank()) {
                                Text(
                                    text = "🎯 ${currentContext.activeTaskTitle}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Dedicated AI Subtitle Creation Card (Only when opened from upper icon / full subtitles context)
            if (activeContext?.isFullSubtitlesContext == true || activeContext?.audioFilePath != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = Loc.getText("ai_create_subtitles"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = Loc.getText("ai_create_subtitles_desc"),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (isGeneratingSubtitles) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = subtitleGenerationStatus.ifBlank { Loc.getText("ai_creating_subtitles") },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.generateSubtitlesForCurrentTrack(
                                        contextSummary = activeContext,
                                        onSuccess = {
                                            Toast.makeText(context, Loc.getText("ai_subtitles_success"), Toast.LENGTH_LONG).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ClosedCaption,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Loc.getText("ai_create_subtitles_btn"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Smart Context Chips (Quick Action Prompts)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeContext?.isFullSubtitlesContext == true || !activeContext?.fullSubtitlesText.isNullOrBlank()) {
                    AssistChip(
                        onClick = {
                            viewModel.generateSubtitlesForCurrentTrack(
                                contextSummary = activeContext,
                                onSuccess = {
                                    Toast.makeText(context, Loc.getText("ai_subtitles_success"), Toast.LENGTH_LONG).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        label = { Text(Loc.getText("ai_prompt_create_subtitles_chip"), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    )

                    AssistChip(
                        onClick = {
                            val promptText = Loc.getText("ai_prompt_msg_summarize_transcript")
                            viewModel.sendChatMessage(
                                prompt = promptText,
                                contextSummary = activeContext
                            )
                        },
                        label = { Text(Loc.getText("ai_prompt_summarize_transcript"), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Summarize, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                val activeSubtitleLine = activeContext?.activeSubtitleLine
                if (!activeSubtitleLine.isNullOrBlank()) {
                    AssistChip(
                        onClick = {
                            val promptText = String.format(Loc.getText("ai_prompt_msg_explain_sentence"), activeSubtitleLine)
                            viewModel.sendChatMessage(
                                prompt = promptText,
                                contextSummary = activeContext
                            )
                        },
                        label = { Text(Loc.getText("ai_prompt_explain_sentence"), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )

                    AssistChip(
                        onClick = {
                            val promptText = String.format(Loc.getText("ai_prompt_msg_translate_examples"), activeSubtitleLine)
                            viewModel.sendChatMessage(
                                prompt = promptText,
                                contextSummary = activeContext
                            )
                        },
                        label = { Text(Loc.getText("ai_prompt_translate_examples"), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                val activeTrackTitle = activeContext?.trackTitle
                if (!activeTrackTitle.isNullOrBlank()) {
                    AssistChip(
                        onClick = {
                            val promptText = String.format(Loc.getText("ai_prompt_msg_summarize_audio"), activeTrackTitle)
                            viewModel.sendChatMessage(
                                prompt = promptText,
                                contextSummary = activeContext
                            )
                        },
                        label = { Text(Loc.getText("ai_prompt_summarize_audio"), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Summarize, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )

                    AssistChip(
                        onClick = {
                            val promptText = Loc.getText("ai_prompt_msg_quiz_comprehension")
                            viewModel.sendChatMessage(
                                prompt = promptText,
                                contextSummary = activeContext
                            )
                        },
                        label = { Text(Loc.getText("ai_prompt_quiz_comprehension"), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                val activeTaskTitle = activeContext?.activeTaskTitle
                if (!activeTaskTitle.isNullOrBlank()) {
                    AssistChip(
                        onClick = {
                            val promptText = String.format(Loc.getText("ai_prompt_msg_goal_tips"), activeTaskTitle)
                            viewModel.sendChatMessage(
                                prompt = promptText,
                                contextSummary = activeContext
                            )
                        },
                        label = { Text(Loc.getText("ai_prompt_goal_tips"), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.TrackChanges, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            // Messages List Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = Loc.getText("ai_no_messages_yet"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatBubbleItem(
                                message = msg,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(msg.text))
                                    Toast.makeText(context, Loc.getText("note_copied"), Toast.LENGTH_SHORT).show()
                                },
                                onSaveToNotebook = {
                                    val currentTrack = AudioPlayerManager.currentTrack.value
                                    val currentPos = AudioPlayerManager.currentPosition.value
                                    val quote = msg.attachedContext?.activeSubtitleLine
                                        ?: AudioPlayerManager.activeSubtitleCue.value?.text
                                        ?: ""
                                    val explanation = msg.text
                                    val start = msg.attachedContext?.currentPositionMs ?: maxOf(0L, currentPos - 5000L)
                                    val end = (start + 10000L).coerceAtMost(currentTrack?.duration ?: (start + 10000L))
                                    onSaveToNotebookRequested(
                                        quote,
                                        explanation,
                                        currentTrack?.id,
                                        start,
                                        end
                                    )
                                }
                            )
                        }

                        if (isGenerating) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = Loc.getText("ai_thinking"),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Input Bar at the Bottom
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_input_field"),
                        placeholder = {
                            Text(
                                text = Loc.getText("ask_ai_placeholder"),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isGenerating) {
                                val textToSend = inputText
                                inputText = ""
                                viewModel.sendChatMessage(
                                    prompt = textToSend,
                                    contextSummary = activeContext
                                )
                            }
                        },
                        enabled = inputText.isNotBlank() && !isGenerating,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("ai_send_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    message: ChatMessage,
    onCopy: () -> Unit,
    onSaveToNotebook: () -> Unit
) {
    val isUser = message.role == "user"
    val isError = message.isError

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Optional Context header on User messages
        if (isUser && message.attachedContext?.activeSubtitleLine != null) {
            Text(
                text = "📌 \"${message.attachedContext.activeSubtitleLine}\"",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = when {
                isUser -> MaterialTheme.colorScheme.primary
                isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            },
            border = if (!isUser && !isError) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            SelectionContainer {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    FormattedMarkdownText(
                        text = message.text,
                        textColor = when {
                            isUser -> MaterialTheme.colorScheme.onPrimary
                            isError -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }

        // Action buttons for AI assistant responses
        if (!isUser && !isError) {
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Loc.getText("ai_copy_response"), fontSize = 11.sp)
                }

                TextButton(
                    onClick = onSaveToNotebook,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Loc.getText("ai_save_to_notebook"), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun FormattedMarkdownText(
    text: String,
    textColor: Color
) {
    val annotated = remember(text, textColor) {
        buildAnnotatedString {
            // Process bold formatting **bold**
            val parts = text.split("**")
            for (i in parts.indices) {
                if (i % 2 == 1) {
                    // Bold part
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor))
                    append(parts[i])
                    pop()
                } else {
                    append(parts[i])
                }
            }
        }
    }

    Text(
        text = annotated,
        fontSize = 13.5.sp,
        lineHeight = 21.sp,
        color = textColor
    )
}
