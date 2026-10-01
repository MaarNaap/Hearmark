package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun TaskFooterNav(
    currentStep: Int,
    isEditing: Boolean,
    onPrevOrCancel: () -> Unit,
    onQuickSave: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 1) {
                TextButton(onClick = onPrevOrCancel) {
                    Text(Loc.getText("prev"))
                }
            } else {
                TextButton(onClick = onPrevOrCancel) {
                    Text(Loc.getText("cancel"))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isEditing && currentStep < 4) {
                    OutlinedButton(
                        onClick = onQuickSave,
                        modifier = Modifier.testTag("quick_save_btn")
                    ) {
                        Text(Loc.getText("update_task_btn"))
                    }
                }

                if (currentStep < 4) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.testTag("step_next_btn")
                    ) {
                        Text(Loc.getText("next"))
                    }
                } else {
                    Button(
                        onClick = onSave,
                        modifier = Modifier.testTag("step_save_btn")
                    ) {
                        Text(if (isEditing) Loc.getText("update_task_btn") else Loc.getText("save_task"))
                    }
                }
            }
        }
    }
}
