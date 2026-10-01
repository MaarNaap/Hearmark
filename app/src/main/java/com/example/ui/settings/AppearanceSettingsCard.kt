package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppearanceSettingsCard(
    chosenTheme: String,
    onThemeChange: (String) -> Unit,
    chosenLang: String,
    onLangChange: (String) -> Unit
) {
    // App Theme Selector
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(Loc.getText("theme"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            listOf("system" to Loc.getText("system"), "light" to Loc.getText("light"), "dark" to Loc.getText("dark")).forEach { (key, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(key) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = chosenTheme == key, onClick = { onThemeChange(key) })
                    Text(label, modifier = Modifier.padding(start = 8.dp), fontSize = 12.sp)
                }
            }
        }
    }

    // Language Selector
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(Loc.getText("app_language"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            listOf("ar" to Loc.getText("ar_label"), "en" to Loc.getText("en_label")).forEach { (key, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLangChange(key) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = chosenLang == key, onClick = { onLangChange(key) })
                    Text(label, modifier = Modifier.padding(start = 8.dp), fontSize = 12.sp)
                }
            }
        }
    }
}
