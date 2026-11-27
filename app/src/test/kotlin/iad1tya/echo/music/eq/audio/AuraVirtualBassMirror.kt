package iad1tya.echo.music.eq.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Row 920: JVM mirror of the native psychoacoustic bass (`app/src/main/cpp/superpowered/AuraVirtualBass.h`). Same
 * constants, same filters, same order, so its maths is unit-tested here (no C++ test runs in CI). Test sources only:
 * the playback path runs the native one. `VirtualBassTest` pins that both keep the same constants.
 */
object VirtualBassConstants {
    const val CUTOFF_HZ = 150.0
    const val LOW_FLOOR_HZ = 40.0
    const val HARMONIC_TOP_HZ = 500.0
    const val SECOND_HARMONIC = 0.5
    const val THIRD_HARMONIC = 0.35
    const val RELEASE_MS = 200.0
    const val RAMP_MS = 30.0
    const val GATE = 1.0e-5
    const val MAX_EXTRA_DB = 2.0
    const val MAX_CHANNELS = 8
}

/** Mirror of `aura::VbBiquad` (RBJ, transposed direct form II, double state per channel). */
class VbBiquad private constructor(sampleRate: Double, hz: Double, q: Double, high: Boolean) {
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    private val z1 = DoubleArray(VirtualBassConstants.MAX_CHANNELS)
    private val z2 = DoubleArray(VirtualBassConstants.MAX_CHANNELS)

    init {
        val w0 = 2.0 * PI * hz / sampleRate
        val c = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        val k = if (high) (1.0 + c) / 2.0 else (1.0 - c) / 2.0
        b0 = k / a0
        b1 = (if (high) -2.0 * k else 2.0 * k) / a0
        b2 = k / a0
        a1 = -2.0 * c / a0
        a2 = (1.0 - alpha) / a0
    }

    fun run(ch: Int, x: Double): Double {
        val y = b0 * x + z1[ch]
        z1[ch] = b1 * x - a1 * y + z2[ch]
        z2[ch] = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    companion object {
        fun lowPass(sampleRate: Double, hz: Double, q: Double) = VbBiquad(sampleRate, hz, q, false)
        fun highPass(sampleRate: Double, hz: Double, q: Double) = VbBiquad(sampleRate, hz, q, true)
    }
}

/** Mirror of `aura::VirtualBass`. In place, interleaved. */
class AuraVirtualBass(sampleRate: Int) {
    private val sr = if (sampleRate > 0) sampleRate.toDouble() else 48_000.0
    private val q1 = 0.54119610014619698
    private val q2 = 1.3065629648763766
    private val qb = 0.70710678118654752
    private val speakerHp = arrayOf(
        VbBiquad.highPass(sr, VirtualBassConstants.CUTOFF_HZ, q1),
        VbBiquad.highPass(sr, VirtualBassConstants.CUTOFF_HZ, q2),
    )
    private val lowLp = arrayOf(
        VbBiquad.lowPass(sr, VirtualBassConstants.CUTOFF_HZ, q1),
        VbBiquad.lowPass(sr, VirtualBassConstants.CUTOFF_HZ, q2),
    )
    private val lowHp = VbBiquad.highPass(sr, VirtualBassConstants.LOW_FLOOR_HZ, qb)
    private val harmonicHp = VbBiquad.highPass(sr, VirtualBassConstants.CUTOFF_HZ, qb)
    private val harmonicLp = VbBiquad.lowPass(sr, VirtualBassConstants.HARMONIC_TOP_HZ, qb)
    private val release = exp(-1.0 / (sr * VirtualBassConstants.RELEASE_MS / 1000.0))
    private val rampStep = (1.0 / (sr * VirtualBassConstants.RAMP_MS / 1000.0)).toFloat()
    private var mix = 0f
    private var idle = true
    private var envelope = 0.0

    var requested = false

    fun wanted(): Boolean = requested || mix > 0f

    fun reset() {
        speakerHp.forEach { it.reset() }
        lowLp.forEach { it.reset() }
        lowHp.reset()
        harmonicHp.reset()
        harmonicLp.reset()
        envelope = 0.0
    }

    fun process(data: FloatArray, frames: Int, channels: Int) {
        if (channels < 1 || channels > VirtualBassConstants.MAX_CHANNELS) return
        val on = requested
        if (!on && mix <= 0f) return
        if (idle) { reset(); idle = false }
        val inv = 1.0 / channels
        for (i in 0 until frames) {
            if (on) { if (mix < 1f) { mix += rampStep; if (mix > 1f) mix = 1f } } else { mix -= rampStep; if (mix < 0f) mix = 0f }
            val base = i * channels
            var mono = 0.0
            for (ch in 0 until channels) mono += data[base + ch]
            mono *= inv
            val low = lowHp.run(0, lowLp[1].run(0, lowLp[0].run(0, mono)))
            val a = abs(low)
            envelope *= release
            if (a > envelope) envelope = a
            var harmonics = 0.0
            if (envelope > VirtualBassConstants.GATE) {
                val u = (low / envelope).coerceIn(-1.0, 1.0)
                val t2 = 2.0 * u * u - 1.0
                val t3 = (4.0 * u * u - 3.0) * u
                harmonics = envelope * (VirtualBassConstants.SECOND_HARMONIC * t2 + VirtualBassConstants.THIRD_HARMONIC * t3)
            }
            harmonics = harmonicLp.run(0, harmonicHp.run(0, harmonics))
            val m = mix.toDouble()
            for (ch in 0 until channels) {
                val x = data[base + ch].toDouble()
                val wet = speakerHp[1].run(ch, speakerHp[0].run(ch, x)) + harmonics
                data[base + ch] = (x + m * (wet - x)).toFloat()
            }
        }
        if (!on && mix <= 0f) idle = true
    }
}
