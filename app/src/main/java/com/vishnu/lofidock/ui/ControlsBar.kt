package com.vishnu.lofidock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishnu.lofidock.core.ClockFace

/**
 * The bar a tap brings up: which face is showing (tap to go to the next),
 * mute, rain and vinyl, settings, and a bolt while charging.
 */
@Composable
fun ControlsBar(
    face: ClockFace,
    muted: Boolean,
    rainOn: Boolean,
    vinylOn: Boolean,
    isCharging: Boolean,
    onNextFace: () -> Unit,
    onMute: () -> Unit,
    onRain: () -> Unit,
    onVinyl: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(CircleShape)
            .background(Palette.barBackground)
            .border(1.dp, Palette.cream.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 10.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.fillMaxHeight().clickable(onClick = onNextFace).padding(horizontal = 10.dp)
                .semantics { contentDescription = "Clock face: ${face.title}. Next face" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ClockFace.entries.forEach {
                Box(Modifier.size(6.dp).background(Palette.cream.copy(alpha = if (it == face) 0.9f else 0.3f), CircleShape))
            }
            Text(
                face.title, Modifier.padding(start = 4.dp),
                style = TextStyle(color = Palette.cream.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(Palette.cream.copy(alpha = 0.18f)))
        BarButton(
            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            if (muted) Palette.terracotta else Palette.cream.copy(alpha = 0.85f),
            "Mute all sound", if (muted) "On" else "Off", onMute,
        )
        // While muted, tapping a sound unmutes and plays it (handled by the caller).
        BarButton(Icons.Filled.WaterDrop, soundTint(rainOn && !muted), "Rain sound", if (rainOn) "On" else "Off", onRain)
        BarButton(Icons.Filled.Album, soundTint(vinylOn && !muted), "Vinyl crackle", if (vinylOn) "On" else "Off", onVinyl)
        BarButton(Icons.Filled.Settings, Palette.cream.copy(alpha = 0.85f), "Settings", null, onSettings)
        if (isCharging) {
            Icon(
                Icons.Filled.Bolt, null,
                Modifier.padding(horizontal = 4.dp).size(16.dp).semantics { contentDescription = "Charging" },
                tint = Palette.lamp.copy(alpha = 0.8f),
            )
        }
    }
}

private fun soundTint(on: Boolean) = Palette.cream.copy(alpha = if (on) 0.9f else 0.3f)

@Composable
private fun BarButton(icon: ImageVector, tint: Color, label: String, state: String?, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                state?.let { stateDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
    }
} 
