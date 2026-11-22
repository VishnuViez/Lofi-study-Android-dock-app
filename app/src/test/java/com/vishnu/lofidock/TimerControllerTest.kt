package com.vishnu.lofidock

import com.vishnu.lofidock.core.CountdownTimer.Phase
import com.vishnu.lofidock.timer.TimerAlarms
import com.vishnu.lofidock.timer.TimerController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The coroutine timing, run on virtual time: no test waits in real time. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {
    private val t0 = 1_800_000_000_000L

    private class FakeAlarms : TimerAlarms {
        val scheduled = mutableListOf<Long>()
        var cancels = 0
        override fun schedule(endsAt: Long, lengthSeconds: Double, muted: Boolean) { scheduled += endsAt }
        override fun cancel() { cancels++ }
    }

    private fun TestScope.controller(alarms: TimerAlarms, foreground: () -> Boolean = { true }) =
        TimerController(backgroundScope, alarms, foreground, now = { t0 + testScheduler.currentTime })

    @Test fun ringsExactlyAtTheEndWhileInFront() = runTest {
        var rings = 0
        val alarms = FakeAlarms()
        val timers = controller(alarms).apply { onAlertStart = { rings++ } }
        timers.start(60.0, muted = false)
        assertEquals(listOf(t0 + 60_000), alarms.scheduled)

        advanceTimeBy(59_999); runCurrent()
        assertEquals(0, rings)
        advanceTimeBy(2); runCurrent()
        assertEquals(1, rings)
        assertEquals(Phase.Finished(t0 + 60_000), timers.timer.value.phase)
    }

    @Test fun finishesQuietlyWhenTheAppIsInTheBackground() = runTest {
        var rings = 0
        val timers = controller(FakeAlarms(), foreground = { false }).apply { onAlertStart = { rings++ } }
        timers.start(30.0, muted = false)
        advanceTimeBy(31_000); runCurrent()
        assertTrue(timers.timer.value.isFinished)
        assertEquals(0, rings)   // the alarm's notification covers it
    }

    @Test fun pauseStopsTheCountdownAndCancelsTheAlarm() = runTest {
        var rings = 0
        val alarms = FakeAlarms()
        val timers = controller(alarms).apply { onAlertStart = { rings++ } }
        timers.start(60.0, muted = false)
        advanceTimeBy(20_000); runCurrent()
        timers.pause()
        advanceTimeBy(600_000); runCurrent()
        assertEquals(0, rings)
        assertEquals(1, alarms.cancels)
        assertEquals(40.0, timers.timer.value.remainingSeconds(t0 + testScheduler.currentTime), 1e-9)

        timers.resume(muted = false)
        advanceTimeBy(40_001); runCurrent()
        assertEquals(1, rings)
    }

    @Test fun cancellingARingingTimerStopsTheAlert() = runTest {
        var stops = 0
        val timers = controller(FakeAlarms()).apply { onAlertStop = { stops++ } }
        timers.start(5.0, muted = false)
        advanceTimeBy(6_000); runCurrent()
        timers.cancel()
        assertEquals(1, stops)
        assertEquals(Phase.Idle, timers.timer.value.phase)
    }
}
