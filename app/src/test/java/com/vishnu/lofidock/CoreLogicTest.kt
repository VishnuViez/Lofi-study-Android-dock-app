package com.vishnu.lofidock

import com.vishnu.lofidock.core.AnalogHands
import com.vishnu.lofidock.core.AnimationBudget
import com.vishnu.lofidock.core.BurnInDrift
import com.vishnu.lofidock.core.ClockFace
import com.vishnu.lofidock.core.NightSchedule
import com.vishnu.lofidock.core.SceneRandom
import com.vishnu.lofidock.core.ScreenAwake
import com.vishnu.lofidock.core.SessionClock
import com.vishnu.lofidock.core.Thermal
import com.vishnu.lofidock.core.TimeFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

private val UTC = ZoneOffset.UTC
private fun at(hour: Int, minute: Int, second: Int = 0): Long =
    ZonedDateTime.of(2026, 11, 21, hour, minute, second, 0, UTC).toInstant().toEpochMilli()

class SessionClockTest {
    private val launch = 1_800_000_000_000L

    @Test fun countsUpFromLaunch() {
        assertEquals(125.0, SessionClock(launch).elapsedSeconds(launch + 125_000), 0.0)
    }

    @Test fun neverReportsNegativeTime() {
        assertEquals(0.0, SessionClock(launch).elapsedSeconds(launch - 10_000), 0.0)
    }

    @Test fun leavingTheAppStartsAFreshSessionOnReturn() {
        val clock = SessionClock(launch)
        clock.appDidEnterBackground()
        val back = launch + 3_600_000
        clock.appDidBecomeActive(back)
        assertEquals(back, clock.startedAt.value)
        assertEquals(5.0, clock.elapsedSeconds(back + 5_000), 0.0)
    }

    @Test fun becomingActiveWithoutLeavingKeepsTheSession() {
        val clock = SessionClock(launch)
        clock.appDidBecomeActive(launch + 30_000)
        assertEquals(launch, clock.startedAt.value)
    }

    @Test fun eachExitResetsOnlyOnce() {
        val clock = SessionClock(launch)
        clock.appDidEnterBackground()
        clock.appDidBecomeActive(launch + 60_000)
        clock.appDidBecomeActive(launch + 150_000)
        assertEquals(launch + 60_000, clock.startedAt.value)
    }
}

class TimeFormattingTest {
    @Test fun sessionLength() {
        val cases = listOf(0.0 to "0:00:00", 59.9 to "0:00:59", 3_725.0 to "1:02:05",
            -4.0 to "0:00:00", 360_000.0 to "100:00:00", Double.NaN to "0:00:00")
        for ((seconds, expected) in cases) assertEquals(expected, TimeFormatting.session(seconds))
    }

    @Test fun countdownRoundsUp() {
        val cases = listOf(1_500.0 to "25:00", 0.2 to "0:01", 0.0 to "0:00",
            3_900.0 to "1:05:00", 59.4 to "1:00", -3.0 to "0:00")
        for ((seconds, expected) in cases) assertEquals(expected, TimeFormatting.countdown(seconds))
    }

    @Test fun twelveHourSplitsOutThePeriod() {
        assertEquals(TimeFormatting.Clock("9:41", "PM"),
            TimeFormatting.clock(at(21, 41), use24Hour = false, locale = Locale.US, zone = UTC))
    }

    @Test fun midnightReadsTwelveOnATwelveHourClock() {
        assertEquals(TimeFormatting.Clock("12:05", "AM"),
            TimeFormatting.clock(at(0, 5), use24Hour = false, locale = Locale.US, zone = UTC))
    }

    @Test fun twentyFourHourHasNoPeriod() {
        assertEquals(TimeFormatting.Clock("21:41", null),
            TimeFormatting.clock(at(21, 41), use24Hour = true, locale = Locale.UK, zone = UTC))
    }

    @Test fun dateLineNamesTheDay() {
        assertEquals("Saturday, November 21", TimeFormatting.dateLine(at(9, 0), Locale.US, UTC))
    }
}

class BurnInDriftTest {
    private val minuteStart = 60_000L * 29_000_000

    @Test fun holdsStillWithinAMinute() {
        assertEquals(BurnInDrift.offset(minuteStart), BurnInDrift.offset(minuteStart + 59_000))
    }

    @Test fun staysWithinTheAmplitude() {
        for (m in 0 until 120) {
            val o = BurnInDrift.offset(minuteStart + m * 60_000L, amplitude = 6f)
            assertTrue(abs(o.x) <= 6f && abs(o.y) <= 6f)
        }
    }

