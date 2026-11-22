package com.vishnu.lofidock.timer

import com.vishnu.lofidock.core.CountdownTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Schedules the "time's up" notification for when the app isn't in front. */
interface TimerAlarms {
    fun schedule(endsAt: Long, lengthSeconds: Double, muted: Boolean)
    fun cancel()
}

/**
 * Runs the countdown. While the app is open, a coroutine waits until the end
 * and fires the in-app alert; an exact alarm posts a notification instead when
 * you've left the app (the alarm receiver skips it while the app is in front).
 */
class TimerController(
    private val scope: CoroutineScope,
    private val alarms: TimerAlarms,
    private val isInForeground: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _timer = MutableStateFlow(CountdownTimer())
    val timer: StateFlow<CountdownTimer> = _timer.asStateFlow()

    var onAlertStart: () -> Unit = {}
    var onAlertStop: () -> Unit = {}
    private var finishJob: Job? = null

    fun start(lengthSeconds: Double, muted: Boolean) {
        _timer.value = _timer.value.start(lengthSeconds, now())
        arm()
        scheduleAlarm(muted)
    }

    fun pause() {
        _timer.value = _timer.value.pause(now())
        disarm()
    }

    fun resume(muted: Boolean) {
        _timer.value = _timer.value.resume(now())
        arm()
        scheduleAlarm(muted)
    }

    /** Back to idle from any phase, silencing a ringing alert. */
    fun cancel() {
        val wasRinging = _timer.value.isFinished
        _timer.value = _timer.value.cancel()
        disarm()
        if (wasRinging) onAlertStop()
    }

    /**
     * Catches up on returning to the app. A timer that ended while you were
     * away is marked finished quietly; its notification already went off.
     */
    fun refresh() {
        val updated = _timer.value.updated(now())
        if (updated !== _timer.value) _timer.value = updated else arm()
    }

    private fun arm() {
        finishJob?.cancel()
        val running = _timer.value.phase as? CountdownTimer.Phase.Running ?: return
        finishJob = scope.launch {
            delay((running.endsAt - now()).coerceAtLeast(0))
            fire()
        }
    }

    private fun fire() {
        val running = _timer.value.phase as? CountdownTimer.Phase.Running ?: return
        val updated = _timer.value.updated(now())
        if (updated === _timer.value) return arm()   // woke a hair early
        _timer.value = updated
        if (isInForeground() && now() - running.endsAt < 5_000) onAlertStart()
    }

    private fun disarm() {
        finishJob?.cancel()
        finishJob = null
        alarms.cancel()
    }

    private fun scheduleAlarm(muted: Boolean) {
        val running = _timer.value.phase as? CountdownTimer.Phase.Running ?: return
        alarms.schedule(running.endsAt, _timer.value.lengthSeconds, muted)
    }
} 
