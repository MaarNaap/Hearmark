package com.example.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Loc
import com.example.ui.QuizTabMode

@Composable
internal fun QuizModeTabs(
    activeTabMode: QuizTabMode,
    onSelectMode: (QuizTabMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Tab 0: Subtitles
        FilterChip(
            selected = activeTabMode == QuizTabMode.TRACK,
            onClick = { onSelectMode(QuizTabMode.TRACK) },
            label = { Text(Loc.getText("unified_quiz_tab_track"), fontSize = 12.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
            },
            modifier = Modifier.weight(1f)
        )

        // Tab 1: Notebook Notes
        FilterChip(
            selected = activeTabMode == QuizTabMode.NOTEBOOK,
            onClick = { onSelectMode(QuizTabMode.NOTEBOOK) },
            label = { Text(Loc.getText("unified_quiz_tab_notes"), fontSize = 12.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
            },
            modifier = Modifier.weight(1f)
        )

        // Tab 2: Practice
        FilterChip(
            selected = activeTabMode == QuizTabMode.BANK,
            onClick = { onSelectMode(QuizTabMode.BANK) },
            label = {
                Text(Loc.getText("unified_quiz_tab_bank"), fontSize = 12.sp)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Quiz,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
            },
            modifier = Modifier.weight(1f)
        )
    }

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}
