package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskThresholdLabelsStep(
    formState: TaskFormState,
    availableExistingLabels: List<String>
) {
    Text(Loc.getText("step_2"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
    Spacer(modifier = Modifier.height(12.dp))
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = formState.targetType == "PLAY_COUNT", onClick = { formState.targetType = "PLAY_COUNT" })
        Text(Loc.getText("goal_type_plays"))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = formState.targetType == "DAYS_COUNT", onClick = { formState.targetType = "DAYS_COUNT" })
        Text(Loc.getText("goal_type_days"))
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(Loc.getText("target_value_label"), fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (formState.targetValue > 1) formState.targetValue-- }) {
            Icon(Icons.Filled.RemoveCircleOutline, "Dec")
        }
        Text("${formState.targetValue}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        IconButton(onClick = { formState.targetValue++ }) {
            Icon(Icons.Filled.AddCircleOutline, "Inc")
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
    Spacer(modifier = Modifier.height(12.dp))
    // Custom Completion Threshold Toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { formState.useCustomThreshold = !formState.useCustomThreshold }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Loc.getText("custom_threshold_toggle"),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Switch(
            checked = formState.useCustomThreshold,
            onCheckedChange = { formState.useCustomThreshold = it },
            modifier = Modifier.testTag("custom_threshold_switch")
        )
    }
    if (formState.useCustomThreshold) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Loc.getText("custom_threshold_slider_label") + ": ${formState.taskThresholdValue.toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = formState.taskThresholdValue,
                    onValueChange = { formState.taskThresholdValue = it },
                    valueRange = 70f..100f,
                    steps = 30,
                    modifier = Modifier.testTag("custom_threshold_slider")
                )
                Text(
                    text = Loc.getText("eligible_threshold_note"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
    Spacer(modifier = Modifier.height(12.dp))
    // Labels or Tags section
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Label,
            contentDescription = "Labels",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = Loc.getText("task_labels_title"),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = Loc.getText("task_labels_desc"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    // Input field + Add button for new label
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = formState.newLabelInput,
            onValueChange = { formState.newLabelInput = it },
            placeholder = { Text(Loc.getText("new_label_placeholder"), fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("task_label_input"),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val trimmed = formState.newLabelInput.trim()
                    if (trimmed.isNotEmpty() && !formState.taskLabels.any { it.equals(trimmed, ignoreCase = true) }) {
                        formState.taskLabels = formState.taskLabels + trimmed
                        formState.newLabelInput = ""
                    }
                }
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                val trimmed = formState.newLabelInput.trim()
                if (trimmed.isNotEmpty() && !formState.taskLabels.any { it.equals(trimmed, ignoreCase = true) }) {
                    formState.taskLabels = formState.taskLabels + trimmed
                    formState.newLabelInput = ""
                }
            },
            enabled = formState.newLabelInput.isNotBlank(),
            modifier = Modifier.testTag("add_task_label_btn")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(Loc.getText("add_label"), fontSize = 12.sp)
        }
    }
    // Selected labels chips
    if (formState.taskLabels.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            formState.taskLabels.forEach { label ->
                InputChip(
                    selected = true,
                    onClick = { formState.taskLabels = formState.taskLabels - label },
                    label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
    // Currently used labels from other tasks / DB
    val unselectedExistingLabels = availableExistingLabels.filter { exist ->
        !formState.taskLabels.any { it.equals(exist, ignoreCase = true) }
    }
    if (unselectedExistingLabels.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = Loc.getText("currently_used_labels_hint"),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            unselectedExistingLabels.forEach { existing ->
                FilterChip(
                    selected = false,
                    onClick = {
                        formState.taskLabels = formState.taskLabels + existing
                    },
                    label = { Text(existing, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }
    }
}
