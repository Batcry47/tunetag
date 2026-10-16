package com.tunetag.player.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun openBatterySettings(ctx: Context) {
    val intents = listOf(
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
    )
    for (i in intents) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, actions: SongActions, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = vm.user.collectAsState().value.settings

    LargePage("Settings", onBack = onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingsCard("YOUR LISTENING") {
                Text(
                    "TuneTag keeps a private history of what you play and for how long, so you can see your own " +
                        "year-in-music, like Wrapped. It never leaves your phone.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button({ actions.push(Route.Stats) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Insights, null); Spacer(Modifier.width(8.dp)); Text("Open listening stats")
                }
            }

            SettingsCard("PLAYER STYLE") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(s.playerStyle == 0, { vm.updateSettings { it.copy(playerStyle = 0) } }, label = { Text("Modern") })
                    FilterChip(s.playerStyle == 1, { vm.updateSettings { it.copy(playerStyle = 1) } }, label = { Text("iPod Classic") })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "iPod Classic turns the whole app into a click-wheel iPod: scroll the wheel to move through menus " +
                        "and volume, tap the centre to select, press-and-hold the side buttons to seek.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow("Click sound", s.ipodSound) { v -> vm.updateSettings { it.copy(ipodSound = v) } }
                SwitchRow("Haptic ticks", s.ipodHaptics) { v -> vm.updateSettings { it.copy(ipodHaptics = v) } }
            }

            SettingsCard("APPEARANCE") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                        FilterChip(s.themeMode == i, { vm.updateSettings { it.copy(themeMode = i) } }, label = { Text(label) })
                    }
                }
            }

            SettingsCard("NOW PLAYING") {
                Text("Double-tap the artwork to skip")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20).forEach { sec ->
                        FilterChip(s.seekSeconds == sec, { vm.updateSettings { it.copy(seekSeconds = sec) } }, label = { Text("${sec}s") })
                    }
                }
            }

            SettingsCard("LIBRARY") {
                OutlinedButton({ vm.refresh() }, Modifier.fillMaxWidth()) { Text("Rescan music library") }
            }

            SettingsCard("TAG SOURCES") {
                Text(
                    "Auto-tagging searches every enabled source at once and picks the best match.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow("Apple Music", s.useItunes) { v -> vm.updateSettings { it.copy(useItunes = v) } }
                SwitchRow("Deezer", s.useDeezer) { v -> vm.updateSettings { it.copy(useDeezer = v) } }
                SwitchRow("MusicBrainz + Cover Art Archive", s.useMusicBrainz) { v -> vm.updateSettings { it.copy(useMusicBrainz = v) } }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(s.country, { v -> vm.updateSettings { it.copy(country = v.take(2).uppercase()) } },
                    label = { Text("Apple Music store country (US, ZA, GB…)") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Auto-select matches scoring at least ${s.minScore}%")
                Slider(s.minScore.toFloat(), { v -> vm.updateSettings { it.copy(minScore = v.toInt()) } }, valueRange = 40f..95f)
            }

            SettingsCard("KEEP MUSIC PLAYING (HUAWEI / EMUI)") {
                Text(
                    "EMUI closes background apps aggressively. Open the launch settings for TuneTag, turn off " +
                        "\"Manage automatically\", then allow Auto-launch, Secondary launch and Run in background.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton({ openBatterySettings(ctx) }, Modifier.fillMaxWidth()) { Text("Open battery / launch settings") }
            }

            Spacer(Modifier.height(LocalBottomPad.current + 16.dp))
        }
    }
}
