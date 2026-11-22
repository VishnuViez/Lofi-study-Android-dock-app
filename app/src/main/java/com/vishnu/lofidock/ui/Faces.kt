package com.vishnu.lofidock.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.vishnu.lofidock.core.AnalogHands
import com.vishnu.lofidock.core.TimeFormatting
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Cream text with the lamp's warm glow; tabular figures so digits don't jiggle. */
fun lofiText(
    size: TextUnit,
    weight: FontWeight,
    alpha: Float = 1f,
    color: Color = Palette.cream,
    glow: Color = Palette.lamp.copy(alpha = 0.28f),
    glowRadius: Float = 16f,
    tracking: TextUnit = TextUnit.Unspecified,
) = TextStyle(
    color = color.copy(alpha = color.alpha * alpha),
    fontSize = size,
    fontWeight = weight,
    fontFamily = FontFamily.SansSerif,
    fontFeatureSettings = "tnum",
    letterSpacing = tracking,
    shadow = Shadow(glow, Offset.Zero, glowRadius),
)

@Composable
fun clockOf(epochMillis: Long): TimeFormatting.Clock = TimeFormatting.clock(epochMillis, LocalUse24Hour.current)

@Composable
fun dateLineOf(epochMillis: Long): String = TimeFormatting.dateLine(epochMillis, pattern = LocalDatePattern.current)

/** The live haze/rain/grain backdrop behind the clock-only faces. */
@Composable
fun LofiBackdrop(modifier: Modifier = Modifier.fillMaxSize()) {
    val scene by rememberSceneTime()
    val rain = LocalShowsRain.current
    val u = LocalDensity.current.density
    Canvas(modifier) { Backdrop.draw(this, scene.seconds, rain, u) }
}

/** The study-room scene with the clock on the wall beside the window. */
@Composable
fun LofiRoomFace(now: Long, sessionSeconds: Double, widthDp: Float, heightDp: Float, recordSpinning: Boolean) {
    val scene by rememberSceneTime()
    val rain = LocalShowsRain.current
    val u = LocalDensity.current.density
    val dims = rememberDims(DockMetrics.scale(widthDp, heightDp))
    val panel = DockMetrics.clockPanel(widthDp, heightDp)
    Box(Modifier.fillMaxSize()) {
        // The scene state is read inside the draw lambda, so new frames only redraw, never recompose.
        Canvas(Modifier.fillMaxSize()) { LofiScene(scene.seconds, recordSpinning, rain, u).draw(this) }
        ClockOverlay(
            now, sessionSeconds, dims,
            Modifier.offset(panel.left.dp, panel.top.dp).size(panel.width.dp, panel.height.dp),
        )
    }
}

@Composable
fun ClockOverlay(now: Long, sessionSeconds: Double, d: Dims, modifier: Modifier = Modifier) {
    val clock = clockOf(now)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row {
            Text(clock.time, Modifier.alignByBaseline(), style = lofiText(d.sp(94f), FontWeight.Light), maxLines = 1)
            clock.period?.let {
                Spacer(Modifier.width(d.dp(6f)))
                Text(it, Modifier.alignByBaseline(), style = lofiText(d.sp(24f), FontWeight.Medium, alpha = 0.7f))
            }
        }
        Text(dateLineOf(now), style = lofiText(d.sp(16f), FontWeight.Medium, alpha = 0.65f))
        Box(
            Modifier.padding(vertical = d.dp(12f)).size(d.dp(36f), d.dp(1.5f))
                .background(Palette.cream.copy(alpha = 0.22f), CircleShape),
        )
        Row {
            Text(
                "SESSION", Modifier.alignByBaseline(),
                style = lofiText(d.sp(11f), FontWeight.SemiBold, alpha = 0.55f, tracking = d.sp(2.5f)),
            )
            Spacer(Modifier.width(d.dp(10f)))
            Text(TimeFormatting.session(sessionSeconds), Modifier.alignByBaseline(), style = lofiText(d.sp(26f), FontWeight.Normal))
        }
    }
}

