package com.vishnu.lofidock.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The timer's "time's up" sound: three soft rising bell strikes, generated in
 * code. `res/raw/lofi_chime.wav` (the notification sound) is this same
 * waveform, rendered by the iOS project's preview tool.
 */
object Chime {
    const val PEAK = 0.6f

    fun samples(sampleRate: Int): FloatArray {
        val strikes = listOf(0.0 to 659.25, 0.45 to 783.99, 0.9 to 987.77)   // E5 G5 B5
        val count = (3.0 * sampleRate).toInt()
        val out = FloatArray(count)
        for ((time, f) in strikes) {
            val start = (time * sampleRate).toInt()
            for (i in start until count) {
                val t = (i - start).toDouble() / sampleRate
                val attack = min(1.0, t / 0.004)   // 4 ms ramp so the strike doesn't click
                // A bell's partials are inharmonic and the high ones die first.
                val tone = sin(2 * PI * f * t) +
                    0.45 * sin(2 * PI * 2.76 * f * t) * exp(-t / 0.35) +
                    0.25 * sin(2 * PI * 5.4 * f * t) * exp(-t / 0.15)
                out[i] += (attack * exp(-t / 0.9) * tone).toFloat()
            }
        }
        val tail = (0.1 * sampleRate).toInt()
        for (i in (count - tail) until count) out[i] *= (count - i).toFloat() / tail
        val loudest = out.maxOf { abs(it) }
        for (i in out.indices) out[i] = out[i] / loudest * PEAK
        return out
    }
}

/** 16-bit PCM WAV encoding; two or more channels are interleaved. */
object WavFile {
    fun data(channels: List<FloatArray>, sampleRate: Int): ByteArray {
        val frames = channels.firstOrNull()?.size ?: 0
        val channelCount = channels.size
        val bytes = frames * channelCount * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes)
            put("WAVEfmt ".toByteArray()); putInt(16); putShort(1)
            putShort(channelCount.toShort()); putInt(sampleRate)
            putInt(sampleRate * channelCount * 2); putShort((channelCount * 2).toShort()); putShort(16)
            put("data".toByteArray()); putInt(bytes)
        }
        val body = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            for (channel in channels) {
                body.putShort((channel[i].coerceIn(-1f, 1f) * 32_767f).roundToInt().toShort())
            }
        }
        return ByteArrayOutputStream(44 + bytes).apply {
            write(header.array())
            write(body.array())
        }.toByteArray()
    }
} 
