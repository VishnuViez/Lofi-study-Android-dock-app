package com.vishnu.lofidock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishnu.lofidock.core.AnimationBudget
import com.vishnu.lofidock.core.SceneRandom
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object Palette {
    val cream = Color(0xFFF6E7D0)
    val lamp = Color(0xFFFFB45E)
    val wallTop = Color(0xFF17122B)
    val wallBottom = Color(0xFF2B1E3A)
    val terracotta = Color(0xFFFF9C80)
    val barBackground = Color(0xD115101C)
}

/** A colour from 0xRRGGBB plus alpha. */
fun hex(rgb: Long, alpha: Float = 1f): Color = Color(0xFF000000L or rgb).copy(alpha = alpha)

/** A colour fading to transparent from [from] (fraction of the radius) outward. */
fun glow(rgb: Long, alpha: Float, center: Offset, radius: Float, from: Float = 0f): Brush {
    val solid = hex(rgb, alpha)
    val clear = hex(rgb, 0f)
    val stops = if (from > 0f) arrayOf(0f to solid, from to solid, 1f to clear) else arrayOf(0f to solid, 1f to clear)
    return Brush.radialGradient(*stops, center = center, radius = max(radius, 0.01f))
}

fun circle(center: Offset, radius: Float) = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)

fun rnd(index: Int, salt: Int): Float = SceneRandom.unit(index, salt).toFloat()

fun fract(x: Double): Double = x - floor(x)

/**
 * Layout as fractions of the iPhone 15's 852 × 393 pt landscape screen, the
 * same numbers as the iOS app, so the design scales to any phone.
 */
object DockMetrics {
    fun scale(widthDp: Float, heightDp: Float): Float = min(widthDp / 852f, heightDp / 393f)

    /** The bare wall right of the window; the trailing margin clears a side camera cutout. */
    fun clockPanel(widthDp: Float, heightDp: Float): Rect {
        val minX = widthDp * 0.53f
        val maxX = widthDp - 64f * scale(widthDp, heightDp)
        return Rect(minX, heightDp * 0.1f, maxX, heightDp * 0.66f)
    }
}

/** Sizes for one face: dp and font sizes scaled from the iPhone design, ignoring font scale. */
class Dims(val scale: Float, private val fontScale: Float) {
    fun dp(v: Float): Dp = (v * scale).dp
    fun sp(v: Float): TextUnit = (v * scale / fontScale).sp
}

@Composable
fun rememberDims(scale: Float): Dims {
    val fontScale = LocalDensity.current.fontScale
    return remember(scale, fontScale) { Dims(scale, fontScale) }
}

/** A fixed point in time for rendering faces without live clocks (screenshots). */
data class FrozenMoment(val time: Double, val epochMillis: Long)

val LocalFrozenMoment = staticCompositionLocalOf<FrozenMoment?> { null }
val LocalFramesPerSecond = compositionLocalOf { AnimationBudget.FULL }
val LocalShowsRain = compositionLocalOf { true }
val LocalUse24Hour = compositionLocalOf { false }
val LocalDatePattern = compositionLocalOf { "EEEE, MMMM d" }

/** Animation clock: seconds since the face appeared and the current instant. */
data class SceneTime(val seconds: Double, val epochMillis: Long)

/**
 * Drives animated scenery from a coroutine: wait for a frame, publish the
 * time, then sleep off the rest of the frame budget so the scene redraws at
 * [LocalFramesPerSecond] instead of the display's 60-120 Hz.
 */
@Composable
fun rememberSceneTime(): State<SceneTime> {
    val frozen = LocalFrozenMoment.current
    if (frozen != null) return remember(frozen) { mutableStateOf(SceneTime(frozen.time, frozen.epochMillis)) }
    val fps = LocalFramesPerSecond.current
    val origin = remember { System.nanoTime() }
    return produceState(SceneTime(0.0, System.currentTimeMillis()), fps) {
        val budgetMillis = (1000f / fps).toLong()
        while (true) {
            val frameNanos = withFrameNanos { it }
            value = SceneTime((frameNanos - origin) / 1e9, System.currentTimeMillis())
            delay((budgetMillis - 8).coerceAtLeast(0))   // the next withFrameNanos adds up to a vsync
        }
    }
}

/** Wall clock that ticks on each whole second, from a coroutine. */
@Composable
fun rememberWallClock(): State<Long> {
    val frozen = LocalFrozenMoment.current
    if (frozen != null) return remember(frozen) { mutableLongStateOf(frozen.epochMillis) }
    return produceState(System.currentTimeMillis()) {
        while (true) {
            val now = System.currentTimeMillis()
            value = now
            delay(1000 - now % 1000)
        }
    }
}