/** "Saturday, November 21 · SESSION 0:42:13", optionally led by the time. */
@Composable
fun SessionLine(now: Long, sessionSeconds: Double, d: Dims, showsTime: Boolean = false) {
    val base = lofiText(d.sp(18f), FontWeight.Medium, alpha = 0.8f)
    Row(horizontalArrangement = Arrangement.spacedBy(d.dp(12f))) {
        if (showsTime) {
            Text(clockOf(now).joined, Modifier.alignByBaseline(), style = base)
            Text("·", Modifier.alignByBaseline(), style = base.copy(color = base.color.copy(alpha = 0.4f)))
        }
        Text(dateLineOf(now), Modifier.alignByBaseline(), style = base)
        Text("·", Modifier.alignByBaseline(), style = base.copy(color = base.color.copy(alpha = 0.4f)))
        Text(
            "SESSION", Modifier.alignByBaseline(),
            style = lofiText(d.sp(12f), FontWeight.SemiBold, alpha = 0.5f, tracking = d.sp(2.5f)),
        )
        Text(TimeFormatting.session(sessionSeconds), Modifier.alignByBaseline(), style = base)
    }
}

/** One huge, thin clock, StandBy style, over the lofi backdrop. */
@Composable
fun BigDigitsFace(now: Long, sessionSeconds: Double, scale: Float) {
    val d = rememberDims(scale)
    val clock = clockOf(now)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LofiBackdrop()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text(clock.time, Modifier.alignByBaseline(), style = lofiText(d.sp(200f), FontWeight.Thin, glowRadius = 24f), maxLines = 1)
                clock.period?.let {
                    Spacer(Modifier.width(d.dp(10f)))
                    Text(it, Modifier.alignByBaseline(), style = lofiText(d.sp(34f), FontWeight.Medium, alpha = 0.6f))
                }
            }
            SessionLine(now, sessionSeconds, d)
        }
    }
}

/** A paper-dial wall clock with a sweeping second hand; time and session beside it. */
@Composable
fun AnalogFace(now: Long, sessionSeconds: Double, widthDp: Float, heightDp: Float, scale: Float) {
    val d = rememberDims(scale)
    val scene by rememberSceneTime()
    val measurer = rememberTextMeasurer()
    val clock = clockOf(now)
    Box(Modifier.fillMaxSize()) {
        LofiBackdrop()
        Canvas(Modifier.fillMaxSize()) {
            AnalogDial.draw(this, Offset(size.width * 0.36f, size.height * 0.5f), size.height * 0.4f, AnalogHands.at(scene.epochMillis), measurer)
        }
        Column(
            Modifier.offset((widthDp * 0.60f).dp, 0.dp).width((widthDp * 0.26f).dp).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Row {
                Text(clock.time, Modifier.alignByBaseline(), style = lofiText(d.sp(44f), FontWeight.Light, glowRadius = 14f))
                clock.period?.let {
                    Spacer(Modifier.width(d.dp(5f)))
                    Text(it, Modifier.alignByBaseline(), style = lofiText(d.sp(16f), FontWeight.Medium, alpha = 0.6f))
                }
            }
            Text(dateLineOf(now), style = lofiText(d.sp(15f), FontWeight.Medium, alpha = 0.65f))
            Box(
                Modifier.padding(vertical = d.dp(10f)).size(d.dp(30f), d.dp(1.5f))
                    .background(Palette.cream.copy(alpha = 0.22f), CircleShape),
            )
            Text("SESSION", style = lofiText(d.sp(11f), FontWeight.SemiBold, alpha = 0.55f, tracking = d.sp(2.5f)))
            Text(TimeFormatting.session(sessionSeconds), style = lofiText(d.sp(24f), FontWeight.Normal))
        }
    }
}

