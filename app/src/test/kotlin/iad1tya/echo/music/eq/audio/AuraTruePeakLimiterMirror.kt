package iad1tya.echo.music.eq.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Row 911: JVM mirror of the native true-peak limiter (`app/src/main/cpp/superpowered/AuraTruePeakLimiter.h`), the
 * last stage of the Superpowered chain. Same constants, same steps, same order, so the limiter's maths is unit-tested
 * here (no C++ test runs in CI). Test sources only: the playback path runs the native one.
 * `TruePeakLimiterTest` pins that both files keep the same constants.
 */
object TruePeakLimiterConstants {
    const val CEILING_DBTP = -1.0
    const val LOOKAHEAD_MS = 1.5
    const val RELEASE_MS = 100.0
    const val SAFETY_CLAMP = 0.989f
    const val OVERSAMPLE = 4
    const val TAPS = 32
    const val CENTRE = 15

    /** Windowed-sinc (Blackman) coefficients of the OVERSAMPLE-1 points between h[CENTRE] and h[CENTRE+1]. */
    fun interpolatorCoefficients(): Array<DoubleArray> = Array(OVERSAMPLE - 1) { pi ->
        val p = pi + 1
        val frac = p.toDouble() / OVERSAMPLE
        val c = DoubleArray(TAPS) { k ->
            val t = (k - CENTRE).toDouble() - frac
            val sinc = if (abs(t) < 1e-12) 1.0 else sin(PI * t) / (PI * t)
            val x = t / (TAPS / 2.0)
            sinc * (0.42 + 0.5 * cos(PI * x) + 0.08 * cos(2.0 * PI * x))
        }
        val sum = c.sum()
        DoubleArray(TAPS) { c[it] / sum }
    }
}

/** True peak (linear) of interleaved audio, 4x reconstructed — mirror of `aura::TruePeakMeter`. */
class TruePeakMeter(private val channels: Int) {
    private val coef = TruePeakLimiterConstants.interpolatorCoefficients()
    private val taps = TruePeakLimiterConstants.TAPS
    private val hist = Array(channels) { FloatArray(2 * taps) }
    private var pos = 0
    var peakLinear = 0.0
        private set

    fun process(data: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val base = pos + 1
            for (ch in 0 until channels) {
                val v = data[i * channels + ch]
                hist[ch][pos] = v
                hist[ch][pos + taps] = v
                var m = abs(hist[ch][base + TruePeakLimiterConstants.CENTRE + 1].toDouble())
                for (p in coef.indices) {
                    var y = 0.0
                    for (k in 0 until taps) y += coef[p][k] * hist[ch][base + k]
                    if (abs(y) > m) m = abs(y)
                }
                if (m > peakLinear) peakLinear = m
            }
            pos = (pos + 1) % taps
        }
    }
}

/** Mirror of `aura::TruePeakLimiter`: linked true-peak lookahead limiter, ceiling -1 dBTP. In place, interleaved. */
class AuraTruePeakLimiter(sampleRate: Int, private val channels: Int) {
    private val taps = TruePeakLimiterConstants.TAPS
    private val centre = TruePeakLimiterConstants.CENTRE
    private val coef = TruePeakLimiterConstants.interpolatorCoefficients()
    private val fs = if (sampleRate < 8000) 48_000.0 else sampleRate.toDouble()
    val lookahead: Int = (fs * TruePeakLimiterConstants.LOOKAHEAD_MS / 1000.0).roundToLong().coerceIn(1L, MAX_LOOKAHEAD.toLong()).toInt()
    private val releaseCoef = 1.0 - exp(-1.0 / (fs * TruePeakLimiterConstants.RELEASE_MS / 1000.0))
    val ceiling: Double = 10.0.pow(TruePeakLimiterConstants.CEILING_DBTP / 20.0)
    private val quietThreshold = ceiling / maxOf(1.0, coef.maxOf { row -> row.sumOf { abs(it) } })

    /** Detection delay (TAPS - 1 - CENTRE) + lookahead - 1. */
    val latencyFrames: Int = (taps - 1 - centre) + lookahead - 1

    private val hist = Array(channels) { FloatArray(2 * taps) }
    private var histPos = 0
    private val delayCapacity = MAX_LOOKAHEAD + taps
    private val delay = Array(delayCapacity) { FloatArray(channels) }
    private var delayPos = 0
    private var prevInterval = 0.0
    private var framesSinceLoud = taps
    private val dequeCapacity = MAX_LOOKAHEAD + 1
    private val dqVal = DoubleArray(dequeCapacity)
    private val dqIdx = LongArray(dequeCapacity)
    private var dqHead = 0
    private var dqCount = 0
    private val rRing = DoubleArray(MAX_LOOKAHEAD) { 1.0 }
    private var rSum = lookahead.toDouble()
    private var rPos = 0
    private var rPrev = 1.0
    private var counter = 0L

    /** Last gain applied (1.0 = untouched). */
    var lastGain = 1.0
        private set

    fun process(data: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val off = i * channels
            val base = histPos + 1
            var newestAbs = 0f
            for (ch in 0 until channels) {
                val v = data[off + ch]
                hist[ch][histPos] = v
                hist[ch][histPos + taps] = v
                delay[delayPos][ch] = v
                if (abs(v) > newestAbs) newestAbs = abs(v)
            }
            histPos = (histPos + 1) % taps
            if (newestAbs.toDouble() > quietThreshold) framesSinceLoud = 0
            else if (framesSinceLoud < taps) framesSinceLoud++

            var interval = 0.0
            if (framesSinceLoud < taps) {
                for (ch in 0 until channels) {
                    val h = hist[ch]
                    var m = maxOf(abs(h[base + centre].toDouble()), abs(h[base + centre + 1].toDouble()))
                    for (p in coef.indices) {
                        var y = 0.0
                        for (k in 0 until taps) y += coef[p][k] * h[base + k]
                        if (abs(y) > m) m = abs(y)
                    }
                    if (m > interval) interval = m
                }
            }
            val samplePeak = if (interval > prevInterval) interval else prevInterval
            prevInterval = interval
            val greq = if (samplePeak > ceiling) ceiling / samplePeak else 1.0

            while (dqCount > 0) {
                val back = (dqHead + dqCount - 1) % dequeCapacity
                if (dqVal[back] >= greq) dqCount-- else break
            }
            val slot = (dqHead + dqCount) % dequeCapacity
            dqVal[slot] = greq
            dqIdx[slot] = counter
            dqCount++
            while (dqCount > 0 && dqIdx[dqHead] <= counter - lookahead) {
                dqHead = (dqHead + 1) % dequeCapacity
                dqCount--
            }
            val hold = dqVal[dqHead]

            var r = if (hold < rPrev) hold else rPrev + (hold - rPrev) * releaseCoef
            if (hold >= 1.0 && 1.0 - r < 1e-7) r = 1.0
            rPrev = r

            rSum += r - rRing[rPos]
            rRing[rPos] = r
            if (++rPos == lookahead) {
                rPos = 0
                var s = 0.0
                for (k in 0 until lookahead) s += rRing[k]
                rSum = s
            }
            var g = rSum / lookahead
            if (g > 1.0) g = 1.0
            lastGain = g

            val readPos = (delayPos - latencyFrames + delayCapacity) % delayCapacity
            for (ch in 0 until channels) {
                data[off + ch] = if (g == 1.0) delay[readPos][ch] else (delay[readPos][ch].toDouble() * g).toFloat()
            }
            delayPos = (delayPos + 1) % delayCapacity
            counter++
        }
    }

    companion object {
        const val MAX_LOOKAHEAD = 2048
    }
}
