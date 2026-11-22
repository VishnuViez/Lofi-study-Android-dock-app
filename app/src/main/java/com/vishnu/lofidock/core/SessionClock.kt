package com.vishnu.lofidock.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How long the current study session has run.
 *
 * A session lasts while the app stays in front. Leaving it (home, recents,
 * the power button) ends the session, and the next return starts a fresh one
 * at zero. Pulling down the notification shade doesn't stop the activity, so
 * it leaves the session running.
 */
class SessionClock(now: Long = System.currentTimeMillis()) {
    private val _startedAt = MutableStateFlow(now)
    val startedAt: StateFlow<Long> = _startedAt.asStateFlow()
    private var leftApp = false

    fun elapsedSeconds(at: Long): Double = ((at - _startedAt.value).coerceAtLeast(0)) / 1000.0

    fun appDidEnterBackground() {
        leftApp = true
    }

    fun appDidBecomeActive(now: Long = System.currentTimeMillis()) {
        if (!leftApp) return
        leftApp = false
        _startedAt.value = now
    }
} 
