package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NoteExplanationSection(
    commentText: String,
    isGeneratingAiExplanation: Boolean,
    onCommentTextChange: (String) -> Unit,
    onTriggerAiGeneration: () -> Unit
) {
    Text(
        text = Loc.getText("note_comment"),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(6.dp))

    OutlinedTextField(
        value = commentText,
        onValueChange = onCommentTextChange,
        label = { Text(Loc.getText("note_comment")) },
        placeholder = { Text(Loc.getText("ai_note_comment_placeholder"), fontSize = 12.5.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("note_comment_input"),
        minLines = 2,
        maxLines = 5,
        shape = RoundedCornerShape(12.dp),
        trailingIcon = {
            IconButton(
                onClick = onTriggerAiGeneration,
                enabled = !isGeneratingAiExplanation,
                modifier = Modifier.testTag("ai_generate_note_button")
            ) {
                if (isGeneratingAiExplanation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = Loc.getText("ai_generate_note_button"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}
