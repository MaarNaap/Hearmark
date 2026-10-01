package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun maskApiKey(key: String): String {
    val trimmed = key.trim()
    if (trimmed.isBlank()) return "—"
    if (trimmed.length <= 10) return "••••••••"
    return "${trimmed.take(6)}••••${trimmed.takeLast(4)}"
}

@Composable
fun GeminiSettingsCard(
    viewModel: AppViewModel,
    context: Context
) {
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingApiKey by remember { mutableStateOf<AppViewModel.SavedApiKey?>(null) }
    var dialogKeyName by remember { mutableStateOf("") }
    var dialogKeyValue by remember { mutableStateOf("") }
    var dialogKeySetAsActive by remember { mutableStateOf(true) }
    var dialogKeyPasswordVisible by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<AppViewModel.SavedApiKey?>(null) }
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth().testTag("card_gemini_ai_settings"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
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
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Loc.getText("gemini_ai_assistant"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = Loc.getText("custom_gemini_api_key_multi_desc"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Active Key Status Banner
            val activeSavedKey = viewModel.savedApiKeys.firstOrNull { it.id == viewModel.activeApiKeyId }
            if (activeSavedKey != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_api_key_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = Loc.getText("active_key_label") + ":",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = activeSavedKey.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = maskApiKey(activeSavedKey.key),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = Loc.getText("in_use"),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("no_active_api_key_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = Loc.getText("no_api_keys_saved"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // List of Saved Keys (with 1-tap switching)
            if (viewModel.savedApiKeys.isNotEmpty()) {
                Text(
                    text = Loc.getText("saved_api_keys") + " (${viewModel.savedApiKeys.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    viewModel.savedApiKeys.forEach { item ->
                        val isSelected = item.id == viewModel.activeApiKeyId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (!isSelected) {
                                        viewModel.selectActiveApiKey(item.id)
                                        Toast.makeText(
                                            context,
                                            Loc.getFormattedText("key_switched_success", item.name),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                .testTag("saved_api_key_item_${item.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        if (!isSelected) {
                                            viewModel.selectActiveApiKey(item.id)
                                            Toast.makeText(
                                                context,
                                                Loc.getFormattedText("key_switched_success", item.name),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp).testTag("radio_api_key_${item.id}")
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isSelected) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = Loc.getText("active_key_badge"),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = maskApiKey(item.key),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        editingApiKey = item
                                        dialogKeyName = item.name
                                        dialogKeyValue = item.key
                                        dialogKeySetAsActive = isSelected
                                        dialogKeyPasswordVisible = false
                                        showAddEditDialog = true
                                    },
                                    modifier = Modifier.size(32.dp).testTag("btn_edit_api_key_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = Loc.getText("edit_api_key"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        keyToDelete = item
                                    },
                                    modifier = Modifier.size(32.dp).testTag("btn_delete_api_key_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = Loc.getText("delete_api_key"),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Add Key Button
            Button(
                onClick = {
                    editingApiKey = null
                    dialogKeyName = "Key ${viewModel.savedApiKeys.size + 1}"
                    dialogKeyValue = ""
                    dialogKeySetAsActive = viewModel.savedApiKeys.isEmpty()
                    dialogKeyPasswordVisible = false
                    showAddEditDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("btn_add_new_api_key"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = Loc.getText("add_api_key"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Add / Edit API Key Dialog
    if (showAddEditDialog) {
        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = {
                Text(
                    text = if (editingApiKey == null) Loc.getText("add_api_key") else Loc.getText("edit_api_key"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = dialogKeyName,
                        onValueChange = { dialogKeyName = it },
                        label = { Text(Loc.getText("key_name_label"), fontSize = 12.sp) },
                        placeholder = { Text(Loc.getText("key_name_placeholder"), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_api_key_name_input")
                    )

                    OutlinedTextField(
                        value = dialogKeyValue,
                        onValueChange = { dialogKeyValue = it },
                        label = { Text(Loc.getText("api_key_value_label"), fontSize = 12.sp) },
                        placeholder = { Text(Loc.getText("api_key_value_placeholder"), fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = if (dialogKeyPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrBlank()) {
                                            dialogKeyValue = clipText.trim()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { dialogKeyPasswordVisible = !dialogKeyPasswordVisible },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (dialogKeyPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (dialogKeyPasswordVisible) "Hide key" else "Show key",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_api_key_value_input")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dialogKeySetAsActive = !dialogKeySetAsActive }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = dialogKeySetAsActive,
                            onCheckedChange = { dialogKeySetAsActive = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Loc.getText("set_as_active_key"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogKeyValue.isBlank()) {
                            Toast.makeText(context, Loc.getText("api_key_cannot_be_empty"), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (editingApiKey == null) {
                            viewModel.addSavedApiKey(dialogKeyName, dialogKeyValue, dialogKeySetAsActive)
                            Toast.makeText(context, Loc.getText("key_added_success"), Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.updateSavedApiKey(editingApiKey!!.id, dialogKeyName, dialogKeyValue)
                            if (dialogKeySetAsActive) {
                                viewModel.selectActiveApiKey(editingApiKey!!.id)
                            }
                            Toast.makeText(context, Loc.getText("key_updated_success"), Toast.LENGTH_SHORT).show()
                        }
                        showAddEditDialog = false
                    },
                    enabled = dialogKeyValue.isNotBlank()
                ) {
                    Text(Loc.getText("save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (keyToDelete != null) {
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = {
                Text(
                    text = Loc.getText("delete_api_key"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = Loc.getFormattedText("delete_api_key_confirm", keyToDelete!!.name),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = keyToDelete
                        if (target != null) {
                            viewModel.deleteSavedApiKey(target.id)
                            Toast.makeText(context, Loc.getText("key_deleted_success"), Toast.LENGTH_SHORT).show()
                        }
                        keyToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("delete"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { keyToDelete = null }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }
}
