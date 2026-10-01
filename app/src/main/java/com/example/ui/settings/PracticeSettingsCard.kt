package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack

@Composable
fun PracticeSettingsCard(
    context: Context,
    viewModel: AppViewModel,
    currentPlayingTrack: AudioTrack?,
    segmentSource: String,
    onSegmentSourceChange: (String) -> Unit,
    pauseMultiplier: Float,
    onPauseMultiplierChange: (Float) -> Unit,
    silencePadding: Long,
    onSilencePaddingChange: (Long) -> Unit,
    silenceMinDuration: Long,
    onSilenceMinDurationChange: (Long) -> Unit,
    silenceSensitivity: String,
    onSilenceSensitivityChange: (String) -> Unit,
    onOpenWaveformEditor: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("card_practice_settings"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = Loc.getText("practice_mode"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // Segment source selection
            Text(
                text = Loc.getText("segment_source"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = Loc.getText("segment_source_desc"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            listOf(
                "SILENCE" to Loc.getText("segment_source_silence"),
                "SUBTITLES" to Loc.getText("segment_source_subtitles"),
                "MANUAL" to Loc.getText("segment_source_manual")
            ).forEach { (sourceKey, sourceLabel) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSegmentSourceChange(sourceKey) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = segmentSource.equals(sourceKey, ignoreCase = true),
                        onClick = { onSegmentSourceChange(sourceKey) }
                    )
                    Text(
                        text = sourceLabel,
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 12.sp
                    )
                }
            }

            if (segmentSource.equals("MANUAL", ignoreCase = true)) {
                val trackForWaveform = currentPlayingTrack
                if (trackForWaveform != null) {
                    OutlinedButton(
                        onClick = onOpenWaveformEditor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("btn_open_waveform_editor_settings"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Loc.getText("open_waveform_editor"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text(
                        text = Loc.getText("manual_segments_desc"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // Pause length multiplier setting
            Text(
                text = Loc.getText("pause_multiplier_title"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = Loc.getText("pause_multiplier_desc"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Stepper: Minus button, center chip holding value, Plus button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val next = (Math.round((pauseMultiplier - 0.25f) * 4f) / 4f).coerceIn(0.25f, 4.0f)
                        onPauseMultiplierChange(next)
                    },
                    enabled = pauseMultiplier > 0.25f,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("btn_pause_multiplier_decrease")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Decrease multiplier",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .clickable { onPauseMultiplierChange(1.0f) }
                        .testTag("chip_pause_multiplier_value")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", pauseMultiplier) + "x",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                FilledTonalIconButton(
                    onClick = {
                        val next = (Math.round((pauseMultiplier + 0.25f) * 4f) / 4f).coerceIn(0.25f, 4.0f)
                        onPauseMultiplierChange(next)
                    },
                    enabled = pauseMultiplier < 4.0f,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("btn_pause_multiplier_increase")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Increase multiplier",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // Silence Analysis Tuning Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = Loc.getText("silence_analysis_tuning"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = Loc.getText("silence_analysis_tuning_desc"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. Word Ending Safety Buffer (Padding)
            Text(
                text = Loc.getText("silence_padding"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = Loc.getText("silence_padding_desc"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    100L to "100ms",
                    200L to "200ms ★",
                    300L to "300ms",
                    400L to "400ms"
                ).forEach { (paddingMs, label) ->
                    val isSelected = silencePadding == paddingMs
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSilencePaddingChange(paddingMs) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("chip_silence_padding_${paddingMs}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 2. Minimum Pause Duration
            Text(
                text = Loc.getText("silence_min_duration"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = Loc.getText("silence_min_duration_desc"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    350L to "350ms",
                    500L to "500ms ★",
                    750L to "750ms",
                    1000L to "1.0s"
                ).forEach { (minMs, label) ->
                    val isSelected = silenceMinDuration == minMs
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSilenceMinDurationChange(minMs) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("chip_silence_duration_${minMs}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 3. Detection Sensitivity
            Text(
                text = Loc.getText("silence_sensitivity"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = Loc.getText("silence_sensitivity_desc"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            listOf(
                "HIGH" to Loc.getText("silence_sensitivity_high"),
                "MEDIUM" to Loc.getText("silence_sensitivity_medium") + " ★",
                "LOW" to Loc.getText("silence_sensitivity_low")
            ).forEach { (sensKey, sensLabel) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSilenceSensitivityChange(sensKey) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = silenceSensitivity.equals(sensKey, ignoreCase = true),
                        onClick = { onSilenceSensitivityChange(sensKey) }
                    )
                    Text(
                        text = sensLabel,
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 12.sp
                    )
                }
            }

            // 4. Re-analyze Current Track Button
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    Toast.makeText(context, Loc.getText("reanalyzing_with_new_settings"), Toast.LENGTH_SHORT).show()
                    viewModel.reanalyzeCurrentTrackPracticeSegments(context)
                },
                modifier = Modifier.fillMaxWidth().testTag("btn_reanalyze_current_track"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(Loc.getText("reanalyze_current_track"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
