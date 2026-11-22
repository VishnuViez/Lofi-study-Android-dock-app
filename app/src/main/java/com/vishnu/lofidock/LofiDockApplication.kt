package com.vishnu.lofidock

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vishnu.lofidock.audio.AmbiencePlayer
import com.vishnu.lofidock.core.SessionClock
import com.vishnu.lofidock.core.Thermal
import com.vishnu.lofidock.data.ChecklistRepository
import com.vishnu.lofidock.data.SettingsRepository
import com.vishnu.lofidock.data.lofiStore
import com.vishnu.lofidock.system.DeviceHeat
import com.vishnu.lofidock.system.chargingFlow
import com.vishnu.lofidock.system.deviceHeatFlow
import com.vishnu.lofidock.timer.AndroidTimerAlarms
import com.vishnu.lofidock.timer.TimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LofiDockApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/**
 * The app's long-lived objects, all driven from one main-thread scope. The
 * process lifecycle (any activity started vs. none) stands in for iOS's
 * foreground/background: it ends sessions, starts and stops sound, and lets
 * the timer catch up.
 */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(app.lofiStore, scope)
    val checklist = ChecklistRepository(app.lofiStore, scope)
    val session = SessionClock()
    val ambience = AmbiencePlayer(app, scope)

    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    val timers = TimerController(
        scope,
        AndroidTimerAlarms(app),
        isInForeground = { processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
    )

    // Broadcast receivers stay registered only while the UI is collecting.
    val charging: StateFlow<Boolean> = app.chargingFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)
    val heat: StateFlow<DeviceHeat> = app.deviceHeatFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DeviceHeat(Thermal.NOMINAL, false))

    init {
        timers.onAlertStart = { ambience.startAlarm() }
        timers.onAlertStop = { ambience.stopAlarm() }

        processLifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                session.appDidBecomeActive()
                timers.refresh()
                ambience.setForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                session.appDidEnterBackground()
                ambience.stopAlarm()
                ambience.setForeground(false)
            }
        })

        scope.launch {
            settings.settings
                .map { Triple(it.muted, it.rain, it.vinyl) }
                .distinctUntilChanged()
                .collect { (muted, rain, vinyl) ->
                    ambience.setMuted(muted)
                    ambience.setSources(rain, vinyl)
                }
        }
    }
} 
