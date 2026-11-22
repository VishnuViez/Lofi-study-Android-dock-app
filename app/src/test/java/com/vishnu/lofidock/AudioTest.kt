package com.vishnu.lofidock

import com.vishnu.lofidock.audio.AmbienceSynth
import com.vishnu.lofidock.audio.Chime
import com.vishnu.lofidock.audio.NoiseRandom
import com.vishnu.lofidock.audio.WavFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class AmbienceSynthTest {
    private val rate = 48_000

    /** Renders like the audio thread does: 512-frame buffers. Returns (left, right). */
    private fun render(synth: AmbienceSynth, seconds: Double): Pair<FloatArray, FloatArray> {
        val frames = (rate * seconds).toInt()
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        val buffer = FloatArray(512 * 2)
        var i = 0
        while (i < frames) {
            val n = minOf(512, frames - i)
            synth.render(n, buffer)
            for (f in 0 until n) {
                left[i + f] = buffer[2 * f]
                right[i + f] = buffer[2 * f + 1]
            }
            i += n
        }
        return left to right
    }

    private fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Float {
        var sum = 0.0
        for (i in from until to) sum += x[i] * x[i]
        return sqrt(sum / (to - from)).toFloat()
    }

    private fun synth(rain: Float, vinyl: Float, seed: Long = 7) =
        AmbienceSynth(rate, seed).apply { rainLevel = rain; vinylLevel = vinyl }

    @Test fun silentWhenBothSourcesAreOff() {
        val (l, r) = render(synth(0f, 0f), 1.0)
        assertTrue(l.all { it == 0f } && r.all { it == 0f })
    }

    /** Around -20 dBFS: present at normal volume without dominating it. Same range as iOS. */
    @Test fun rainSitsInAComfortableRange() {
        val (l, _) = render(synth(1f, 0f), 4.0)
        val level = rms(l, from = rate * 2)
        assertTrue("rms $level", level in 0.05f..0.2f)
        assertTrue(l.all { it.isFinite() && abs(it) < 1f })
    }

    @Test fun vinylIsSparseCrackle() {
        val (l, _) = render(synth(0f, 1f), 6.0)
        val steady = l.copyOfRange(rate * 2, l.size)
        val peak = steady.maxOf { abs(it) }
        assertTrue(peak / rms(steady) > 8f)
        assertTrue(peak < 1f)
    }

    @Test fun fadesInRatherThanJumping() {
        val (l, _) = render(synth(1f, 0f), 3.0)
        assertTrue(rms(l, 0, rate / 100) < rms(l, from = rate * 2) * 0.1f)
    }

    @Test fun turningASourceOffFadesItOut() {
        val s = synth(1f, 0f)
        render(s, 2.0)
        s.rainLevel = 0f
        val (l, _) = render(s, 2.5)
        assertTrue(rms(l, from = rate * 2) < 0.002f)
    }

    @Test fun sameSeedSameSound() {
        val a = render(synth(1f, 1f, seed = 42), 0.5)
        val b = render(synth(1f, 1f, seed = 42), 0.5)
        assertArrayEquals(a.first, b.first, 0f)
        assertArrayEquals(a.second, b.second, 0f)
    }

    @Test fun rainChannelsAreDecorrelated() {
        val (l, r) = render(synth(1f, 0f), 3.0)
        val from = rate * 2
        var dot = 0.0
        for (i in from until l.size) dot += l[i] * r[i]
        val correlation = dot / (rms(l, from) * rms(r, from) * (l.size - from))
        assertTrue("correlation $correlation", abs(correlation) < 0.3)
    }

    /** Same xorshift stream as the iOS NoiseRandom (reference values from Swift). */
    @Test fun noiseMatchesTheIosApp() {
        val r = NoiseRandom(42)
        assertEquals(45_454_805_674L, r.next())
        assertEquals(-6_914_526_270_109_646_145L, r.next())   // 11532217803599905471 as UInt64
    }
}

class ChimeAndWavTest {
    @Test fun chimeIsThreeSecondsAtTheIntendedPeak() {
        val samples = Chime.samples(44_100)
        assertEquals(3 * 44_100, samples.size)
        assertEquals(Chime.PEAK, samples.maxOf { abs(it) }, 1e-4f)
    }

    @Test fun chimeStartsAndEndsWithoutAClick() {
        val samples = Chime.samples(44_100)
        assertTrue(abs(samples.first()) < 0.01f)
        assertTrue(abs(samples.last()) < 0.001f)
    }

    @Test fun wavHeaderDescribesTheSamples() {
        val data = WavFile.data(listOf(floatArrayOf(0f, 0.5f, -0.5f), floatArrayOf(1f, -1f, 0f)), 48_000)
        assertEquals(44 + 3 * 2 * 2, data.size)
        assertEquals("RIFF", String(data, 0, 4))
        assertEquals("WAVE", String(data, 8, 4))
        val le = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2.toShort(), le.getShort(22))       // channels
        assertEquals(32_767.toShort(), le.getShort(46))  // first frame, right channel, full scale
    }
}
