package iad1tya.echo.music.eq.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Row 918 — «Graves y agudos a volumen bajo». Pure maths (no Android) so it is tested on the JVM.
 *
 * At a low listening level the ear loses bass (and a little of the top) faster than the mids: the ISO 226 equal-
 * loudness contours spread apart below ~500 Hz and above ~8 kHz as the level drops. The compensation is the
 * difference between the contour at the listening level and the contour at a reference level, each normalised at
 * 1 kHz, fitted (least squares on the ISO frequencies) to two first-order shelves: 100 Hz (20-500 Hz) and 10 kHz
 * (6.3-12.5 kHz). Never a cut: a negative fit is 0. Capped at [MAX_BASS_DB] / [MAX_TREBLE_DB], then scaled by the
 * user's intensity. Exactly zero at full volume.
 *
 * ISO 226:2003 parameters (Table 1, the 29 one-third-octave frequencies 20 Hz-12.5 kHz) and its equation for the
 * sound pressure level of a contour; the code is this app's own. The 2023 revision moves the contours by up to 0.6 dB.
 */
object LoudnessCompensation {
    /** Reference listening level (phon) the music is assumed to be mastered for: full volume. */
    const val REFERENCE_PHON = 83.0
    /** Lower end of ISO 226's validity. */
    const val MIN_PHON = 20.0
    const val MAX_BASS_DB = 8.0
    const val MAX_TREBLE_DB = 3.0
    const val BASS_CORNER_HZ = 100.0
    const val TREBLE_CORNER_HZ = 10_000.0

    data class Gains(val bassDb: Double, val trebleDb: Double) {
        /** The largest boost of the two shelves: what the automatic headroom takes off (first-order shelves peak at their plateau). */
        val peakDb: Double get() = max(bassDb, trebleDb)
    }

    val ZERO = Gains(0.0, 0.0)

    internal val FREQS = doubleArrayOf(
        20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0,
        800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0,
    )
    private val AF = doubleArrayOf(
        0.532, 0.506, 0.480, 0.455, 0.432, 0.409, 0.387, 0.367, 0.349, 0.330, 0.315, 0.301, 0.288, 0.276, 0.267,
        0.259, 0.253, 0.250, 0.246, 0.244, 0.243, 0.243, 0.243, 0.242, 0.242, 0.245, 0.254, 0.271, 0.301,
    )
    private val LU = doubleArrayOf(
        -31.6, -27.2, -23.0, -19.1, -15.9, -13.0, -10.3, -8.1, -6.2, -4.5, -3.1, -2.0, -1.1, -0.4, 0.0,
        0.3, 0.5, 0.0, -2.7, -4.1, -1.0, 1.7, 2.5, 1.2, -2.1, -7.1, -11.2, -10.7, -3.1,
    )
    private val TF = doubleArrayOf(
        78.5, 68.7, 59.5, 51.1, 44.0, 37.5, 31.5, 26.5, 22.1, 17.9, 14.4, 11.4, 8.6, 6.2, 4.4,
        3.0, 2.2, 2.4, 3.5, 1.7, -1.3, -4.2, -6.0, -5.4, -1.5, 6.0, 12.6, 13.9, 12.3,
    )
    private const val INDEX_1K = 17

    /** Sound pressure level (dB SPL) of the [phon] contour at FREQS[[index]] (ISO 226:2003, equation 1). */
    internal fun contourSpl(index: Int, phon: Double): Double {
        val af = 4.47e-3 * (10.0.pow(0.025 * phon) - 1.15) +
            (0.4 * 10.0.pow((TF[index] + LU[index]) / 10.0 - 9.0)).pow(AF[index])
        return 10.0 / AF[index] * log10(af) - LU[index] + 94.0
    }

    /** How much more level FREQS[[index]] needs, relative to 1 kHz, at [listeningPhon] than at the reference. */
    internal fun differenceDb(index: Int, listeningPhon: Double): Double =
        (contourSpl(index, listeningPhon) - contourSpl(INDEX_1K, listeningPhon)) -
            (contourSpl(index, REFERENCE_PHON) - contourSpl(INDEX_1K, REFERENCE_PHON))

