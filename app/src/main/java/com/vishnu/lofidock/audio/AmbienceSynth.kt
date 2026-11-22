package com.vishnu.lofidock.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural rain-on-glass and vinyl-surface noise. Generated sample by
 * sample, so the app ships no ambience audio and the sound never loops.
 * The same DSP as the iOS app.
 *
 * [render] runs on the audio thread inside a tight loop: no allocation, no
 * locks. The levels are @Volatile because the UI thread writes them.
 */
class AmbienceSynth(sampleRate: Int, seed: Long = 0x51EE7A1D0C0FFEE5uL.toLong()) {
    /** Loudness targets (0..1); the render loop glides toward them. */
    @Volatile var rainLevel = 0f
    @Volatile var vinylLevel = 0f

    val sampleRate: Float = sampleRate.toFloat()
    private val rng = NoiseRandom(seed)
    private val rainLeft = RainVoice(this.sampleRate, swellPhase = 0f)
    private val rainRight = RainVoice(this.sampleRate, swellPhase = 2.1f)
    private val vinyl = VinylVoice(this.sampleRate)
    private var rainGain = 0f
    private var vinylGain = 0f
    private val glide = 1f - exp(-1f / (this.sampleRate * GLIDE_SECONDS))

    /** Drops both sources to silence at once, so the next start fades in. */
    fun silence() {
        rainGain = 0f
        vinylGain = 0f
    }

    /** Writes [frames] stereo frames into [out], interleaved L, R. */
    fun render(frames: Int, out: FloatArray) {
        val rainTarget = rainLevel
        val vinylTarget = vinylLevel
        for (i in 0 until frames) {
            rainGain += (rainTarget - rainGain) * glide
            vinylGain += (vinylTarget - vinylGain) * glide
            var l = 0f
            var r = 0f
            if (rainGain > 1e-4f) {
                l += rainLeft.next(rng) * rainGain
                r += rainRight.next(rng) * rainGain
            }
            if (vinylGain > 1e-4f) {
                val v = vinyl.next(rng) * vinylGain
                l += v
                r += v
            }
            out[2 * i] = softClip(l)
            out[2 * i + 1] = softClip(r)
        }
    }

    companion object {
        /** Time constant of fades between levels. */
        const val GLIDE_SECONDS = 0.3f
    }
}

/** One channel of rain: pink-noise hiss, a soft low body, sparse close drops. */
private class RainVoice(sampleRate: Float, private var swellPhase: Float) {
    private val pink = PinkNoise()
    private val hissHighPass = OnePole(350f, sampleRate)
    private val hissLowPass = OnePole(5_500f, sampleRate)
    private var brown = 0f
    private val bodyLowPass = OnePole(180f, sampleRate)
    private val dropHighPass = OnePole(2_500f, sampleRate)
    private var drop = 0f
    private val swellStep = (2 * PI * 0.07 / sampleRate).toFloat()   // thickens and thins every ~14 s
    private val dropChance = 22f / sampleRate                          // ~22 close drops a second
    private val dropDecay = exp(-1f / (sampleRate * 0.003f))           // each rings ~3 ms

    fun next(rng: NoiseRandom): Float {
        val hiss = hissLowPass.lowPass(hissHighPass.highPass(pink.next(rng.bipolar())))
        brown = (brown + 0.02f * rng.bipolar()) / 1.02f
        val body = bodyLowPass.lowPass(brown)
        if (rng.unit() < dropChance) {
            val a = rng.unit()
            drop = 0.1f + 0.5f * a * a
        }
        val tick = dropHighPass.highPass(rng.bipolar()) * drop
        drop *= dropDecay
        swellPhase += swellStep
        if (swellPhase > 2 * PI.toFloat()) swellPhase -= 2 * PI.toFloat()
        val swell = 0.85f + 0.15f * sin(swellPhase)
        return (hiss * 1.0f + body * 1.5f + tick * 0.3f) * swell
    }
}

/** Record surface noise: faint hiss, tiny ticks, the odd pop, a scratch every turn. */
private class VinylVoice(sampleRate: Float) {
    private val hissHighPass = OnePole(3_000f, sampleRate)
    private val popLowPass = OnePole(2_200f, sampleRate)
    private val rumbleLowPass = OnePole(45f, sampleRate)
    private var tick = 0f
    private var pop = 0f
    private var turn = 0f
    private val turnStep = (100f / 3f) / 60f / sampleRate   // 33⅓ rpm
    private val tickChance = 14f / sampleRate
    private val popChance = 0.5f / sampleRate
    private val tickDecay = exp(-1f / (sampleRate * 0.0003f))
    private val popDecay = exp(-1f / (sampleRate * 0.0018f))

    fun next(rng: NoiseRandom): Float {
        if (rng.unit() < tickChance) {
            val a = rng.unit()
            tick = 0.05f + 0.4f * a * a * a
        }
        if (rng.unit() < popChance) pop = 0.4f + 0.4f * rng.unit()
        turn += turnStep
        if (turn >= 1f) {
            turn -= 1f
            pop = maxOf(pop, 0.25f)
        }
        val hiss = hissHighPass.highPass(rng.bipolar())
        val crackle = tick * rng.bipolar() + popLowPass.lowPass(pop * rng.bipolar()) * 1.6f
        tick *= tickDecay
        pop *= popDecay
        val rumble = rumbleLowPass.lowPass(rng.bipolar())
        return hiss * 0.015f + crackle + rumble * 0.6f
    }
}

/** xorshift64: fast, allocation-free noise for the audio thread. */
class NoiseRandom(seed: Long) {
    private var state = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    fun next(): Long {
        state = state xor (state shl 13)
        state = state xor (state ushr 7)
        state = state xor (state shl 17)
        return state
    }

    /** Uniform in 0 until 1. */
    fun unit(): Float = (next() ushr 40).toFloat() * (1f / 16_777_216f)

    /** Uniform in -1 until 1. */
    fun bipolar(): Float = unit() * 2f - 1f
}

/** Paul Kellet's refined pink-noise filter (-3 dB/octave from white). */
private class PinkNoise {
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f; private var b3 = 0f
    private var b4 = 0f; private var b5 = 0f; private var b6 = 0f

    fun next(white: Float): Float {
        b0 = 0.99886f * b0 + white * 0.0555179f
        b1 = 0.99332f * b1 + white * 0.0750759f
        b2 = 0.96900f * b2 + white * 0.1538520f
        b3 = 0.86650f * b3 + white * 0.3104856f
        b4 = 0.55000f * b4 + white * 0.5329522f
        b5 = -0.7616f * b5 - white * 0.0168980f
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
        b6 = white * 0.115926f
        return pink * 0.11f
    }
}

/** One-pole filter; each instance is used as either a low-pass or a high-pass. */
private class OnePole(cutoff: Float, sampleRate: Float) {
    private val a = 1f - exp(-2f * PI.toFloat() * cutoff / sampleRate)
    private var z = 0f

    fun lowPass(x: Float): Float {
        z += a * (x - z)
        return z
    }

    fun highPass(x: Float): Float = x - lowPass(x)
}

/** Smooth limiter (Padé tanh), exactly ±1 at ±3, so stacked peaks never clip hard. */
private fun softClip(x: Float): Float {
    val c = x.coerceIn(-3f, 3f)
    return c * (27f + c * c) / (27f + 9f * c * c)
}
