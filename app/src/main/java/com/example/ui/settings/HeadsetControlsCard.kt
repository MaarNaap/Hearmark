package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeadsetControlsCard(
    headsetEnabled: Boolean,
    onHeadsetEnabledChange: (Boolean) -> Unit,
    headsetAction: String,
    onHeadsetActionChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("card_headset_controls"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Loc.getText("headset_controls_title"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = headsetEnabled,
                    onCheckedChange = onHeadsetEnabledChange,
                    modifier = Modifier.testTag("switch_headset_controls")
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Loc.getText("headset_controls_desc"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (headsetEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = Loc.getText("headset_single_click_hint"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Loc.getText("headset_double_click_hint"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Loc.getText("headset_triple_click_hint"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "⚡ " + Loc.getText("headset_unplug_pause_hint"),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = Loc.getText("headset_double_action_label"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                listOf(
                    "NEXT_PREV" to Loc.getText("headset_action_track"),
                    "SKIP_SECONDS" to Loc.getText("headset_action_skip")
                ).forEach { (actionKey, actionLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHeadsetActionChange(actionKey) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = headsetAction == actionKey,
                            onClick = { onHeadsetActionChange(actionKey) }
                        )
                        Text(
                            text = actionLabel,
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
