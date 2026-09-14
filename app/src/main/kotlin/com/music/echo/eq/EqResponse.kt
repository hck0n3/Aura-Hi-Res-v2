package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The ONE definition of what an EQ band does to the sound: RBJ cookbook biquads evaluated at z = e^jω.
 *
 * The native engine (SuperpoweredBridge.cpp, `auraRbjCoefficients`) computes the very same coefficients
 * and loads them into Superpowered as custom coefficients, so the curve drawn on screen, the expected
 * values of the in-app verification and the processed audio all follow these formulas.
 *
 * Shelves take their slope from the band's Q: S = clamp(Q / 1.414, 0.05, 1). The default Q (1.414) is
 * S = 1, the steepest shelf without overshoot — exactly what the graph always drew.
 */
object EqResponse {
    const val DEFAULT_Q = 1.414

    fun shelfSlope(q: Double): Double = (q / DEFAULT_Q).coerceIn(0.05, 1.0)

    /** Magnitude in dB of one band at [f] Hz, for a stream at [sampleRate]. Disabled bands are flat. */
    fun bandMagnitudeDb(band: ParametricEQBand, f: Double, sampleRate: Double): Double {
        if (!band.enabled) return 0.0
        val c = coefficients(band.frequency, band.gain, band.q, band.filterType, sampleRate)
        val omega = 2.0 * PI * f / sampleRate
        val cw = cos(omega); val sw = sin(omega)
        val c2w = cos(2.0 * omega); val s2w = sin(2.0 * omega)
        val numRe = c[0] + c[1] * cw + c[2] * c2w
        val numIm = -(c[1] * sw + c[2] * s2w)
        val denRe = 1.0 + c[3] * cw + c[4] * c2w
        val denIm = -(c[3] * sw + c[4] * s2w)
        val mag = hypot(numRe, numIm) / hypot(denRe, denIm).coerceAtLeast(1e-12)
        return 20.0 * log10(mag.coerceAtLeast(1e-9))
    }

    /** Combined response (dB) of a cascade = sum of every band's magnitude in dB. */
    fun combinedMagnitudeDb(bands: List<ParametricEQBand>, f: Double, sampleRate: Double): Double =
        bands.sumOf { bandMagnitudeDb(it, f, sampleRate) }

    /** Highest boost (dB, >= 0) the cascade applies anywhere in 20 Hz – 20 kHz. */
    fun peakBoostDb(bands: List<ParametricEQBand>, sampleRate: Double = 48_000.0): Double {
        var peak = 0.0
        val steps = 240
        for (i in 0..steps) {
            val f = 20.0 * 1000.0.pow(i / steps.toDouble())
            peak = maxOf(peak, combinedMagnitudeDb(bands, f, sampleRate))
        }
        return peak
    }

    /** Normalized RBJ coefficients [b0, b1, b2, a1, a2] (a0 = 1). */
    fun coefficients(frequency: Double, gainDb: Double, qIn: Double, type: FilterType, sampleRate: Double): DoubleArray {
        val q = qIn.coerceAtLeast(1e-4)
        val w0 = 2.0 * PI * frequency / sampleRate
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1: Double; val a2: Double
        when (type) {
            FilterType.LSC, FilterType.HSC -> {
                val a = sqrt(10.0.pow(gainDb / 20.0))
                val s = shelfSlope(q)
                val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / s - 1.0) + 2.0)
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                if (type == FilterType.LSC) {
                    b0 = a * ((a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha)
                    b1 = 2.0 * a * ((a - 1) - (a + 1) * cosW0)
                    b2 = a * ((a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha)
                    a0 = (a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha
                    a1 = -2.0 * ((a - 1) + (a + 1) * cosW0)
                    a2 = (a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha
                } else {
                    b0 = a * ((a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha)
                    b1 = -2.0 * a * ((a - 1) + (a + 1) * cosW0)
                    b2 = a * ((a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha)
                    a0 = (a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha
                    a1 = 2.0 * ((a - 1) - (a + 1) * cosW0)
                    a2 = (a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha
                }
            }
            FilterType.LPQ, FilterType.HPQ -> {
                val alpha = sinW0 / (2.0 * q)
                val k = if (type == FilterType.LPQ) 1.0 - cosW0 else 1.0 + cosW0
                b0 = k / 2.0
                b1 = if (type == FilterType.LPQ) k else -k
                b2 = k / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
            else -> {
                val a = 10.0.pow(gainDb / 40.0)
                val alpha = sinW0 / (2.0 * q)
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW0
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha / a
            }
        }
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}