    @Test fun visitsManyPositionsPerLoop() {
        val positions = (0 until BurnInDrift.STEPS_PER_LOOP).map {
            val o = BurnInDrift.offset(minuteStart + it * 60_000L)
            "${(o.x * 100).toInt()},${(o.y * 100).toInt()}"
        }.toSet()
        assertTrue(positions.size >= 10)
    }

    @Test fun handlesDatesBeforeTheEpoch() {
        val o = BurnInDrift.offset(-90_000, amplitude = 6f)
        assertTrue(abs(o.x) <= 6f && abs(o.y) <= 6f)
    }
}

class DockSettingsLogicTest {
    @Test fun overnightWindow() {
        val night = NightSchedule(22 * 60, 7 * 60)
        val cases = listOf(Triple(22, 0, true), Triple(23, 59, true), Triple(0, 0, true), Triple(6, 59, true),
            Triple(7, 0, false), Triple(12, 0, false), Triple(21, 59, false))
        for ((h, m, expected) in cases) assertEquals("$h:$m", expected, night.contains(at(h, m), UTC))
    }

    @Test fun sameDayWindow() {
        val nap = NightSchedule(13 * 60, 15 * 60)
        assertTrue(nap.contains(at(14, 0), UTC))
        assertFalse(nap.contains(at(15, 0), UTC))
        assertFalse(nap.contains(at(2, 0), UTC))
    }

    @Test fun equalStartAndEndMeansNoNight() {
        val none = NightSchedule(600, 600)
        assertFalse(none.contains(at(10, 0), UTC))
        assertFalse(none.contains(at(3, 0), UTC))
    }

    @Test fun frameRateBacksOffAsThePhoneWarms() {
        assertEquals(30f, AnimationBudget.framesPerSecond(Thermal.NOMINAL, false, true))
        assertEquals(30f, AnimationBudget.framesPerSecond(Thermal.CRITICAL, true, adaptive = false))
        val rates = Thermal.entries.map { AnimationBudget.framesPerSecond(it, false, true) }
        assertEquals(rates.sortedDescending(), rates)
        assertTrue(rates.last() >= 1f)
        assertTrue(AnimationBudget.framesPerSecond(Thermal.NOMINAL, powerSave = true, adaptive = true) < 30f)
    }

    @Test fun screenStaysOnRegardlessOfChargingByDefault() {
        assertTrue(ScreenAwake.shouldStayOn(onlyWhileCharging = false, isCharging = false))
        assertTrue(ScreenAwake.shouldStayOn(onlyWhileCharging = false, isCharging = true))
        assertTrue(ScreenAwake.shouldStayOn(onlyWhileCharging = true, isCharging = true))
        assertFalse(ScreenAwake.shouldStayOn(onlyWhileCharging = true, isCharging = false))
    }

    @Test fun analogHands() {
        assertEquals(AnalogHands(90.0, 0.0, 0.0), AnalogHands.at(at(15, 0), UTC))
        val halfNine = AnalogHands.at(at(9, 30), UTC)
        assertEquals(285.0, halfNine.hour, 1e-9)
        assertEquals(180.0, halfNine.minute, 1e-9)
        val sweep = AnalogHands.at(at(12, 0, 30), UTC)
        assertEquals(0.25, sweep.hour, 1e-9)
        assertEquals(3.0, sweep.minute, 1e-9)
        assertEquals(180.0, sweep.second, 1e-9)
    }

    @Test fun facesCycleBothWaysAndWrap() {
        assertEquals(ClockFace.DIGITS, ClockFace.ROOM.cycled(1))
        assertEquals(ClockFace.CHECKLIST, ClockFace.ROOM.cycled(-1))
        assertEquals(ClockFace.ROOM, ClockFace.CHECKLIST.cycled(1))
        assertEquals(ClockFace.CHECKLIST, ClockFace.ANALOG.cycled(9))
    }

    /** Keys are persisted (and match iOS); renaming one resets everyone's face. */
    @Test fun faceKeysAreStable() {
        assertEquals(listOf("room", "digits", "analog", "flip", "timer", "checklist"), ClockFace.entries.map { it.key })
        assertEquals(ClockFace.FLIP, ClockFace.fromKey("flip"))
        assertEquals(null, ClockFace.fromKey("nope"))
    }

    /** Reference values computed by the iOS SceneRandom, so both apps draw the same skyline. */
    @Test fun sceneRandomMatchesTheIosApp() {
        assertEquals(0.0, SceneRandom.unit(0, 0), 0.0)
        assertEquals(0.16585117907674918, SceneRandom.unit(7, 11), 0.0)
        assertEquals(0.46393086567689479, SceneRandom.unit(123, 52), 0.0)
        assertEquals(0.58672810159037614, SceneRandom.unit(-5, 3), 0.0)
    }
} 
