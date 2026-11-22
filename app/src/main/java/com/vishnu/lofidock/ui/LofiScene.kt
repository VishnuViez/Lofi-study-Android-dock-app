package com.vishnu.lofidock.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.vishnu.lofidock.core.SceneRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val LAMP_TILT = -34f

private fun rotated(p: Offset, degrees: Float): Offset {
    val r = Math.toRadians(degrees.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    return Offset(p.x * c - p.y * s, p.x * s + p.y * c)
}

/**
 * The animated study room, drawn entirely in code: a port of the iOS
 * `LofiScene`, same geometry and colours. Every element is a pure function of
 * the canvas size and [t] (seconds), so any frame can be reproduced. [u] is
 * pixels per dp, for the design's few absolute (point) sizes.
 */
class LofiScene(
    private val t: Double,
    private val recordSpinning: Boolean,
    private val rain: Boolean,
    private val u: Float,
) {
    fun draw(scope: DrawScope) = with(scope) {
        val g = RoomGeometry(size, u)
        drawWall(g)
        NightView.draw(this, g.glass, t, rain, lampReflection = true)
        drawWindowFrame(g)
        drawDesk(g)
        drawLampLight(g)
        drawBooks(g)
        drawMug(g)
        drawPlant(g)
        drawTurntable(g)
        drawLamp(g)
        drawSteam(g)
        drawFairyLights(g)
        drawVignette(g)
    }

    private class RoomGeometry(size: Size, val u: Float) {
        val w = size.width
        val h = size.height
        val window = Rect(w * 0.06f, h * 0.08f, w * 0.50f, h * 0.70f)
        val frameWidth = max(4f * u, h * 0.022f)
        val glass = window.deflate(frameWidth)
        val deskTop = h * 0.80f
        val deskTopHeight = h * 0.035f
        val objectBase = deskTop + deskTopHeight * 0.6f
        val lampBase = Offset(w * 0.11f, objectBase)
        val lampElbow = Offset(w * 0.082f, h * 0.585f)
        val lampPivot = Offset(w * 0.15f, h * 0.445f)
        val shadeLength = h * 0.085f
        val shadeMouth = h * 0.115f
        val lampMouth = lampPivot + rotated(Offset(0f, shadeLength), LAMP_TILT)
        val mug: Rect = run {
            val mw = h * 0.068f
            val mh = h * 0.082f
            Rect(w * 0.365f - mw / 2, objectBase - mh, w * 0.365f + mw / 2, objectBase)
        }
    }

    private fun DrawScope.drawWall(g: RoomGeometry) {
        drawRect(Brush.verticalGradient(listOf(Palette.wallTop, Palette.wallBottom)))
        drawRect(glow(0xFFB45E, 0.20f, g.lampMouth, g.w * 0.55f))
    }

    private fun DrawScope.drawWindowFrame(g: RoomGeometry) {
        val wood = Brush.horizontalGradient(listOf(hex(0x3C2B42), hex(0x231A2E)), startX = g.window.left, endX = g.window.right)
        val frame = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(g.window)
            addRect(g.glass)
        }
        drawPath(frame, wood)
        val bar = g.frameWidth * 0.55f
        val glass = g.glass
        val crossY = glass.top + glass.height * 0.42f
        val bars = Path().apply {
            addRect(Rect(glass.center.x - bar / 2, glass.top, glass.center.x + bar / 2, glass.bottom))
            addRect(Rect(glass.left, crossY - bar / 2, glass.right, crossY + bar / 2))
        }
        drawPath(bars, wood)
        drawRect(Color.Black.copy(alpha = 0.35f), glass.topLeft, glass.size, style = Stroke(1f * u))
        drawRect(Palette.lamp.copy(alpha = 0.10f), g.window.topLeft, g.window.size, style = Stroke(1f * u))

        val sill = Rect(Offset(g.window.left - g.w * 0.012f, g.window.bottom - 1f * u), Size(g.window.width + g.w * 0.024f, g.h * 0.028f))
        drawRect(Color.Black.copy(alpha = 0.22f), Offset(sill.left + 4 * u, sill.bottom), Size(sill.width - 8 * u, g.h * 0.014f))
        drawRoundRect(hex(0x3F2E3E), sill.topLeft, sill.size, CornerRadius(2 * u))
        drawRect(Palette.lamp.copy(alpha = 0.2f), sill.topLeft, Size(sill.width, 1.5f * u))
    }

    private fun DrawScope.drawDesk(g: RoomGeometry) {
        val topBottom = g.deskTop + g.deskTopHeight
        drawRect(
            Brush.verticalGradient(listOf(hex(0x5B4037), hex(0x47312C)), startY = g.deskTop, endY = topBottom),
            Offset(0f, g.deskTop), Size(g.w, g.deskTopHeight),
        )
        drawRect(
            Brush.verticalGradient(listOf(hex(0x2E201E), hex(0x181012)), startY = topBottom, endY = g.h),
            Offset(0f, topBottom), Size(g.w, g.h - topBottom),
        )
        drawRect(Palette.lamp.copy(alpha = 0.22f), Offset(0f, g.deskTop), Size(g.w, max(1f * u, g.h * 0.004f)))
        drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, topBottom), Size(g.w, 1f * u))
    }

    private fun DrawScope.drawLampLight(g: RoomGeometry) {
        val half = g.shadeMouth / 2
        val a = g.lampPivot + rotated(Offset(-half, g.shadeLength), LAMP_TILT)
        val b = g.lampPivot + rotated(Offset(half, g.shadeLength), LAMP_TILT)
        val floor = g.deskTop + g.deskTopHeight
        val cone = Path().apply {
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(g.w * 0.40f, floor)
            lineTo(g.w * 0.12f, floor)
            close()
        }
        drawPath(
            cone,
            Brush.linearGradient(listOf(hex(0xFFC27A, 0.14f), hex(0xFFC27A, 0f)), start = g.lampMouth, end = Offset(g.lampMouth.x + g.w * 0.04f, g.deskTop)),
            blendMode = BlendMode.Screen,
        )
        // Pool of light on the desk: a circle squashed flat.
        withTransform({
            translate(g.w * 0.25f, g.deskTop + g.deskTopHeight * 0.5f)
            scale(1f, 0.16f, pivot = Offset.Zero)
        }) {
            val r = g.w * 0.2f
            drawCircle(glow(0xFFB45E, 0.45f, Offset.Zero, r), r, Offset.Zero, blendMode = BlendMode.Screen)
        }
    }

    private fun DrawScope.drawLamp(g: RoomGeometry) {
        drawOval(hex(0x27403F), Offset(g.lampBase.x - g.w * 0.03f, g.lampBase.y - g.h * 0.018f), Size(g.w * 0.06f, g.h * 0.026f))
        val arm = Path().apply {
            moveTo(g.lampBase.x, g.lampBase.y - g.h * 0.01f)
            lineTo(g.lampElbow.x, g.lampElbow.y)
            lineTo(g.lampPivot.x, g.lampPivot.y)
        }
        drawPath(arm, hex(0x35585C), style = Stroke(max(2f * u, g.h * 0.011f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (joint in listOf(g.lampElbow, g.lampPivot)) drawCircle(hex(0x4D7A7C), g.h * 0.011f, joint)

        // The shade is drawn pointing straight down in its own rotated space.
        withTransform({
            translate(g.lampPivot.x, g.lampPivot.y)
            rotate(LAMP_TILT, pivot = Offset.Zero)
        }) {
            val half = g.shadeMouth / 2
            val neck = g.shadeMouth * 0.16f
            val cap = -g.h * 0.012f
            val length = g.shadeLength
            val body = Path().apply {
                moveTo(-neck, cap)
                lineTo(neck, cap)
                quadraticTo(half * 0.35f, length * 0.45f, half, length)
                lineTo(-half, length)
                quadraticTo(-half * 0.35f, length * 0.45f, -neck, cap)
                close()
            }
            drawPath(body, Brush.horizontalGradient(listOf(hex(0x5C8C8C), hex(0x2C4747)), startX = -half, endX = half))
            drawOval(hex(0xFFE6B8), Offset(-half, length - g.shadeMouth * 0.12f), Size(g.shadeMouth, g.shadeMouth * 0.24f))
            val mouth = Offset(0f, length)
            drawCircle(glow(0xFFD08A, 0.55f, mouth, g.shadeMouth * 1.3f), g.shadeMouth * 1.3f, mouth, blendMode = BlendMode.Screen)
        }
    }

    private fun DrawScope.drawBooks(g: RoomGeometry) {
        val books = listOf(Triple(0.115f, 0x7E4545L, 0f), Triple(0.104f, 0x3C6464L, 0.006f), Triple(0.094f, 0xB0873EL, -0.004f))
        val thickness = g.h * 0.03f
        var y = g.objectBase
        for ((width, color, shift) in books) {
            val rect = Rect(Offset(g.w * (0.27f + shift - width / 2), y - thickness), Size(g.w * width, thickness))
            drawRoundRect(hex(color), rect.topLeft, rect.size, CornerRadius(1.5f * u))
            drawRect(Palette.cream.copy(alpha = 0.8f), Offset(rect.right - g.w * 0.012f, rect.top + thickness * 0.18f), Size(g.w * 0.009f, thickness * 0.64f))
            drawRect(Color.Black.copy(alpha = 0.2f), Offset(rect.left + g.w * 0.014f, rect.top), Size(g.w * 0.005f, thickness))
            drawRect(Palette.lamp.copy(alpha = 0.3f), rect.topLeft, Size(rect.width, 1f * u))
            y -= thickness
        }
    }

    private fun DrawScope.drawMug(g: RoomGeometry) {
        val r = g.mug
        val handle = Rect(Offset(r.right - r.width * 0.18f, r.top + r.height * 0.22f), Size(r.width * 0.45f, r.height * 0.46f))
        drawRoundRect(hex(0xC9B79F), handle.topLeft, handle.size, CornerRadius(r.width * 0.2f), style = Stroke(r.width * 0.12f))
        drawRoundRect(
            Brush.horizontalGradient(listOf(hex(0xEEDFCA), hex(0xB9A790)), startX = r.left, endX = r.right),
            r.topLeft, r.size, CornerRadius(r.width * 0.14f),
        )
        drawRect(hex(0xC9785A), Offset(r.left, r.top + r.height * 0.36f), Size(r.width, r.height * 0.12f))
        val rim = Rect(Offset(r.left, r.top - r.width * 0.08f), Size(r.width, r.width * 0.16f))
        drawOval(hex(0xDCCBB3), rim.topLeft, rim.size)
        val coffee = Rect(rim.left + r.width * 0.08f, rim.top + r.width * 0.025f, rim.right - r.width * 0.08f, rim.bottom - r.width * 0.025f)
        drawOval(hex(0x3A2418), coffee.topLeft, coffee.size)
    }

    private fun DrawScope.drawPlant(g: RoomGeometry) {
        val cx = g.w * 0.455f
        val potHeight = g.h * 0.075f
        val potTop = g.objectBase - potHeight
        val topWidth = g.h * 0.088f
        val bottomWidth = topWidth * 0.74f
        val stem = Offset(cx, potTop + potHeight * 0.1f)
        for (i in 0 until 7) {
            val spread = -62.0 + i * (124.0 / 6)
            val sway = sin(t * 0.55 + i * 1.37) * 2.5
            val length = g.h * (0.09f + 0.06f * rnd(i, 71)) * (1.15f - (abs(spread) / 180).toFloat())
            withTransform({
                translate(stem.x, stem.y)
                rotate((spread + sway).toFloat(), pivot = Offset.Zero)
            }) {
                val leaf = Path().apply {
                    moveTo(0f, 0f)
                    quadraticTo(length * 0.34f, -length * 0.5f, 0f, -length)
                    quadraticTo(-length * 0.34f, -length * 0.5f, 0f, 0f)
                    close()
                }
                drawPath(leaf, hex(if (i % 2 == 0) 0x3E6C4F else 0x55885F))
                drawLine(Color.Black.copy(alpha = 0.18f), Offset.Zero, Offset(0f, -length * 0.88f), strokeWidth = 0.8f * u)
            }
        }
        val pot = Path().apply {
            moveTo(cx - topWidth / 2, potTop)
            lineTo(cx + topWidth / 2, potTop)
            lineTo(cx + bottomWidth / 2, g.objectBase)
            lineTo(cx - bottomWidth / 2, g.objectBase)
            close()
        }
        drawPath(pot, Brush.horizontalGradient(listOf(hex(0xC46E52), hex(0x8E4A37)), startX = cx - topWidth / 2, endX = cx + topWidth / 2))
        drawRoundRect(hex(0xD27C5E), Offset(cx - topWidth * 0.54f, potTop - potHeight * 0.06f), Size(topWidth * 1.08f, potHeight * 0.22f), CornerRadius(1.5f * u))
    }

    private fun DrawScope.drawTurntable(g: RoomGeometry) {
        val box = Rect(Offset(g.w * 0.615f, g.objectBase - g.h * 0.058f), Size(g.w * 0.24f, g.h * 0.058f))
        val deck = Rect(Offset(box.left + g.w * 0.006f, box.top - g.h * 0.05f), Size(box.width - g.w * 0.012f, g.h * 0.05f))
        drawRoundRect(hex(0x3A2A2C), deck.topLeft, deck.size, CornerRadius(3 * u))
        drawRoundRect(Brush.verticalGradient(listOf(hex(0x6E4C38), hex(0x4A3124)), startY = box.top, endY = box.bottom), box.topLeft, box.size, CornerRadius(3 * u))
        drawRect(Palette.lamp.copy(alpha = 0.18f), box.topLeft, Size(box.width, 1f * u))

        val led = Offset(box.left + box.width * 0.07f, box.center.y)
        drawCircle(glow(0xFFB45E, if (recordSpinning) 0.5f else 0f, led, g.h * 0.016f), g.h * 0.016f, led)
        drawCircle(hex(if (recordSpinning) 0xFFC27A else 0x5A3A2A), g.h * 0.005f, led)
        for (k in 0 until 2) drawCircle(hex(0x2A1D19), g.h * 0.011f, Offset(box.right - box.width * (0.08f + 0.09f * k), box.center.y))

        val c = Offset(deck.left + deck.width * 0.42f, deck.center.y)
        val rx = deck.width * 0.36f
        val ry = deck.height * 0.4f
        fun ring(f: Float) = Rect(c.x - rx * f, c.y - ry * f, c.x + rx * f, c.y + ry * f)
        drawOval(hex(0x111014), ring(1f).topLeft, ring(1f).size)
        for (f in listOf(0.85f, 0.7f, 0.55f)) drawOval(Color.White.copy(alpha = 0.06f), ring(f).topLeft, ring(f).size, style = Stroke(0.6f * u))
        // Two glints that sweep round at 33⅓ rpm while the record plays.
        val spin = if (recordSpinning) t * 2 * PI * (100.0 / 3.0) / 60 else 0.6
        for (start in listOf(spin, spin + PI)) {
            val glint = Path()
            for (s in 0..8) {
                val a = start + s * 0.09
                val p = Offset(c.x + rx * 0.72f * cos(a).toFloat(), c.y + ry * 0.72f * sin(a).toFloat())
                if (s == 0) glint.moveTo(p.x, p.y) else glint.lineTo(p.x, p.y)
            }
            drawPath(glint, Color.White.copy(alpha = 0.22f), style = Stroke(1.2f * u, cap = StrokeCap.Round))
        }
        drawOval(hex(0xC9785A), ring(0.3f).topLeft, ring(0.3f).size)
        drawCircle(hex(0xE8D9C4), 1.2f * u, c)

        // Tonearm: on the record while it plays, parked beside it otherwise.
        val pivot = Offset(deck.right - deck.width * 0.08f, deck.top + deck.height * 0.3f)
        val needle = if (recordSpinning) Offset(c.x + rx * 0.58f, c.y + ry * 0.1f)
        else Offset(deck.right - deck.width * 0.05f, deck.bottom - deck.height * 0.15f)
        val arm = Path().apply {
            moveTo(pivot.x, pivot.y)
            lineTo((pivot.x + needle.x) / 2 + 4 * u, min(pivot.y, needle.y) - ry * 0.1f)
            lineTo(needle.x, needle.y)
        }
        drawPath(arm, hex(0xC9C2B7), style = Stroke(max(1.2f * u, g.h * 0.005f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawRoundRect(hex(0xE0D9CE), Offset(needle.x - 3 * u, needle.y - 2 * u), Size(6 * u, 4 * u), CornerRadius(1 * u))
        drawCircle(hex(0x8A837A), g.h * 0.013f, pivot)
    }

    private fun DrawScope.drawSteam(g: RoomGeometry) {
        val origin = Offset(g.mug.center.x, g.mug.top - g.mug.width * 0.06f)
        val rise = g.h * 0.2f
        val top = Offset(origin.x, origin.y - rise)
        val halo = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0f)), start = origin, end = top)
        val core = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0f)), start = origin, end = top)
        for (i in 0 until 3) {
            val wisp = Path()
            for (j in 0..28) {
                val s = j / 28.0
                val amplitude = g.h * (0.005f + 0.02f * s.toFloat())
                // Phase moves with t, so the waves travel upward like rising steam.
                val sway = amplitude * sin(s * 5.5 - t * 1.1 + i * 2.2).toFloat()
                val p = Offset(origin.x + (i - 1) * g.h * 0.011f + sway, origin.y - s.toFloat() * rise)
                if (j == 0) wisp.moveTo(p.x, p.y) else wisp.lineTo(p.x, p.y)
            }
            drawPath(wisp, halo, style = Stroke(g.h * 0.02f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(wisp, core, style = Stroke(g.h * 0.006f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    private fun DrawScope.drawFairyLights(g: RoomGeometry) {
        val hooks = listOf(-0.02f to 0.03f, 0.27f to 0.036f, 0.56f to 0.03f, 0.83f to 0.036f, 1.02f to 0.03f)
            .map { Offset(g.w * it.first, g.h * it.second) }
        val sag = g.h * 0.11f   // quad control drop; the wire hangs half this at mid-span
        val wire = Path()
        val bulbs = ArrayList<Offset>(28)
        for ((a, b) in hooks.zipWithNext()) {
            val control = Offset((a.x + b.x) / 2, max(a.y, b.y) + sag)
            wire.moveTo(a.x, a.y)
            wire.quadraticTo(control.x, control.y, b.x, b.y)
            for (step in 1..7) {
                val s = step / 8f
                val q = 1 - s
                bulbs += Offset(q * q * a.x + 2 * q * s * control.x + s * s * b.x, q * q * a.y + 2 * q * s * control.y + s * s * b.y)
            }
        }
        drawPath(wire, hex(0x0C0912, 0.85f), style = Stroke(max(0.8f * u, g.h * 0.0025f)))
        val colors = listOf(0xFFC46BL, 0xFF9F7AL, 0xFFE2A0L, 0xFFB45EL)
        val r = g.h * 0.007f
        bulbs.forEachIndexed { i, p ->
            val wave = sin(t * (0.7 + 0.9 * SceneRandom.unit(i, 81)) + SceneRandom.unit(i, 82) * 2 * PI)
            val twinkle = (0.6 + 0.4 * (0.5 + 0.5 * wave)).toFloat()
            val color = colors[i % colors.size]
            val bulb = Offset(p.x, p.y + r)
            drawCircle(glow(color, 0.42f * twinkle, bulb, r * 5), r * 5, bulb, blendMode = BlendMode.Screen)
            drawCircle(hex(color, 0.7f + 0.3f * twinkle), r, bulb)
        }
    }

    private fun DrawScope.drawVignette(g: RoomGeometry) {
        val radius = g.w * 0.62f
        val clearUntil = (g.h * 0.5f / radius).coerceIn(0f, 1f)
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, clearUntil to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.42f),
                center = Offset(g.w * 0.5f, g.h * 0.45f), radius = radius,
            ),
        )
    }
}

/** The view through the window (sky, moon, city, rain); also used by the backdrop. */
object NightView {
    fun draw(scope: DrawScope, glass: Rect, t: Double, rain: Boolean, lampReflection: Boolean) = with(scope) {
        clipRect(glass.left, glass.top, glass.right, glass.bottom) {
            // Small details scale with the glass; 230 dp is its height on the reference phone.
            val k = glass.height / 230f
            drawRect(
                Brush.verticalGradient(
                    0f to hex(0x0A0E2A), 0.45f to hex(0x221C48), 0.8f to hex(0x4A3266), 1f to hex(0x7C4C6A),
                    startY = glass.top, endY = glass.bottom,
                ),
                glass.topLeft, glass.size,
            )
            stars(glass, t, k)
            moon(glass)
            clouds(glass, t)
            skyline(glass, t, k, layer = 0)
            skyline(glass, t, k, layer = 1)
            if (rain) {
                drawRain(glass, t, k)
                beads(glass, t, k)
            }
            sheen(glass, lampReflection)
        }
    }

    private fun DrawScope.stars(glass: Rect, t: Double, k: Float) {
        for (i in 0 until 46) {
            val x = glass.left + rnd(i, 11) * glass.width
            val y = glass.top + rnd(i, 12) * glass.height * 0.6f
            val r = (0.4f + rnd(i, 13) * 0.9f) * k
            val rate = 0.5 + 1.5 * SceneRandom.unit(i, 14)
            val twinkle = 0.35 + 0.65 * (0.5 + 0.5 * sin(t * rate + SceneRandom.unit(i, 15) * 2 * PI))
            drawCircle(hex(0xFFF3DA, (0.85 * twinkle).toFloat()), r, Offset(x, y))
        }
    }

    private fun DrawScope.moon(glass: Rect) {
        val c = Offset(glass.left + glass.width * 0.74f, glass.top + glass.height * 0.24f)
        val r = glass.height * 0.075f
        drawCircle(glow(0xF3E3C3, 0.26f, c, r * 5, from = 0.16f), r * 5, c)
        val disc = Path().apply { addOval(circle(c, r)) }
        val shadow = Path().apply { addOval(circle(Offset(c.x - r * 0.42f, c.y - r * 0.2f), r * 0.92f)) }
        drawPath(disc, hex(0xF4E6C6, 0.07f))   // earthshine on the dark side
        drawPath(Path.combine(PathOperation.Difference, disc, shadow), hex(0xF4E6C6))
    }

    private fun DrawScope.clouds(glass: Rect, t: Double) {
        val span = glass.width * 1.8f
        for (i in 0 until 3) {
            val speed = (glass.width * (0.006f + 0.005f * rnd(i, 21))).toDouble()
            val travel = fract((rnd(i, 22) * span + t * speed) / span).toFloat() * span
            val x = glass.left - glass.width * 0.4f + travel
            val y = glass.top + glass.height * (0.3f + 0.2f * rnd(i, 23))
            val cw = glass.width * (0.3f + 0.2f * rnd(i, 24))
            val ch = glass.height * 0.055f
            val cloud = Path().apply {
                addOval(Rect(Offset(x - cw * 0.5f, y - ch * 0.5f), Size(cw * 0.6f, ch)))
                addOval(Rect(Offset(x - cw * 0.25f, y - ch), Size(cw * 0.55f, ch * 1.4f)))
                addOval(Rect(Offset(x, y - ch * 0.45f), Size(cw * 0.5f, ch * 0.95f)))
            }
            drawPath(cloud, hex(0x9A7BA8, 0.10f))
        }
    }

    private fun DrawScope.skyline(glass: Rect, t: Double, k: Float, layer: Int) {
        val gw = glass.width
        val gh = glass.height
        val (minHeight, maxHeight) = if (layer == 0) 0.28f to 0.52f else 0.14f to 0.38f
        val litChance = if (layer == 0) 0.16f else 0.28f
        val windowSize = max(1.2f, gh * 0.011f)
        val pitch = windowSize * 2.3f
        val bodies = Path()
        val warm = Path()
        val cool = Path()
        var beacon = Offset.Zero
        var tallest = 0f
        var x = glass.left - gw * 0.03f * (layer + 1)
        var i = 0
        while (x < glass.right) {
            val seed = layer * 1_000 + i
            val bw = gw * (0.06f + 0.07f * rnd(seed, 31))
            val bh = gh * (minHeight + (maxHeight - minHeight) * rnd(seed, 32))
            val rect = Rect(x, glass.bottom - bh, x + bw, glass.bottom)
            bodies.addRect(rect)
            var roof = rect.top
            if (rnd(seed, 33) < 0.3f) {
                val box = Rect(Offset(rect.left + bw * 0.3f, rect.top - gh * 0.03f), Size(bw * 0.35f, gh * 0.03f))
                bodies.addRect(box)
                roof = box.top
            }
            if (bh > tallest) {
                tallest = bh
                beacon = Offset(rect.center.x, roof - 2f * k)
            }
            val cols = ((bw - pitch * 0.6f) / pitch).toInt()
            val rows = ((bh - pitch * 0.6f) / pitch).toInt()
            for (row in 0 until max(0, rows)) {
                for (col in 0 until max(0, cols)) {
                    val key = seed * 4_096 + row * 64 + col
                    // Most windows keep their state; about a third switch now and then.
                    var epoch = 0
                    if (rnd(key, 45) < 0.3f) {
                        val period = 40 + 120 * SceneRandom.unit(key, 41)
                        epoch = floor((t + SceneRandom.unit(key, 42) * period) / period).toInt()
                    }
                    if (rnd(key, 43 + epoch * 7) >= litChance) continue
                    val lit = Rect(Offset(rect.left + pitch * 0.6f + col * pitch, rect.top + pitch * 0.6f + row * pitch), Size(windowSize, windowSize))
                    if (rnd(key, 44) < 0.82f) warm.addRect(lit) else cool.addRect(lit)
                }
            }
            x += bw + gw * 0.006f * rnd(seed, 34)
            i++
        }
        drawPath(bodies, if (layer == 0) hex(0x2A2148) else hex(0x140E25))
        val brightness = if (layer == 0) 0.45f else 0.85f
        drawPath(warm, hex(0xFFC777, brightness))
        drawPath(cool, hex(0x9ED6E6, brightness * 0.8f))
        if (layer != 1) return
        // Aircraft warning light on the tallest tower.
        val on = fract(t / 2.4) < 0.12
        val r = 1.3f * k
        drawCircle(glow(0xFF4D4D, if (on) 0.5f else 0.08f, beacon, r * 5), r * 5, beacon)
        drawCircle(hex(0xFF5A5A, if (on) 1f else 0.35f), r, beacon)
    }

    private data class RainLayer(val count: Int, val speed: Float, val length: Float, val width: Float, val opacity: Float)
    private val rainLayers = listOf(
        RainLayer(70, 0.85f, 0.035f, 0.6f, 0.16f),   // far, fine
        RainLayer(45, 1.45f, 0.065f, 0.9f, 0.26f),   // near, heavier
    )

    fun DrawScope.drawRain(area: Rect, t: Double, k: Float, alphaScale: Float = 1f) {
        val slant = 0.16f
        rainLayers.forEachIndexed { index, layer ->
            val length = area.height * layer.length
            val travel = area.height + length * 2
            val cycle = (travel / (area.height * layer.speed)).toDouble()
            val streaks = Path()
            for (i in 0 until layer.count) {
                val seed = index * 500 + i
                val fall = t / cycle + SceneRandom.unit(seed, 51)
                val lap = floor(fall).toInt()
                val y = area.top - length + (fall - lap).toFloat() * travel
                // A new column on every fall, so no streak retraces the same line.
                val column = rnd(seed * 31 + lap, 52) * (area.width + slant * area.height)
                val x = area.left + column - slant * (y - area.top)
                streaks.moveTo(x, y)
                streaks.lineTo(x + slant * length, y - length)
            }
            drawPath(streaks, hex(0xD2DBF2, layer.opacity * alphaScale), style = Stroke(layer.width * k, cap = StrokeCap.Round))
        }
    }

    /** Drops clinging to the glass, sliding slowly down with a faint trail. */
    private fun DrawScope.beads(glass: Rect, t: Double, k: Float) {
        for (i in 0 until 14) {
            val r = (1.2f + 1.8f * rnd(i, 61)) * k
            val run = t * (0.02 + 0.06 * SceneRandom.unit(i, 62)) + SceneRandom.unit(i, 63)
            val lap = floor(run).toInt()
            val y = glass.top - 2 * r + (run - lap).toFloat() * (glass.height + 4 * r)
            val x = glass.left + rnd(i * 17 + lap, 64) * glass.width + sin(t * 0.9 + i).toFloat() * 0.8f * k
            val trailTop = y - glass.height * 0.08f
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.10f)), startY = trailTop, endY = y),
                Offset(x - r * 0.3f, trailTop), Size(r * 0.6f, glass.height * 0.08f), CornerRadius(r * 0.3f),
            )
            drawCircle(Color.White.copy(alpha = 0.16f), r, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.5f), r * 0.35f, Offset(x - r * 0.3f, y - r * 0.3f))
        }
    }

    private fun DrawScope.sheen(glass: Rect, lampReflection: Boolean) {
        drawRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.06f), 0.45f to Color.White.copy(alpha = 0f), 1f to Color.White.copy(alpha = 0.03f),
                start = glass.topLeft, end = glass.bottomRight,
            ),
            glass.topLeft, glass.size,
        )
        if (!lampReflection) return
        val c = Offset(glass.left + glass.width * 0.2f, glass.bottom - glass.height * 0.22f)
        val r = glass.height * 0.4f
        drawCircle(glow(0xFFB45E, 0.12f, c, r), r, c)
    }
}

