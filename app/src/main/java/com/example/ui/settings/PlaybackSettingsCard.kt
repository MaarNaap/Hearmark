package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.VoiceBoostController
import kotlin.math.roundToInt

@Composable
fun PlaybackSettingsCard(
    sliderValue: Float,
    onSliderValueChange: (Float) -> Unit,
    skipVal: Int,
    onSkipValChange: (Int) -> Unit,
    voiceBoostLevel: Int,
    onVoiceBoostChange: (Int) -> Unit
) {
    // Threshold slider
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(Loc.getText("completion_slider") + ": ${sliderValue.toInt()}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Slider(
                value = sliderValue,
                onValueChange = onSliderValueChange,
                valueRange = 80f..100f,
                steps = 20,
                modifier = Modifier.testTag("threshold_slider")
            )
            Text(Loc.getText("eligible_threshold_note"), fontSize = 10.sp, color = Color.Gray)
        }
    }

    // skip durations
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(Loc.getText("skip_duration_label"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                listOf(5, 10, 15, 30).forEach { s ->
                    val selected = skipVal == s
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSkipValChange(s) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("${s}s", color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    // voice boost
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            val boostText = if (voiceBoostLevel <= 0) {
                Loc.getText("voice_boost_off")
            } else {
                "+${VoiceBoostController.levelToDb(voiceBoostLevel)} dB"
            }
            Text(Loc.getText("voice_boost_label") + ": " + boostText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Slider(
                value = voiceBoostLevel.toFloat(),
                onValueChange = { onVoiceBoostChange(it.roundToInt()) },
                valueRange = 0f..VoiceBoostController.MAX_LEVEL.toFloat(),
                steps = VoiceBoostController.MAX_LEVEL - 1,
                modifier = Modifier.testTag("voice_boost_slider")
            )
            Text(Loc.getText("voice_boost_note"), fontSize = 10.sp, color = Color.Gray)
        }
    }
}
