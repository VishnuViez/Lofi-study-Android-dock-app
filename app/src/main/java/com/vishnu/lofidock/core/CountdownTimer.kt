package com.vishnu.lofidock.core

import kotlin.math.max
import kotlin.math.min

/**
 * A kitchen-timer countdown: pick a length, start, pause, resume, cancel.
 * An immutable value, so it sits directly in a StateFlow; TimerController
 * adds the coroutine that fires at the end, alarms and sound.
 * Times are epoch milliseconds; lengths are seconds.
 */
data class CountdownTimer(val lengthSeconds: Double = 0.0, val phase: Phase = Phase.Idle) {

    sealed interface Phase {
        data object Idle : Phase
        data class Running(val endsAt: Long) : Phase
        data class Paused(val remainingSeconds: Double) : Phase
        data class Finished(val at: Long) : Phase
    }

    val isIdle: Boolean get() = phase == Phase.Idle
    val isRunning: Boolean get() = phase is Phase.Running
    val isPaused: Boolean get() = phase is Phase.Paused
    val isFinished: Boolean get() = phase is Phase.Finished

    fun start(lengthSeconds: Double, now: Long): CountdownTimer {
        if (lengthSeconds < 1) return this
        return CountdownTimer(lengthSeconds, Phase.Running(now + (lengthSeconds * 1000).toLong()))
    }

    fun pause(now: Long): CountdownTimer {
        val running = phase as? Phase.Running ?: return this
        return copy(phase = Phase.Paused(max(0L, running.endsAt - now) / 1000.0))
    }

    fun resume(now: Long): CountdownTimer {
        val paused = phase as? Phase.Paused ?: return this
        return copy(phase = Phase.Running(now + (paused.remainingSeconds * 1000).toLong()))
    }

    fun cancel(): CountdownTimer = copy(phase = Phase.Idle)

    /** Finished once a running timer's end has passed; otherwise unchanged. */
    fun updated(now: Long): CountdownTimer {
        val running = phase as? Phase.Running ?: return this
        return if (now >= running.endsAt) copy(phase = Phase.Finished(running.endsAt)) else this
    }

    fun remainingSeconds(now: Long): Double = when (val p = phase) {
        Phase.Idle -> lengthSeconds
        is Phase.Running -> max(0L, p.endsAt - now) / 1000.0
        is Phase.Paused -> p.remainingSeconds
        is Phase.Finished -> 0.0
    }

    /** Share of the run still to go, from 1 down to 0. */
    fun fractionRemaining(now: Long): Double {
        if (lengthSeconds <= 0) return 0.0
        return min(1.0, max(0.0, remainingSeconds(now) / lengthSeconds))
    }
} 
