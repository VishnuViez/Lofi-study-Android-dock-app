package com.vishnu.lofidock.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Walks always-on content around a small figure-eight, one step a minute, so
 * an OLED panel never shows the same bright edges for hours.
 */
object BurnInDrift {
    const val STEPS_PER_LOOP = 12

    data class Offset(val x: Float, val y: Float)

    fun offset(epochMillis: Long, amplitude: Float = 6f): Offset {
        val minute = Math.floorDiv(epochMillis, 60_000L)
        val step = Math.floorMod(minute, STEPS_PER_LOOP.toLong()).toInt()
        val angle = step.toDouble() / STEPS_PER_LOOP * 2 * PI
        return Offset(
            x = amplitude * cos(angle).toFloat(),
            y = amplitude * 0.6f * sin(angle * 2).toFloat(),
        )
    }
}