/** The analog dial and hands; a port of the iOS `AnalogDial`. */
object AnalogDial {
    fun draw(scope: DrawScope, c: Offset, r: Float, hands: AnalogHands, measurer: TextMeasurer) = with(scope) {
        fun point(degrees: Double, distance: Float): Offset {
            val a = Math.toRadians(degrees)
            return Offset(c.x + distance * sin(a).toFloat(), c.y - distance * cos(a).toFloat())
        }
        val ink = hex(0x33262A)

        // Shadow and wooden rim.
        val shadowCenter = Offset(c.x, c.y + r * 0.05f)
        drawCircle(
            Brush.radialGradient(0f to Color.Black.copy(alpha = 0.5f), 0.78f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent,
                center = shadowCenter, radius = r * 1.15f),
            r * 1.15f, shadowCenter,
        )
        drawCircle(Brush.verticalGradient(listOf(hex(0x6A4A3C), hex(0x2E201E)), startY = c.y - r, endY = c.y + r), r, c)

        // Paper dial, warmer toward the lamp side.
        val face = r * 0.92f
        drawCircle(
            Brush.radialGradient(listOf(hex(0xF5EBD8), hex(0xDCCAAA)), center = Offset(c.x - face * 0.3f, c.y - face * 0.35f), radius = face * 1.5f),
            face, c,
        )
        val minor = Path()
        val major = Path()
        for (i in 0 until 60) {
            val degrees = i * 6.0
            val (path, inner) = if (i % 5 == 0) major to 0.80f else minor to 0.88f
            val from = point(degrees, face * inner)
            val to = point(degrees, face * 0.93f)
            path.moveTo(from.x, from.y)
            path.lineTo(to.x, to.y)
        }
        drawPath(minor, ink.copy(alpha = 0.35f), style = Stroke(max(1f, r * 0.008f)))
        drawPath(major, ink.copy(alpha = 0.85f), style = Stroke(r * 0.024f, cap = StrokeCap.Round))

        val numeralStyle = TextStyle(color = ink, fontSize = (r * 0.17f).toSp(), fontWeight = FontWeight.Medium)
        for ((numeral, degrees) in listOf("12" to 0.0, "3" to 90.0, "6" to 180.0, "9" to 270.0)) {
            val layout = measurer.measure(numeral, numeralStyle)
            val p = point(degrees, face * 0.63f)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        val brand = measurer.measure(
            "lofi",
            TextStyle(color = ink.copy(alpha = 0.45f), fontSize = (r * 0.08f).toSp(), fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic),
        )
        drawText(brand, topLeft = Offset(c.x - brand.size.width / 2f, c.y + face * 0.32f - brand.size.height / 2f))

        fun hand(degrees: Double, back: Float, length: Float, width: Float, color: Color) {
            drawLine(color, point(degrees + 180, back), point(degrees, length), strokeWidth = width, cap = StrokeCap.Round)
        }
        hand(hands.hour, face * 0.08f, face * 0.5f, r * 0.055f, ink)
        hand(hands.minute, face * 0.08f, face * 0.76f, r * 0.038f, ink)
        hand(hands.second, face * 0.2f, face * 0.84f, max(1f, r * 0.012f), hex(0xC9785A))
        drawCircle(hex(0xC9785A), r * 0.04f, c)
        drawCircle(hex(0xF5EBD8), r * 0.015f, c)

        // Glass glare.
        drawArc(
            Color.White.copy(alpha = 0.18f), startAngle = 200f, sweepAngle = 65f, useCenter = false,
            topLeft = Offset(c.x - face * 0.86f, c.y - face * 0.86f), size = Size(face * 1.72f, face * 1.72f),
            style = Stroke(r * 0.03f, cap = StrokeCap.Round),
        )
    }
}

/** Retro split-flap cards for hours and minutes. */
@Composable
fun FlipFace(now: Long, sessionSeconds: Double, scale: Float) {
    val d = rememberDims(scale)
    val clock = clockOf(now)
    val parts = clock.time.split(":")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LofiBackdrop()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d.dp(24f))) {
            Row(horizontalArrangement = Arrangement.spacedBy(d.dp(18f))) {
                FlipCard(parts.first(), clock.period, d)
                FlipCard(parts.last(), null, d)
            }
            SessionLine(now, sessionSeconds, d)
        }
    }
}

@Composable
fun FlipCard(text: String, corner: String?, d: Dims) {
    val shape = RoundedCornerShape(d.dp(22f))
    Box(
        Modifier.size(d.dp(250f), d.dp(230f))
            .shadow(d.dp(18f), shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Brush.verticalGradient(0f to hex(0x372A31), 0.5f to hex(0x2A2027), 0.5f to hex(0x221A20), 1f to hex(0x221A20)), shape)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape),
        contentAlignment = Alignment.Center,
    ) {
        // Digits roll over like a flap falling when the minute changes.
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInVertically(tween(500)) { -it / 2 } + fadeIn(tween(500))) togetherWith
                    (slideOutVertically(tween(500)) { it / 2 } + fadeOut(tween(500)))
            },
            label = "flip",
        ) { value ->
            Text(value, style = lofiText(d.sp(170f), FontWeight.SemiBold, glow = Color.Transparent, glowRadius = 0f))
        }
        Box(Modifier.fillMaxWidth().height(d.dp(3f)).background(Color.Black.copy(alpha = 0.6f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(2) {
                Box(Modifier.offset(x = if (it == 0) (-2).dp else 2.dp).size(d.dp(6f), d.dp(20f)).background(hex(0x120D14), CircleShape))
            }
        }
        corner?.let {
            Text(
                it,
                Modifier.align(Alignment.TopStart).padding(d.dp(16f)),
                style = lofiText(d.sp(15f), FontWeight.Bold, alpha = 0.5f, glow = Color.Transparent, glowRadius = 0f),
            )
        }
    }
}
