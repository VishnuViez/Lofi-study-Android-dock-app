package com.vishnu.lofidock.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishnu.lofidock.core.CountdownTimer
import com.vishnu.lofidock.core.TimeFormatting
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * A countdown timer in the familiar iPhone Clock layout: wheels to set it, a
 * ring while it runs, Cancel on the left and Start/Pause on the right.
 */
@Composable
fun TimerFace(
    now: Long,
    sessionSeconds: Double,
    scale: Float,
    timer: CountdownTimer,
    pickedLengthSeconds: Double,
    onPickLength: (Double) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberDims(scale)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LofiBackdrop()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d.dp(8f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(40f))) {
                RoundButton("Cancel", Palette.cream, d, quiet = true, enabled = !timer.isIdle, onClick = onCancel)
                Box(Modifier.size(d.dp(360f), d.dp(290f)), contentAlignment = Alignment.Center) {
                    if (timer.isIdle) DurationPicker(pickedLengthSeconds, onPickLength, d)
                    else CountdownRing(timer, now, d)
                }
                when (timer.phase) {
                    CountdownTimer.Phase.Idle ->
                        RoundButton("Start", hex(0xFFC27A), d, enabled = pickedLengthSeconds >= 1, onClick = onStart)
                    is CountdownTimer.Phase.Running -> RoundButton("Pause", hex(0xE8D27A), d, onClick = onPause)
                    is CountdownTimer.Phase.Paused -> RoundButton("Resume", hex(0xFFC27A), d, onClick = onResume)
                    is CountdownTimer.Phase.Finished -> RoundButton("Stop", Palette.terracotta, d, onClick = onCancel)
                }
            }
            SessionLine(now, sessionSeconds, rememberDims(scale * 0.85f), showsTime = true)
        }
    }
}

/** Remaining time inside a ring that empties as the countdown runs. */
@Composable
fun CountdownRing(timer: CountdownTimer, now: Long, d: Dims) {
    val fraction by animateFloatAsState(timer.fractionRemaining(now).toFloat(), tween(1000, easing = LinearEasing), label = "ring")
    // A finished timer blinks once a second until stopped.
    val blinkOff = timer.isFinished && (now / 1000) % 2 == 0L
    val alpha by animateFloatAsState(if (blinkOff) 0.45f else 1f, tween(600), label = "blink")
    Box(Modifier.size(d.dp(270f)).alpha(alpha), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx() * d.scale
            inset(stroke / 2) {
                drawArc(Palette.cream.copy(alpha = 0.1f), 0f, 360f, false, style = Stroke(stroke))
                drawArc(Palette.lamp, -90f, 360f * fraction, false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d.dp(8f))) {
            Text(
                if (timer.isFinished) "Time's up" else TimeFormatting.countdown(timer.remainingSeconds(now)),
                style = lofiText(d.sp(if (timer.isFinished) 42f else 62f), FontWeight.Light, glowRadius = 10f),
            )
            when (val phase = timer.phase) {
                is CountdownTimer.Phase.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, Modifier.size(d.dp(15f)), tint = Palette.cream.copy(alpha = 0.6f))
                    Text(" " + clockOf(phase.endsAt).joined, style = lofiText(d.sp(15f), FontWeight.Medium, alpha = 0.6f))
                }
                is CountdownTimer.Phase.Paused ->
                    Text("PAUSED", style = lofiText(d.sp(12f), FontWeight.SemiBold, alpha = 0.5f, tracking = d.sp(2.5f)))
                else -> Unit
            }
        }
    }
}