    /** Response (dB) of a first-order low shelf with [plateauDb] below [BASS_CORNER_HZ]. */
    fun bassShelfDb(hz: Double, plateauDb: Double): Double {
        val a = 10.0.pow(plateauDb / 20.0)
        val x = hz / BASS_CORNER_HZ
        return 10.0 * log10((a * a + x * x) / (1 + x * x))
    }

    /** Response (dB) of a first-order high shelf with [plateauDb] above [TREBLE_CORNER_HZ]. */
    fun trebleShelfDb(hz: Double, plateauDb: Double): Double {
        val a = 10.0.pow(plateauDb / 20.0)
        val x = hz / TREBLE_CORNER_HZ
        return 10.0 * log10((1 + a * a * x * x) / (1 + x * x))
    }

    private val BASS_FIT = FREQS.indices.filter { FREQS[it] <= 500.0 }
    private val TREBLE_FIT = FREQS.indices.filter { FREQS[it] >= 6300.0 }

    /** Least-squares plateau (0..30 dB, 0.05 dB grid) of [shelf] against the contour difference at [listeningPhon]. */
    private fun fit(indices: List<Int>, listeningPhon: Double, shelf: (Double, Double) -> Double): Double {
        val target = indices.map { differenceDb(it, listeningPhon) }
        var best = 0.0
        var bestErr = Double.MAX_VALUE
        for (k in 0..600) {
            val p = k * 0.05
            var err = 0.0
            indices.forEachIndexed { j, i ->
                val e = shelf(FREQS[i], p) - target[j]
                err += e * e
            }
            if (err < bestErr) { bestErr = err; best = p }
        }
        return best
    }

    /**
     * Shelf gains for a system volume of [volumeDb] (0 = full volume, negative below; -infinity or NaN = muted) and an
     * intensity in [0, 1]. Listening level = [REFERENCE_PHON] + [volumeDb], kept within ISO 226's range.
     */
    fun gains(volumeDb: Double, intensity: Double): Gains {
        val strength = if (intensity.isNaN()) 0.0 else intensity.coerceIn(0.0, 1.0)
        if (strength == 0.0 || volumeDb >= 0.0) return ZERO
        val listening = if (volumeDb.isNaN()) MIN_PHON else (REFERENCE_PHON + volumeDb).coerceIn(MIN_PHON, REFERENCE_PHON)
        if (listening >= REFERENCE_PHON) return ZERO
        val bass = minOf(fit(BASS_FIT, listening, ::bassShelfDb), MAX_BASS_DB) * strength
        val treble = minOf(fit(TREBLE_FIT, listening, ::trebleShelfDb), MAX_TREBLE_DB) * strength
        return Gains(bass, treble)
    }

    /** Above this the platform's volume curve is considered flat (the volume is applied elsewhere, e.g. absolute Bluetooth volume). */
    const val FLAT_CURVE_DB = -0.5

    /**
     * The attenuation (dB, <= 0) of system volume [index] of [max]. [routedDb] is the platform's value for the current
     * output, [speakerDb] for the phone speaker at the same index (`AudioManager.getStreamVolumeDb`); null when
     * unavailable. Full volume is 0, muted is -infinity; a flat or missing routed curve falls back to the speaker curve,
     * and with neither the result is 0 (no compensation).
     */
    fun volumeDbFor(index: Int, max: Int, routedDb: Double?, speakerDb: Double?): Double {
        if (max <= 0 || index >= max) return 0.0
        if (index <= 0) return Double.NEGATIVE_INFINITY
        if (routedDb != null && routedDb.isFinite() && routedDb < FLAT_CURVE_DB) return routedDb
        if (speakerDb != null && speakerDb.isFinite()) return speakerDb.coerceAtMost(0.0)
        return 0.0
    }

    /** What the native stage receives: the two boosts and, with the automatic headroom on, a trim of their peak. */
    fun trimDb(gains: Gains, autoHeadroom: Boolean): Double = if (autoHeadroom) -gains.peakDb else 0.0
}
