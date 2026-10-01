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
            val isAr = Loc.currentLanguage == "ar"
            
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
                "TRACKS" -> {
                    if (isAr) {
                        "${formState.selectedManualTrackIds.size} ملف(ات) صوتية محددة"
                    } else {
                        "${formState.selectedManualTrackIds.size} files selected manually"
                    }
                }
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
                if (isAr) "الاستماع لكل ملف ${formState.targetValue} مرات كاملة" else "Listen to each file ${formState.targetValue} times fully"
            } else {
                if (isAr) "الاستماع لكل ملف في ${formState.targetValue} أيام مختلفة" else "Listen to each file on ${formState.targetValue} different days"
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
                    text = if (isAr) "عتبة الاكتمال: " else "Completion Threshold: ",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (formState.useCustomThreshold) {
                        "${formState.taskThresholdValue.toInt()}%"
                    } else {
                        if (isAr) "تلقائي (حسب الإعدادات)" else "Default Settings Threshold"
                    },
                    fontWeight = FontWeight.Normal
                )
            }
            // Scheduled days translated
            val mappedDays = if (formState.scheduledDays.size >= 7) {
                Loc.getText("all_days")
            } else {
                formState.scheduledDays.map { day ->
                    if (isAr) {
                        when (day) {
                            "SUNDAY" -> "أحد"
                            "MONDAY" -> "اثنين"
                            "TUESDAY" -> "ثلاث"
                            "WEDNESDAY" -> "أربع"
                            "THURSDAY" -> "خميس"
                            "FRIDAY" -> "جمعة"
                            "SATURDAY" -> "سبت"
                            else -> day
                        }
                    } else {
                        when (day) {
                            "SUNDAY" -> "Sun"
                            "MONDAY" -> "Mon"
                            "TUESDAY" -> "Tue"
                            "WEDNESDAY" -> "Wed"
                            "THURSDAY" -> "Thu"
                            "FRIDAY" -> "Fri"
                            "SATURDAY" -> "Sat"
                            else -> day
                        }
                    }
                }.joinToString(", ")
            }
            
            Row {
                Text(
                    text = if (isAr) "جدولة التنبيهات: " else "Alert Schedule: ", 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isAr) "كل [$mappedDays] الساعة ${formState.reminderTime}" else "On [$mappedDays] at ${formState.reminderTime}",
                    fontWeight = FontWeight.Normal
                )
            }
            // Daily Goal Summary
            Row {
                Text(
                    text = if (isAr) "الهدف اليومي: " else "Daily Goal: ",
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