/** Hours/min/sec wheels plus one-tap presets. */
@Composable
fun DurationPicker(lengthSeconds: Double, onChange: (Double) -> Unit, d: Dims) {
    val total = lengthSeconds.toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    val wheelText = TextStyle(fontSize = d.sp(24f), fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
    val unitText = lofiText(d.sp(15f), FontWeight.SemiBold, alpha = 0.8f, glow = Color.Transparent, glowRadius = 0f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d.dp(12f))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelPicker(24, hours, { onChange((it * 3600 + minutes * 60 + seconds).toDouble()) }, d.dp(40f), d.dp(62f), wheelText)
            Text("hours", Modifier.width(d.dp(48f)), style = unitText)
            WheelPicker(60, minutes, { onChange((hours * 3600 + it * 60 + seconds).toDouble()) }, d.dp(40f), d.dp(62f), wheelText)
            Text("min", Modifier.width(d.dp(48f)), style = unitText)
            WheelPicker(60, seconds, { onChange((hours * 3600 + minutes * 60 + it).toDouble()) }, d.dp(40f), d.dp(62f), wheelText)
            Text("sec", Modifier.width(d.dp(40f)), style = unitText)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(d.dp(8f))) {
            for (preset in listOf(5, 15, 25, 45, 60)) {
                val selected = total == preset * 60
                Text(
                    "${preset}m",
                    Modifier.clip(CircleShape)
                        .background(Palette.cream.copy(alpha = if (selected) 0.24f else 0.08f))
                        .clickable { onChange(preset * 60.0) }
                        .padding(horizontal = d.dp(12f), vertical = d.dp(7f))
                        .semantics { contentDescription = "$preset minutes" },
                    style = lofiText(d.sp(14f), FontWeight.SemiBold, glow = Color.Transparent, glowRadius = 0f),
                )
            }
        }
    }
}

/**
 * A snapping wheel of numbers 0 until [count]. The value under the centre
 * band is reported when scrolling settles; external changes (presets) scroll
 * the wheel to match.
 */
@Composable
fun WheelPicker(count: Int, value: Int, onValueChange: (Int) -> Unit, itemHeight: Dp, width: Dp, textStyle: TextStyle, visibleItems: Int = 5) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index ?: currentValue
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centered }
            .filter { (scrolling, _) -> !scrolling }
            .map { it.second.coerceIn(0, count - 1) }
            .distinctUntilChanged()
            .collect { if (it != currentValue) currentOnChange(it) }
    }
    LaunchedEffect(value) {
        if (!state.isScrollInProgress && centered != value) state.animateScrollToItem(value)
    }
    Box(Modifier.width(width).height(itemHeight * visibleItems), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(itemHeight).background(Palette.cream.copy(alpha = 0.08f), RoundedCornerShape(10.dp)))
        LazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItems / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count) { i ->
                val distance = abs(i - centered)
                Text(
                    "$i",
                    Modifier.height(itemHeight).wrapContentHeight(Alignment.CenterVertically),
                    style = textStyle.copy(color = Palette.cream.copy(alpha = when (distance) { 0 -> 1f; 1 -> 0.45f; else -> 0.2f })),
                )
            }
        }
    }
}

/** Round Clock-app style button in the lofi palette. */
@Composable
fun RoundButton(label: String, tint: Color, d: Dims, quiet: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(d.dp(82f)).alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (quiet) 0.12f else 0.2f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(d.dp(3.5f))
            .border(d.dp(1.5f), tint.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = tint, fontSize = d.sp(16f), fontWeight = FontWeight.SemiBold))
    }
}

/** Shown on the other faces while a countdown runs or rings. */
@Composable
fun TimerChip(timer: CountdownTimer, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val remaining = TimeFormatting.countdown(timer.remainingSeconds(now))
    val tint = if (timer.isFinished) Palette.terracotta else Palette.cream
    Row(
        modifier.alpha(if (timer.isPaused) 0.6f else 1f)
            .clip(CircleShape)
            .background(hex(0x15101C, 0.78f))
            .border(1.dp, Palette.cream.copy(alpha = 0.1f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .height(32.dp)
            .semantics { contentDescription = if (timer.isFinished) "Timer finished. Stop" else "Timer, $remaining left" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (timer.isFinished) Icons.Filled.Notifications else Icons.Outlined.Timer, null, Modifier.size(16.dp), tint = tint)
        Text(
            if (timer.isFinished) "Time's up · Stop" else remaining,
            style = TextStyle(color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        )
    }
} 
