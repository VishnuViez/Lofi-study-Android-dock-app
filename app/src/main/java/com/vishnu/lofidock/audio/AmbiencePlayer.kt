package com.vishnu.lofidock.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Plays [AmbienceSynth] through an [AudioTrack], and rings the timer chime.
 *
 * The public API is called from the main thread. The render loop is a
 * coroutine on its own single audio thread, blocking in `AudioTrack.write`,
 * which paces it to the hardware. No audio focus is requested, so the
 * ambience mixes under music from other apps (the iOS `.mixWithOthers`).
 */
class AmbiencePlayer(private val context: Context, private val scope: CoroutineScope) {
    private val audioDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "lofi-audio") }
        .asCoroutineDispatcher()
    private val synth = AmbienceSynth(SAMPLE_RATE)
    private var track: AudioTrack? = null
    private var renderJob: Job? = null
    private var pendingStop: Job? = null
    private var alarm: Job? = null
    private var chimeTrack: AudioTrack? = null

    private var inForeground = false
    private var rainOn = false
    private var vinylOn = false
    private var muted = false

    private val chimePcm: ShortArray by lazy {
        Chime.samples(CHIME_RATE).let { f -> ShortArray(f.size) { (f[it] * 32_767f).toInt().toShort() } }
    }

    fun setSources(rain: Boolean, vinyl: Boolean) {
        rainOn = rain
        vinylOn = vinyl
        update()
    }

    fun setForeground(foreground: Boolean) {
        inForeground = foreground
        update()
    }

    /** Silences everything, timer chime included; rain/vinyl choices are kept. */
    fun setMuted(muted: Boolean) {
        this.muted = muted
        if (muted) stopChime()
        update()
    }

    /** Rings every 3 s for up to a minute until [stopAlarm]; each ring also vibrates. */
    fun startAlarm() {
        stopAlarm()
        alarm = scope.launch {
            repeat(20) {
                ringOnce()
                delay(3_000)
            }
        }
    }

    fun stopAlarm() {
        alarm?.cancel()
        alarm = null
        stopChime()
    }

    private val shouldPlay get() = inForeground && !muted && (rainOn || vinylOn)

    private fun update() {
        if (shouldPlay) {
            pendingStop?.cancel()
            pendingStop = null
            start()
            synth.rainLevel = if (rainOn) 1f else 0f
            synth.vinylLevel = if (vinylOn) 1f else 0f
        } else {
            synth.rainLevel = 0f
            synth.vinylLevel = 0f
            if (inForeground) scheduleStop() else stop()
        }
    }

    private fun start() {
        if (renderJob?.isActive == true) return
        val output = runCatching { buildStreamTrack() }.getOrNull() ?: return
        track = output
        synth.silence()
        output.play()
        renderJob = scope.launch(audioDispatcher) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = FloatArray(FRAMES_PER_WRITE * 2)
            while (isActive) {
                synth.render(FRAMES_PER_WRITE, buffer)
                output.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    /** Lets the synth's glide fall below -50 dB before releasing the track, so it never clicks. */
    private fun scheduleStop() {
        if (pendingStop != null || renderJob == null) return
        pendingStop = scope.launch {
            delay(2_000)
            pendingStop = null
            if (!shouldPlay) stop()
        }
    }

    private fun stop() {
        pendingStop?.cancel()
        pendingStop = null
        val job = renderJob ?: return
        val output = track
        renderJob = null
        track = null
        scope.launch {
            job.cancelAndJoin()   // the loop exits after its current write, then the track goes
            output?.run {
                pause()
                flush()
                release()
            }
        }
    }

    private fun buildStreamTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(mediaAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes, FRAMES_PER_WRITE * 2 * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun ringOnce() {
        vibrate()
        if (muted) return
        stopChime()
        chimeTrack = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(mediaAttributes())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(CHIME_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(chimePcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
                .apply {
                    write(chimePcm, 0, chimePcm.size)
                    play()
                }
        }.getOrNull()
    }

    private fun stopChime() {
        chimeTrack?.run {
            runCatching { stop() }
            release()
        }
        chimeTrack = null
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 160, 120, 160), -1))
    }

    private fun mediaAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHIME_RATE = 44_100
        const val FRAMES_PER_WRITE = 512
    }
} 
