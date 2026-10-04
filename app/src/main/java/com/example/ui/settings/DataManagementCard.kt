package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlaybackHistory
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DataManagementCard(
    viewModel: AppViewModel,
    context: Context,
    playbackHistoryList: List<PlaybackHistory>
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedPruneOption by remember { mutableStateOf("ALL") } // "ALL", "LIMIT_1000", "OLDER_YEAR"
    var showFinalConfirm by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportBackupToUri(it)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            viewModel.restoreBackupFromUri(fileUri)
        }
    }

    // Clear/Prune History button
    Card(
        modifier = Modifier.fillMaxWidth().testTag("card_clear_history"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(Loc.getText("clear_history_btn"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(Loc.getText("clear_history_dialog_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.align(Alignment.End).height(38.dp)
            ) {
                Text(Loc.getText("clear_history_btn").replace("🧹 ", ""), fontSize = 12.sp, color = Color.White)
            }
        }
    }

    // Backup & Restore Card
    Card(
        modifier = Modifier.fillMaxWidth().testTag("card_backup_restore"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(Loc.getText("backup_restore_title"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(Loc.getText("backup_restore_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        createDocumentLauncher.launch("hearmark_backup_$timeStamp.json")
                    },
                    modifier = Modifier.weight(1f).height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("📤 " + Loc.getText("export_backup_json"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = {
                        openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("📥 " + Loc.getText("restore_backup_json"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    viewModel.searchRelinkCandidates(autoOpenDialog = true)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("btn_relink_files_settings"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "🔗 " + Loc.getText("relink_files_stats_btn"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

            Text(Loc.getText("auto_backup_title"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(Loc.getText("auto_backup_desc"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val lastAutoBackupTime = remember { viewModel.getAutoBackupLastModified() }
            if (lastAutoBackupTime != null) {
                val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastAutoBackupTime))
                Spacer(modifier = Modifier.height(6.dp))
                Text("ℹ️ " + Loc.getText("latest_auto_snapshot_label") + " $dateFormatted", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    viewModel.restoreLatestAutoBackup { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(38.dp)
            ) {
                Text(Loc.getText("restore_auto_snapshot_btn"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { 
                showClearDialog = false 
                showFinalConfirm = false
            },
            title = {
                Text(
                    text = if (showFinalConfirm) Loc.getText("clear_history_confirm_title") else Loc.getText("clear_history_dialog_title"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!showFinalConfirm) {
                        Text(
                            text = Loc.getText("clear_history_dialog_desc"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Export button
                        Button(
                            onClick = { 
                                viewModel.copyHistoryClipboard(context, playbackHistoryList)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("📋 " + Loc.getText("export_data"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        
                        // Radio option buttons
                        listOf(
                            "ALL" to Loc.getText("clear_history_option_all"),
                            "LIMIT_1000" to Loc.getText("clear_history_option_limit_1000"),
                            "OLDER_YEAR" to Loc.getText("clear_history_option_older_year")
                        ).forEach { (optionKey, optionLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPruneOption = optionKey }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedPruneOption == optionKey,
                                    onClick = { selectedPruneOption = optionKey }
                                )
                                Text(
                                    text = optionLabel,
                                    modifier = Modifier.padding(start = 8.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        // Show final confirmation dialog text
                        Text(
                            text = Loc.getText("clear_history_confirm_desc"),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Summary of what is chosen
                        val summaryText = when (selectedPruneOption) {
                            "ALL" -> Loc.getText("clear_history_option_all")
                            "LIMIT_1000" -> Loc.getText("clear_history_option_limit_1000")
                            else -> Loc.getText("clear_history_option_older_year")
                        }
                        Text(
                            text = "👉 $summaryText",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!showFinalConfirm) {
                            showFinalConfirm = true
                        } else {
                            // Perform delete action
                            when (selectedPruneOption) {
                                "ALL" -> viewModel.clearAllPlaybackHistory()
                                "LIMIT_1000" -> viewModel.prunePlaybackHistoryToRecent1000()
                                "OLDER_YEAR" -> viewModel.prunePlaybackHistoryOlderThanOneYear()
                            }
                            Toast.makeText(context, Loc.getText("clear_history_success"), Toast.LENGTH_LONG).show()
                            showClearDialog = false
                            showFinalConfirm = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showFinalConfirm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (showFinalConfirm) Loc.getText("delete_completely") else Loc.getText("next"),
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (showFinalConfirm) {
                            showFinalConfirm = false
                        } else {
                            showClearDialog = false
                        }
                    }
                ) {
                    Text(text = if (showFinalConfirm) Loc.getText("prev") else Loc.getText("cancel"))
                }
            }
        )
    }
}
