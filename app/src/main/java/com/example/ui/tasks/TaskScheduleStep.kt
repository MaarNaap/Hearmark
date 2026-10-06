package com.example.ui

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

@Composable
fun TaskScheduleStep(
    formState: TaskFormState,
    context: Context
) {
    Text(Loc.getText("step_3"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
    Spacer(modifier = Modifier.height(12.dp))
    Text(Loc.getText("select_days"), fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(8.dp))
    val weekDays = listOf(
        Pair("SUNDAY", "الأحد"),
        Pair("MONDAY", "الإثنين"),
        Pair("TUESDAY", "الثلاثاء"),
        Pair("WEDNESDAY", "الأربعاء"),
        Pair("THURSDAY", "الخميس"),
        Pair("FRIDAY", "الجمعة"),
        Pair("SATURDAY", "السبت")
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        weekDays.forEach { (en, ar) ->
            val checked = formState.scheduledDays.contains(en)
            val label = if (Loc.currentLanguage == "ar") {
                when (en) {
                    "SUNDAY" -> "أحد"
                    "MONDAY" -> "اثنين"
                    "TUESDAY" -> "ثلاثاء"
                    "WEDNESDAY" -> "أربعاء"
                    "THURSDAY" -> "خميس"
                    "FRIDAY" -> "جمعة"
                    "SATURDAY" -> "سبت"
                    else -> ar
                }
            } else {
                when (en) {
                    "SUNDAY" -> "Sun"
                    "MONDAY" -> "Mon"
                    "TUESDAY" -> "Tue"
                    "WEDNESDAY" -> "Wed"
                    "THURSDAY" -> "Thu"
                    "FRIDAY" -> "Fri"
                    "SATURDAY" -> "Sat"
                    else -> en
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (checked) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable {
                        val current = formState.scheduledDays.toMutableSet()
                        if (checked) current.remove(en) else current.add(en)
                        formState.scheduledDays = current
                    }
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    Text(Loc.getText("reminder_time_label"), fontWeight = FontWeight.Bold)
    Button(
        onClick = {
            val cal = Calendar.getInstance()
            TimePickerDialog(
                context,
                { _, h, m ->
                    val ampm = if (h >= 12) "PM" else "AM"
                    val displayH = if (h % 12 == 0) 12 else h % 12
                    formState.reminderTime = String.format(Locale.US, "%02d:%02d %s", displayH, m, ampm)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        },
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text("${Loc.getText("change_time")}: ${formState.reminderTime.toWesternDigits()}")
    }
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 14.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
    // Optional Daily Mini-Goal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { formState.enableDailyGoal = !formState.enableDailyGoal }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Loc.getText("daily_goal_title"),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = Loc.getText("daily_goal_desc"),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
        Switch(
            checked = formState.enableDailyGoal,
            onCheckedChange = { formState.enableDailyGoal = it },
            modifier = Modifier.testTag("daily_goal_switch")
        )
    }
    if (formState.enableDailyGoal) {
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = Loc.getText("daily_goal_plays_label"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { if (formState.dailyTargetValue > 1) formState.dailyTargetValue-- },
                        modifier = Modifier.size(36.dp).testTag("dec_daily_goal_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Decrease daily goal",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "${formState.dailyTargetValue}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    FilledTonalIconButton(
                        onClick = { formState.dailyTargetValue++ },
                        modifier = Modifier.size(36.dp).testTag("inc_daily_goal_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Increase daily goal",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Loc.getText("daily_goal_unit"),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
