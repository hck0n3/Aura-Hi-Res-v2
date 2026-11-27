package iad1tya.echo.music.eq.audio

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Row 917: JVM mirror of `app/src/main/cpp/superpowered/AuraDeEsser.h`, line for line (same constants, same order of
 * operations, float in and out, double inside). [DeEsserTest] pins the constants to the header.
 */
class AuraDeEsserMirror(sampleRate: Int) {
    companion object {
        const val BAND_LOW_HZ = 5000.0
        const val BAND_HIGH_HZ = 9000.0
        const val KEY_HIGH_PASS_HZ = 200.0
        const val THRESHOLD_DB = -12.0
        const val SLOPE = 0.75
        const val MAX_REDUCTION_DB = 6.0
        const val ATTACK_MS = 2.0
        const val RELEASE_MS = 80.0
        const val FLOOR_POWER = 1e-6
        const val CONTROL_FRAMES = 32
        const val MAX_CHANNELS = 8
    }

    class Biquad {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        val z1 = DoubleArray(MAX_CHANNELS)
        val z2 = DoubleArray(MAX_CHANNELS)

        fun design(kind: Int, fs: Double, f0In: Double, q: Double) {
            var f0 = f0In
            if (f0 > fs * 0.45) f0 = fs * 0.45
            val w0 = 2.0 * 3.14159265358979323846 * f0 / fs
            val c = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            when (kind) {
                0 -> { b0 = (1.0 - c) / 2.0; b1 = 1.0 - c; b2 = (1.0 - c) / 2.0 }
                1 -> { b0 = (1.0 + c) / 2.0; b1 = -(1.0 + c); b2 = (1.0 + c) / 2.0 }
                else -> { b0 = alpha; b1 = 0.0; b2 = -alpha }
            }
            b0 /= a0; b1 /= a0; b2 /= a0
            a1 = -2.0 * c / a0
            a2 = (1.0 - alpha) / a0
        }

        fun run(ch: Int, x: Double): Double {
            val y = b0 * x + z1[ch]
            z1[ch] = b1 * x - a1 * y + z2[ch]
            z2[ch] = b2 * x - a2 * y
            return y
        }

        fun clear() { z1.fill(0.0); z2.fill(0.0) }

        /** |H| at [hz] for [fs], for the response checks. */
        fun magnitude(hz: Double, fs: Double): Double {
            val w = 2 * PI * hz / fs
            val nr = b0 + b1 * cos(w) + b2 * cos(2 * w)
            val ni = -(b1 * sin(w) + b2 * sin(2 * w))
            val dr = 1 + a1 * cos(w) + a2 * cos(2 * w)
            val di = -(a1 * sin(w) + a2 * sin(2 * w))
            return sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
        }
    }

    private val usable: Boolean
    val hpA = Biquad(); val hpB = Biquad(); val lp = Biquad(); val ref = Biquad(); val band = Biquad()
    private val attack: Double
    private val release: Double
    private var envKey = 0.0
    private var envRef = 0.0
    var gain = 1.0
        private set
    private var target = 1.0
    private var step = 0.0
    private var rampLeft = 0
    private var countdown = CONTROL_FRAMES

    init {
        val fs = sampleRate.toDouble()
        usable = fs >= 22050.0
        val pi = 3.14159265358979323846
        val wl = tan(pi * BAND_LOW_HZ / fs)
        val wh = tan(pi * BAND_HIGH_HZ / fs)
        val w0 = sqrt(wl * wh)
        hpA.design(1, fs, BAND_LOW_HZ, 0.54119610014619698)
        hpB.design(1, fs, BAND_LOW_HZ, 1.3065629648763766)
        lp.design(0, fs, BAND_HIGH_HZ, 0.70710678118654752)
        ref.design(1, fs, KEY_HIGH_PASS_HZ, 0.70710678118654752)
        band.design(2, fs, atan(w0) * fs / pi, w0 / (wh - wl))
        attack = 1.0 - exp(-2.0 / (fs * ATTACK_MS / 1000.0))
        release = 1.0 - exp(-2.0 / (fs * RELEASE_MS / 1000.0))
        reset()
    }

    fun reset() {
        hpA.clear(); hpB.clear(); lp.clear(); ref.clear(); band.clear()
        envKey = 0.0; envRef = 0.0
        gain = 1.0; target = 1.0; step = 0.0; rampLeft = 0
        countdown = CONTROL_FRAMES
    }

    /** In place, interleaved [data] of [frames] frames. */
    fun process(data: FloatArray, offsetFrames: Int, frames: Int, channels: Int) {
        if (!usable || channels < 1 || channels > MAX_CHANNELS) return
        val inv = 1.0 / channels
        val bp = DoubleArray(MAX_CHANNELS)
        for (f in 0 until frames) {
            val base = (offsetFrames + f) * channels
            var pKey = 0.0
            var pRef = 0.0
            for (ch in 0 until channels) {
                val x = data[base + ch].toDouble()
                val k = lp.run(ch, hpB.run(ch, hpA.run(ch, x)))
                val r = ref.run(ch, x)
                pKey += k * k
                pRef += r * r
                bp[ch] = band.run(ch, x)
            }
            pKey *= inv
            pRef *= inv
            envKey += (pKey - envKey) * (if (pKey > envKey) attack else release)
            envRef += (pRef - envRef) * (if (pRef > envRef) attack else release)

            if (--countdown == 0) {
                countdown = CONTROL_FRAMES
                var reductionDb = 0.0
                if (envKey > FLOOR_POWER && envRef > 0.0) {
                    val ratioDb = 10.0 * log10(envKey / envRef)
                    reductionDb = (ratioDb - THRESHOLD_DB) * SLOPE
                    if (reductionDb > MAX_REDUCTION_DB) reductionDb = MAX_REDUCTION_DB
                }
                val next = if (reductionDb > 0.0) 10.0.pow(-reductionDb / 20.0) else 1.0
                if (next != target || gain != target) {
                    target = next
                    step = (target - gain) / CONTROL_FRAMES
                    rampLeft = CONTROL_FRAMES
                }
            }
            if (rampLeft > 0) {
                gain += step
                if (--rampLeft == 0) gain = target
            }
            if (gain != 1.0) {
                val cut = 1.0 - gain
                for (ch in 0 until channels) data[base + ch] = (data[base + ch].toDouble() - cut * bp[ch]).toFloat()
            }
        }
        if (!envKey.isFinite() || !envRef.isFinite() || !gain.isFinite()) reset()
    }
}
