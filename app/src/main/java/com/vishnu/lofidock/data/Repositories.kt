package com.vishnu.lofidock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vishnu.lofidock.core.Checklist
import com.vishnu.lofidock.core.ClockFace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

val Context.lofiStore: DataStore<Preferences> by preferencesDataStore(name = "lofi_dock")

/** Every user setting, with the same defaults as the iOS app. */
data class DockSettings(
    val face: ClockFace = ClockFace.ROOM,
    val nightDimming: Boolean = true,
    val nightStart: Int = 22 * 60,
    val nightEnd: Int = 7 * 60,
    val burnInShift: Boolean = true,
    val adaptiveMotion: Boolean = true,
    val rain: Boolean = true,
    val vinyl: Boolean = true,
    val muted: Boolean = false,
    val rainVisual: Boolean = true,
    val timerLengthSeconds: Double = 25 * 60.0,
    val awakeOnlyWhileCharging: Boolean = false,
)

/** Key names match the iOS app's UserDefaults keys. */
object PrefKeys {
    val FACE = stringPreferencesKey("clock.face")
    val NIGHT_DIMMING = booleanPreferencesKey("night.enabled")
    val NIGHT_START = intPreferencesKey("night.startMinute")
    val NIGHT_END = intPreferencesKey("night.endMinute")
    val BURN_IN = booleanPreferencesKey("display.burnInShift")
    val ADAPTIVE_MOTION = booleanPreferencesKey("display.adaptiveMotion")
    val RAIN = booleanPreferencesKey("ambience.rain")
    val VINYL = booleanPreferencesKey("ambience.vinyl")
    val MUTED = booleanPreferencesKey("ambience.muted")
    val RAIN_VISUAL = booleanPreferencesKey("display.rainVisual")
    val TIMER_LENGTH = doublePreferencesKey("timer.lengthSeconds")
    val AWAKE_ONLY_WHILE_CHARGING = booleanPreferencesKey("display.awakeOnlyWhileCharging")
    val CHECKLIST = stringPreferencesKey("checklist.items")
}

private fun Preferences.toSettings() = DockSettings().let { d ->
    DockSettings(
        face = ClockFace.fromKey(this[PrefKeys.FACE]) ?: d.face,
        nightDimming = this[PrefKeys.NIGHT_DIMMING] ?: d.nightDimming,
        nightStart = this[PrefKeys.NIGHT_START] ?: d.nightStart,
        nightEnd = this[PrefKeys.NIGHT_END] ?: d.nightEnd,
        burnInShift = this[PrefKeys.BURN_IN] ?: d.burnInShift,
        adaptiveMotion = this[PrefKeys.ADAPTIVE_MOTION] ?: d.adaptiveMotion,
        rain = this[PrefKeys.RAIN] ?: d.rain,
        vinyl = this[PrefKeys.VINYL] ?: d.vinyl,
        muted = this[PrefKeys.MUTED] ?: d.muted,
        rainVisual = this[PrefKeys.RAIN_VISUAL] ?: d.rainVisual,
        timerLengthSeconds = this[PrefKeys.TIMER_LENGTH]?.takeIf { it >= 1 } ?: d.timerLengthSeconds,
        awakeOnlyWhileCharging = this[PrefKeys.AWAKE_ONLY_WHILE_CHARGING] ?: d.awakeOnlyWhileCharging,
    )
}

/** Settings as a StateFlow backed by DataStore; writes happen on [scope]. */
class SettingsRepository(private val store: DataStore<Preferences>, private val scope: CoroutineScope) {
    // The first value is read synchronously (a tiny file) so the app opens on the
    // saved face instead of flashing the default one.
    val settings: StateFlow<DockSettings> = store.data.map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, runBlocking { store.data.first().toSettings() })

    fun <T> set(key: Preferences.Key<T>, value: T) {
        scope.launch { store.edit { it[key] = value } }
    }

    fun setFace(face: ClockFace) = set(PrefKeys.FACE, face.key)
}

/**
 * The checklist, saved as JSON on every change so it survives relaunches.
 * Each change is a read-modify-write inside one DataStore transaction.
 */
class ChecklistRepository(private val store: DataStore<Preferences>, private val scope: CoroutineScope) {
    val checklist: StateFlow<Checklist> = store.data.map { Checklist.fromJson(it[PrefKeys.CHECKLIST]) }
        .stateIn(scope, SharingStarted.Eagerly, Checklist())

    private fun change(transform: (Checklist) -> Checklist) {
        scope.launch {
            store.edit { prefs -> prefs[PrefKeys.CHECKLIST] = transform(Checklist.fromJson(prefs[PrefKeys.CHECKLIST])).toJson() }
        }
    }

    fun add(title: String) = change { it.add(title) }
    fun toggle(id: String) = change { it.toggle(id) }
    fun remove(id: String) = change { it.remove(id) }
    fun clearDone() = change { it.clearDone() }
} 
