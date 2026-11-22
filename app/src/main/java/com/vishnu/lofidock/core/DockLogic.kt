package com.vishnu.lofidock.core

import java.time.Instant
import java.time.ZoneId

/** The selectable dock faces, in swipe order. Keys are persisted. */
enum class ClockFace(val key: String, val title: String) {
    ROOM("room", "Lofi Room"),
    DIGITS("digits", "Big Digits"),
    ANALOG("analog", "Analog"),
    FLIP("flip", "Flip"),
    TIMER("timer", "Timer"),
    CHECKLIST("checklist", "Checklist");

    /** The face [step] places along, wrapping at both ends. */
    fun cycled(step: Int): ClockFace {
        val all = entries
        return all[Math.floorMod(ordinal + step, all.size)]
    }

    companion object {
        fun fromKey(key: String?): ClockFace? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The hours during which the dock dims itself, as minutes after midnight.
 * A window that crosses midnight (22:00 to 07:00) is the usual case.
 */
data class NightSchedule(val startMinute: Int, val endMinute: Int) {
    /** Start is inclusive, end exclusive. Equal start and end means no night. */
    fun contains(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (startMinute == endMinute) return false
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val minute = time.hour * 60 + time.minute
        return if (startMinute < endMinute) minute in startMinute until endMinute
        else minute >= startMinute || minute < endMinute
    }
}

/** The phone's thermal state, collapsed to the four levels the budget uses. */
enum class Thermal { NOMINAL, FAIR, SERIOUS, CRITICAL }

/**
 * How often the animated scenery redraws. An always-on screen on a wireless
 * charger runs warm and Android throttles hot phones, so with adaptive motion
 * on, the frame rate backs off as the phone heats up. Clocks tick on their own
 * one-second timer, so the time shown never depends on this.
 */
object AnimationBudget {
    const val FULL = 30f

    fun framesPerSecond(thermal: Thermal, powerSave: Boolean, adaptive: Boolean): Float {
        if (!adaptive) return FULL
        return when (thermal) {
            Thermal.CRITICAL -> 1f
            Thermal.SERIOUS -> 8f
            Thermal.FAIR -> if (powerSave) 10f else 20f
            Thermal.NOMINAL -> if (powerSave) 15f else FULL
        }
    }
}

/**
 * Hand angles in degrees, clockwise from 12. Each includes the fraction of the
 * smaller unit, so hands sweep instead of jumping.
 */
data class AnalogHands(val hour: Double, val minute: Double, val second: Double) {
    companion object {
        fun at(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): AnalogHands {
            val time = Instant.ofEpochMilli(epochMillis).atZone(zone)
            val seconds = time.second + time.nano / 1e9
            val minutes = time.minute + seconds / 60
            val hours = (time.hour % 12) + minutes / 60
            return AnalogHands(hour = hours * 30, minute = minutes * 6, second = seconds * 6)
        }
    }
}

/**
 * Whether to hold the display on. While Lofi Dock is in front it overrides
 * the phone's screen timeout; "Only while charging" narrows that to when the
 * phone is on a charger.
 */
object ScreenAwake {
    fun shouldStayOn(onlyWhileCharging: Boolean, isCharging: Boolean): Boolean =
        !onlyWhileCharging || isCharging
}

/**
 * Stable pseudo-random values keyed by (index, salt), so every frame places
 * the same stars, buildings and raindrops without storing any of them.
 * Bit-for-bit the same splitmix64 finaliser as the iOS app, so both draw the
 * same skyline.
 */
object SceneRandom {
    fun unit(index: Int, salt: Int = 0): Double {
        var z = index.toLong() * 0x9E3779B97F4A7C15uL.toLong() + salt.toLong() * 0xD1B54A32D192ED03uL.toLong()
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        z = z xor (z ushr 31)
        return (z ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}
