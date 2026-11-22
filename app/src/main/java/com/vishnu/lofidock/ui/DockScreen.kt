package com.vishnu.lofidock.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishnu.lofidock.AppGraph
import com.vishnu.lofidock.core.AnimationBudget
import com.vishnu.lofidock.core.BurnInDrift
import com.vishnu.lofidock.core.ClockFace
import com.vishnu.lofidock.core.NightSchedule
import com.vishnu.lofidock.core.ScreenAwake
import com.vishnu.lofidock.data.DockSettings
import com.vishnu.lofidock.data.PrefKeys
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/** Night look: multiplied toward deep amber-red and darkened, like StandBy's night mode. */
private val NightTint = Color(1f, 0.45f, 0.35f)
private const val NIGHT_DARKENING = 0.45f

/**
 * One face with burn-in drift and night dimming applied. The whole face is
 * scaled 3% so drifting never uncovers an edge; the night look multiplies the
 * rendered layer, so it is done in the view rather than by changing the
 * screen's brightness setting.
 */
@Composable
fun DockFrame(now: Long, scale: Float, burnInShift: Boolean, night: Boolean, content: @Composable () -> Unit) {
    val drift = if (burnInShift) BurnInDrift.offset(now, 6f * scale) else BurnInDrift.Offset(0f, 0f)
    val dx by animateFloatAsState(drift.x, tween(3000), label = "driftX")
    val dy by animateFloatAsState(drift.y, tween(3000), label = "driftY")
    val nightAmount by animateFloatAsState(if (night) 1f else 0f, tween(2000), label = "night")
    Box(
        Modifier.fillMaxSize().clipToBounds()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                if (nightAmount > 0f) {
                    drawRect(lerp(Color.White, NightTint, nightAmount), blendMode = BlendMode.Multiply)
                    drawRect(Color.Black.copy(alpha = NIGHT_DARKENING * nightAmount))
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val s = if (burnInShift) 1.03f else 1f
                scaleX = s
                scaleY = s
                translationX = dx.dp.toPx()
                translationY = dy.dp.toPx()
            },
        ) { content() }
    }
}

/** App root: theme, keep-awake, frame budget and the environment every face reads. */
@Composable
fun LofiDockRoot(graph: AppGraph) {
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val charging by graph.charging.collectAsStateWithLifecycle()
    val heat by graph.heat.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Overrides the phone's screen timeout while the app is in front.
    val keepOn = ScreenAwake.shouldStayOn(settings.awakeOnlyWhileCharging, charging)
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val fps = AnimationBudget.framesPerSecond(heat.thermal, heat.powerSave, settings.adaptiveMotion)
    val datePattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd") }
    MaterialTheme(colorScheme = darkColorScheme(primary = Palette.lamp, surface = hex(0x1C1622), background = Palette.wallTop)) {
        CompositionLocalProvider(
            LocalFramesPerSecond provides fps,
            LocalShowsRain provides settings.rainVisual,
            LocalUse24Hour provides DateFormat.is24HourFormat(context),
            LocalDatePattern provides datePattern,
        ) {
            DockScreen(graph, settings, charging)
        }
    }
}

