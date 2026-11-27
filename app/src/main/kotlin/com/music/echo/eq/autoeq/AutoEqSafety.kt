package iad1tya.echo.music.eq.autoeq

import iad1tya.echo.music.eq.EqResponse
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import kotlin.math.ln
import kotlin.math.pow

/**
 * The safety transform for the AUTOMATIC per-device correction (registry row 915, research §2.1 of
 * docs/plan/AUDIO_INVESTIGACION_2026-11-27.md). A profile the user picks by hand in the Auto-EQ list still plays exactly
 * as AutoEq wrote it (row 792); only the correction Aura switches on by itself goes through here.
 *
 *  1. **No correction above ~10 kHz.** Above it a measurement depends on how the cup or tip sits, and a boost there
 *     lifts exactly the band where Opus leaves its artefacts. The target is the AutoEq curve faded out: unchanged up to
 *     [FADE_START_HZ], zero from [FADE_END_HZ] (half an octave higher), linear in octaves between ([highFrequencyWeight]).
 *     Biquads cannot multiply a curve, so: a peak centred in the fade is weighed by where it sits (gone from 14.1 kHz),
 *     and two high shelves at [COMPENSATION_HZ] are fitted (least squares over 10–20 kHz, 1/12 octave) to cancel what
 *     the remaining bands — AutoEq's own 10 kHz shelf included — still do up there. Measured with a line-by-line Python
 *     port of this code on 150 random catalogue entries (2026-11-27): what is left above 14.1 kHz is ≤ 0.4 dB for half
 *     of them and ≤ 1.8 dB for 99 % (worst 2.3 dB); below 8 kHz the curve moves ≤ 1 dB for 90 % of them.
 *  2. **Boosts capped at [maxBoostDb]** (default +6 dB) for the cascade as it plays ([EqResponse.peakBoostDb], the same
 *     curve the engine computes). When the highest point is above the cap the WHOLE correction is scaled down (cuts
 *     too): scaling only the boosts would break AutoEq's overlapping boost/cut pairs and change the curve's shape.
 *  3. Shelf Q is converted to the engine's slope exactly as [toAutoBands] does (row 792).
 */
object AutoEqSafety {
    const val DEFAULT_MAX_BOOST_DB = 6.0
    const val FADE_START_HZ = 10_000.0
    const val FADE_END_HZ = 14_142.0
    val COMPENSATION_HZ = doubleArrayOf(11_000.0, 15_000.0)
    private const val CAP_TOLERANCE_DB = 0.05
    private const val FIT_SAMPLE_RATE = 48_000.0

    /** 10 kHz to 20 kHz in 1/12 octave: where the fade is fitted. */
    val FIT_HZ: DoubleArray = (0..12).map { FADE_START_HZ * 2.0.pow(it / 12.0) }.filter { it <= 20_000.0 }.toDoubleArray()

    /** Weight (0..1) of the correction at [hz]. */
    fun highFrequencyWeight(hz: Double): Double = when {
        hz <= FADE_START_HZ -> 1.0
        hz >= FADE_END_HZ -> 0.0
        else -> 1.0 - ln(hz / FADE_START_HZ) / ln(FADE_END_HZ / FADE_START_HZ)
    }

    /** The automatic correction for [profile]: faded above 10 kHz and with its highest boost within [maxBoostDb]. */
    fun safeBands(profile: AutoEqProfile, maxBoostDb: Double = DEFAULT_MAX_BOOST_DB): List<ParametricEQBand> {
        val cap = maxBoostDb.coerceAtLeast(0.0)
        val base = profile.bands.mapNotNull { b ->
            if (b.type != FilterType.PK) return@mapNotNull b
            val w = highFrequencyWeight(b.fc)
            if (w <= 0.0) null else b.copy(gainDb = b.gainDb * w)
        }
        var scale = 1.0
        var result = emptyList<ParametricEQBand>()
        repeat(8) {
            val bands = AutoEqProfile(0.0, base.map { it.copy(gainDb = it.gainDb * scale) }).toAutoBands()
                .filter { it.gain != 0.0 }
            result = bands + fadeCompensation(bands)
            val peak = EqResponse.peakBoostDb(result)
            if (peak <= cap + CAP_TOLERANCE_DB) return result
            scale *= cap / peak
        }
        return result
    }

    /**
     * Two high shelves (engine slope S = 1) whose gains — coordinate descent, 0.1 dB steps within ±15 dB, three rounds —
     * bring [bands] closest to the faded target over [FIT_HZ]; above [FADE_END_HZ] the error counts double. Empty when
     * no shelf helps.
     */
    fun fadeCompensation(bands: List<ParametricEQBand>): List<ParametricEQBand> {
        val r = FIT_HZ.map { EqResponse.combinedMagnitudeDb(bands, it, FIT_SAMPLE_RATE) }
        val target = FIT_HZ.mapIndexed { i, f -> r[i] * highFrequencyWeight(f) }
        val weight = FIT_HZ.map { if (it >= FADE_END_HZ) 2.0 else 1.0 }
        val gains = DoubleArray(COMPENSATION_HZ.size)
        val contrib = Array(COMPENSATION_HZ.size) { DoubleArray(FIT_HZ.size) }
        fun shelf(corner: Double, gain: Double) =
            ParametricEQBand(corner, gain, EqResponse.DEFAULT_Q, FilterType.HSC, enabled = true)
        repeat(3) {
            for (s in COMPENSATION_HZ.indices) {
                val others = DoubleArray(FIT_HZ.size) { i -> contrib.indices.filter { it != s }.sumOf { contrib[it][i] } }
                var bestErr = Double.MAX_VALUE
                var bestGain = 0.0
                var bestContrib = DoubleArray(FIT_HZ.size)
                for (k in -150..150) {
                    val g = k / 10.0
                    val c = DoubleArray(FIT_HZ.size) { i ->
                        if (k == 0) 0.0 else EqResponse.bandMagnitudeDb(shelf(COMPENSATION_HZ[s], g), FIT_HZ[i], FIT_SAMPLE_RATE)
                    }
                    var err = 0.0
                    for (i in FIT_HZ.indices) {
                        val e = r[i] + others[i] + c[i] - target[i]
                        err += weight[i] * e * e
                    }
                    if (err < bestErr) {
                        bestErr = err
                        bestGain = g
                        bestContrib = c
                    }
                }
                gains[s] = bestGain
                contrib[s] = bestContrib
            }
        }
        return COMPENSATION_HZ.indices.filter { gains[it] != 0.0 }.map { shelf(COMPENSATION_HZ[it], gains[it]) }
    }

    /**
     * The master preamp to use with the automatic correction. THE SEAM with the automatic headroom (registry row 915 and
     * package G1): when «Headroom automático» is on, `CustomEqualizerAudioProcessor.effectivePreampDb` already lowers the
     * preamp by [EqResponse.peakBoostDb] of the WHOLE cascade (Auto-EQ + manual bands) — subtracting it here as well would
     * cut twice, so this returns 0. When it is off, it returns −(highest boost of that cascade), the AutoEq convention
     * (AutoEq writes «Preamp: −max boost»), so nothing reaches the limiter because of the EQ. If the automatic headroom
     * ever moves elsewhere, this function is the one place to follow it.
     */
    fun devicePreampDb(cascade: List<ParametricEQBand>, autoHeadroomOn: Boolean): Double =
        if (autoHeadroomOn) 0.0 else -EqResponse.peakBoostDb(cascade.filter { it.enabled })
}