/** Backdrop for the clock-only faces: dark gradient, drifting haze, faint rain, film grain. */
object Backdrop {
    private data class Pool(val rgb: Long, val opacity: Float, val period: Double, val phase: Double, val radius: Float)
    private val pools = listOf(
        Pool(0x7A5BB5, 0.22f, 140.0, 0.0, 0.8f),
        Pool(0xFFB45E, 0.12f, 110.0, 2.1, 0.6f),
        Pool(0xC9785A, 0.10f, 170.0, 4.2, 0.7f),
    )

    fun draw(scope: DrawScope, t: Double, rain: Boolean, u: Float) = with(scope) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(hex(0x110D1E), hex(0x1F1629))))
        pools.forEachIndexed { i, pool ->
            val a = t / pool.period * 2 * PI + pool.phase
            val center = Offset(w * (0.5f + 0.32f * cos(a).toFloat()), h * (0.5f + 0.28f * sin(a * 1.3 + i).toFloat()))
            drawRect(glow(pool.rgb, pool.opacity, center, h * pool.radius))
        }
        if (rain) with(NightView) { drawRain(Rect(Offset.Zero, size), t, h / 230f, alphaScale = 0.4f) }
        // Grain re-rolls 12 times a second whatever the frame rate.
        val frame = (t * 12).toInt()
        val grain = ArrayList<Offset>(700)
        for (i in 0 until 700) {
            val key = frame * 1_000 + i
            grain += Offset(rnd(key, 91) * w, rnd(key, 92) * h)
        }
        drawPoints(grain, PointMode.Points, Color.White.copy(alpha = 0.06f), strokeWidth = 1f * u)
        val radius = w * 0.62f
        val clearUntil = (h * 0.45f / radius).coerceIn(0f, 1f)
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, clearUntil to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.55f),
                center = Offset(w / 2, h / 2), radius = radius,
            ),
        )
    }
} 