/** The live dock: swipe sideways to change face, tap to show the controls. */
@Composable
fun DockScreen(graph: AppGraph, settings: DockSettings, isCharging: Boolean) {
    val now by rememberWallClock()
    val startedAt by graph.session.startedAt.collectAsStateWithLifecycle()
    val timer by graph.timers.timer.collectAsStateWithLifecycle()
    val checklist by graph.checklist.checklist.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(false) }
    var interactions by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var direction by remember { mutableIntStateOf(1) }
    val currentFace by rememberUpdatedState(settings.face)

    // Hides the controls 5 s after the last interaction, so the dock goes back to a clean clock.
    LaunchedEffect(controlsVisible, interactions) {
        if (controlsVisible) {
            delay(5_000)
            controlsVisible = false
        }
    }

    fun touched() { interactions++ }
    fun showFace(target: ClockFace, step: Int) {
        direction = if (step >= 0) 1 else -1
        graph.settings.setFace(target)
        touched()
    }
    fun switchFace(step: Int) = showFace(currentFace.cycled(step), step)

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun startTimer() {
        // The first Start asks for notification permission, as on iOS.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        graph.timers.start(settings.timerLengthSeconds, settings.muted)
    }

    val sessionSeconds = ((now - startedAt).coerceAtLeast(0)) / 1000.0
    val night = settings.nightDimming && NightSchedule(settings.nightStart, settings.nightEnd).contains(now)

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    controlsVisible = !controlsVisible
                    touched()
                })
            }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (abs(total) > 60.dp.toPx()) switchFace(if (total < 0) 1 else -1) },
                    onHorizontalDrag = { _, amount -> total += amount },
                )
            },
    ) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val scale = DockMetrics.scale(widthDp, heightDp)

        AnimatedContent(
            targetState = settings.face,
            transitionSpec = {
                slideInHorizontally(tween(450)) { direction * it } togetherWith slideOutHorizontally(tween(450)) { -direction * it }
            },
            label = "face",
        ) { face ->
            DockFrame(now, scale, settings.burnInShift, night) {
                when (face) {
                    ClockFace.ROOM -> LofiRoomFace(now, sessionSeconds, widthDp, heightDp, recordSpinning = settings.vinyl && !settings.muted)
                    ClockFace.DIGITS -> BigDigitsFace(now, sessionSeconds, scale)
                    ClockFace.ANALOG -> AnalogFace(now, sessionSeconds, widthDp, heightDp, scale)
                    ClockFace.FLIP -> FlipFace(now, sessionSeconds, scale)
                    ClockFace.TIMER -> TimerFace(
                        now, sessionSeconds, scale, timer,
                        pickedLengthSeconds = settings.timerLengthSeconds,
                        onPickLength = { graph.settings.set(PrefKeys.TIMER_LENGTH, it) },
                        onStart = ::startTimer,
                        onPause = { graph.timers.pause() },
                        onResume = { graph.timers.resume(settings.muted) },
                        onCancel = { graph.timers.cancel() },
                    )
                    ClockFace.CHECKLIST -> ChecklistFace(
                        now, sessionSeconds, widthDp, scale, checklist,
                        onAdd = graph.checklist::add,
                        onToggle = graph.checklist::toggle,
                        onRemove = graph.checklist::remove,
                        onClearDone = graph.checklist::clearDone,
                    )
                }
            }
        }

        if (settings.face != ClockFace.TIMER && !timer.isIdle) {
            TimerChip(
                timer, now,
                onClick = { if (timer.isFinished) graph.timers.cancel() else showFace(ClockFace.TIMER, +1) },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = (72 * scale).dp)
                    .drawWithContent {
                        drawContent()
                        if (night) drawRect(NightTint, blendMode = BlendMode.Multiply)
                    },
            )
        }

        AnimatedVisibility(
            controlsVisible,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            ControlsBar(
                face = settings.face,
                muted = settings.muted,
                rainOn = settings.rain,
                vinylOn = settings.vinyl,
                isCharging = isCharging,
                onNextFace = { switchFace(1) },
                onMute = { graph.settings.set(PrefKeys.MUTED, !settings.muted); touched() },
                onRain = { toggleSound(graph, settings, PrefKeys.RAIN, settings.rain); touched() },
                onVinyl = { toggleSound(graph, settings, PrefKeys.VINYL, settings.vinyl); touched() },
                onSettings = { showSettings = true; touched() },
            )
        }
    }

    if (showSettings) SettingsDialog(settings, graph.settings) { showSettings = false }
}

/** While muted, tapping a sound unmutes and turns that sound on: the tap does what the icon suggests. */
private fun toggleSound(graph: AppGraph, settings: DockSettings, key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, isOn: Boolean) {
    if (settings.muted) {
        graph.settings.set(PrefKeys.MUTED, false)
        graph.settings.set(key, true)
    } else {
        graph.settings.set(key, !isOn)
    }
}

/** What the screen saver shows: the chosen clock face, without controls or sound. */
@Composable
fun DreamDock(graph: AppGraph) {
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val now by rememberWallClock()
    val session = remember { com.vishnu.lofidock.core.SessionClock() }
    val context = LocalContext.current
    val datePattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd") }
    CompositionLocalProvider(
        LocalShowsRain provides settings.rainVisual,
        LocalUse24Hour provides DateFormat.is24HourFormat(context),
        LocalDatePattern provides datePattern,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val widthDp = maxWidth.value
            val heightDp = maxHeight.value
            val scale = DockMetrics.scale(widthDp, heightDp)
            val sessionSeconds = session.elapsedSeconds(now)
            val night = settings.nightDimming && NightSchedule(settings.nightStart, settings.nightEnd).contains(now)
            DockFrame(now, scale, settings.burnInShift, night) {
                when (settings.face) {
                    ClockFace.DIGITS -> BigDigitsFace(now, sessionSeconds, scale)
                    ClockFace.ANALOG -> AnalogFace(now, sessionSeconds, widthDp, heightDp, scale)
                    ClockFace.FLIP -> FlipFace(now, sessionSeconds, scale)
                    // Timer and Checklist need touch; the screen saver shows the room instead.
                    else -> LofiRoomFace(now, sessionSeconds, widthDp, heightDp, recordSpinning = false)
                }
            }
        }
    }
} 
