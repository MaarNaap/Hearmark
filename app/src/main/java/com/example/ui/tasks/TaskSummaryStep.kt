package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Playlist

@Composable
fun TaskSummaryStep(
    formState: TaskFormState,
    allFolders: List<Folder>,
    allPlaylists: List<Playlist>,
    allTracks: List<AudioTrack>
) {
    Text(Loc.getText("step_4"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
    Spacer(modifier = Modifier.height(12.dp))
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title
            val currentWizardTitle = formState.resolveFinalTitle(allFolders, allPlaylists, allTracks)
            Row {
                Text(
                    text = "${Loc.getText("wizard_title")}: ", 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = currentWizardTitle, fontWeight = FontWeight.Normal)
            }
            // Source details
            val typeLabel = when (formState.sourceType) {
                "FOLDER" -> Loc.getText("use_folder")
                "PLAYLIST" -> Loc.getText("use_playlist")
                "TRACKS" -> Loc.getText("use_tracks")
                else -> formState.sourceType
            }
            
            val sourceDetailsName = when (formState.sourceType) {
                "FOLDER" -> allFolders.find { it.id == formState.sourceId }?.folderName ?: ""
                "PLAYLIST" -> allPlaylists.find { it.id == formState.sourceId }?.name ?: ""
                "TRACKS" -> String.format(Loc.getText("wizard_manual_files_selected"), formState.selectedManualTrackIds.size)
                else -> ""
            }
            
            Row {
                Text(
                    text = "${Loc.getText("wizard_source")}: ", 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (sourceDetailsName.isNotEmpty()) "$typeLabel ($sourceDetailsName)" else typeLabel,
                    fontWeight = FontWeight.Normal
                )
            }
            // Target
            val targetDesc = if (formState.targetType == "PLAY_COUNT") {
                String.format(Loc.getText("goal_type_plays_desc"), formState.targetValue)
            } else {
                String.format(Loc.getText("goal_type_days_desc"), formState.targetValue)
            }
            
            Row {
                Text(
                    text = "${Loc.getText("wizard_target")}: ", 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = targetDesc, fontWeight = FontWeight.Normal)
            }
            // Custom Completion Threshold info in summary
            Row {
                Text(
                    text = Loc.getText("wizard_completion_threshold_label"),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (formState.useCustomThreshold) {
                        "${formState.taskThresholdValue.toInt()}%"
                    } else {
                        Loc.getText("wizard_default_threshold")
                    },
                    fontWeight = FontWeight.Normal
                )
            }
            // Scheduled days translated
            val mappedDays = formatScheduledDays(formState.scheduledDays.joinToString(","))
            
            Row {
                Text(
                    text = Loc.getText("wizard_alert_schedule_label"), 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = String.format(Loc.getText("wizard_alert_schedule_format"), mappedDays, formState.reminderTime),
                    fontWeight = FontWeight.Normal
                )
            }
            // Daily Goal Summary
            Row {
                Text(
                    text = Loc.getText("wizard_daily_goal_label"),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (formState.enableDailyGoal && formState.dailyTargetValue > 0) {
                        "${formState.dailyTargetValue} ${Loc.getText("daily_goal_unit")}"
                    } else {
                        Loc.getText("daily_goal_unset")
                    },
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
