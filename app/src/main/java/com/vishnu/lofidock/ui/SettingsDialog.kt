package com.vishnu.lofidock.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vishnu.lofidock.core.ClockFace
import com.vishnu.lofidock.data.DockSettings
import com.vishnu.lofidock.data.PrefKeys
import com.vishnu.lofidock.data.SettingsRepository
import java.util.Locale

/** Full-screen settings, the same options and wording as the iOS app. */
@Composable
fun SettingsDialog(settings: DockSettings, repo: SettingsRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var editingTime by remember { mutableStateOf<Pair<String, Int>?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(hex(0x141019)), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lofi Dock", style = MaterialTheme.typography.headlineSmall, color = Palette.cream)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Done", color = Palette.lamp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                item {
                    Section("Clock") {
                        ClockFace.entries.forEach { face ->
                            Row(
                                Modifier.fillMaxWidth().clickable { repo.setFace(face) }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = settings.face == face, onClick = { repo.setFace(face) })
                                Text(face.title, color = Palette.cream)
                            }
                        }
                    }
                }
                item {
                    Section(
                        "Keep screen on",
                        footer = if (settings.awakeOnlyWhileCharging)
                            "The screen stays on while the phone is charging. Off the charger it follows your screen timeout."
                        else "The screen stays on whenever Lofi Dock is open, whatever the screen timeout is set to. Off the charger that uses more battery.",
                    ) {
                        SwitchRow("Only while charging", settings.awakeOnlyWhileCharging) { repo.set(PrefKeys.AWAKE_ONLY_WHILE_CHARGING, it) }
                    }
                }
                item {
                    Section(footer = "Dims the screen and warms its colours during these hours, so the clock doesn't light up a dark room.") {
                        SwitchRow("Night dimming", settings.nightDimming) { repo.set(PrefKeys.NIGHT_DIMMING, it) }
                        if (settings.nightDimming) {
                            ValueRow("From", minutesText(settings.nightStart)) { editingTime = "start" to settings.nightStart }
                            ValueRow("Until", minutesText(settings.nightEnd)) { editingTime = "end" to settings.nightEnd }
                        }
                    }
                }
                item {
                    Section(footer = "The falling-rain animation. The rain sound has its own switch under Sound.") {
                        SwitchRow("Rain on the window", settings.rainVisual) { repo.set(PrefKeys.RAIN_VISUAL, it) }
                    }
                }
                item {
                    Section(footer = "Shifts the picture a few pixels every minute. Worth keeping on for OLED screens; harmless on LCD.") {
                        SwitchRow("Burn-in protection", settings.burnInShift) { repo.set(PrefKeys.BURN_IN, it) }
                    }
                }
                item {
                    Section(footer = "Slows the background animation when the phone heats up on the charger, or in Battery Saver. The clock keeps exact time either way.") {
                        SwitchRow("Ease off when warm", settings.adaptiveMotion) { repo.set(PrefKeys.ADAPTIVE_MOTION, it) }
                    }
                }
                item {
                    Section("Sound", footer = "Mute also silences the timer chime. A finished timer still blinks and vibrates.") {
                        SwitchRow("Mute all sound", settings.muted) { repo.set(PrefKeys.MUTED, it) }
                        SwitchRow("Rain", settings.rain, enabled = !settings.muted) { repo.set(PrefKeys.RAIN, it) }
                        SwitchRow("Vinyl crackle", settings.vinyl, enabled = !settings.muted) { repo.set(PrefKeys.VINYL, it) }
                    }
                }
                item {
                    Section(
                        "Show while charging",
                        footer = "Lofi Dock is also a screen saver. Choose it under Display → Screen saver and set it to start while charging, and the clock appears whenever the phone is on the charger.",
                    ) {
                        ValueRow("Screen saver settings", "Open") {
                            try {
                                context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
                            } catch (_: ActivityNotFoundException) {
                                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                            }
                        }
                    }
                }
            }
        }
    }
    editingTime?.let { (which, minutes) ->
        TimeDialog(minutes, onDismiss = { editingTime = null }) { picked ->
            repo.set(if (which == "start") PrefKeys.NIGHT_START else PrefKeys.NIGHT_END, picked)
            editingTime = null
        }
    }
}

private fun minutesText(minutes: Int) = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

@Composable
private fun Section(title: String? = null, footer: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        title?.let { Text(it.uppercase(), color = Palette.cream.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium) }
        Column(
            Modifier.padding(top = 6.dp).fillMaxWidth()
                .background(Palette.cream.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) { content() }
        footer?.let {
            Text(it, Modifier.padding(top = 6.dp, start = 4.dp), color = Palette.cream.copy(alpha = 0.45f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Palette.cream.copy(alpha = if (enabled) 1f else 0.4f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Palette.cream)
        Text(value, color = Palette.lamp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
