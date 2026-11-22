package com.vishnu.lofidock.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.vishnu.lofidock.core.Thermal
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether the phone is on a charger (wireless or cable, including when full). */
fun Context.chargingFlow(): Flow<Boolean> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent.isCharging())
        }
    }
    // The battery broadcast is sticky: registering returns the current state.
    val current = ContextCompat.registerReceiver(
        this@chargingFlow, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    current?.let { trySend(it.isCharging()) }
    awaitClose { unregisterReceiver(receiver) }
}.distinctUntilChanged()

private fun Intent.isCharging(): Boolean {
    val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
}

data class DeviceHeat(val thermal: Thermal, val powerSave: Boolean)

/** Thermal state and Battery Saver, for the animation frame budget. */
fun Context.deviceHeatFlow(): Flow<DeviceHeat> = callbackFlow {
    val power = getSystemService(PowerManager::class.java)
    fun current() = DeviceHeat(power.thermal(), power.isPowerSaveMode)
    trySend(current())

    val thermalListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PowerManager.OnThermalStatusChangedListener { trySend(current()) }
            .also { power.addThermalStatusListener(mainExecutor, it) }
    } else null
    val saverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(current())
        }
    }
    ContextCompat.registerReceiver(
        this@deviceHeatFlow, saverReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    awaitClose {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            power.removeThermalStatusListener(thermalListener)
        }
        unregisterReceiver(saverReceiver)
    }
}.distinctUntilChanged()

/** Android's seven thermal levels collapsed onto the four the budget uses. */
private fun PowerManager.thermal(): Thermal {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Thermal.NOMINAL
    return when (currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> Thermal.NOMINAL
        PowerManager.THERMAL_STATUS_LIGHT, PowerManager.THERMAL_STATUS_MODERATE -> Thermal.FAIR
        PowerManager.THERMAL_STATUS_SEVERE -> Thermal.SERIOUS
        else -> Thermal.CRITICAL
    }
}
