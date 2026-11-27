package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.audio.TIDAL_SIGNATURE_MODEL_BANDS
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.ParametricEQBand
import kotlin.math.log10
import kotlin.math.pow

/**
 * Row 921 — level match for «Comparar A/B»: how much louder (or quieter) the processed sound A is than the reference
 * B (equalizer and its effects bypassed), so B can be played that much louder and the louder one does not win the
 * comparison just by being louder (research §0 and §3, `docs/plan/AUDIO_INVESTIGACION_2026-11-27.md`).
 *
 * Method: the level change the curve gives PINK NOISE, the usual stand-in for a typical music spectrum (equal energy
 * per octave). Pink noise has the same power in every octave, so its power after the curve is the mean of |H(f)|²
 * over a LOGARITHMIC frequency axis; in dB:
 *
 *     L = 10 · log10( mean over log f in [20 Hz, 20 kHz] of 10^(H_dB(f) / 10) )
 *
 * with H_dB the combined response of every band that sounds ([EqResponse.combinedMagnitudeDb], the same RBJ formulas
 * the native biquads run). Evaluated with the midpoint rule on [STEPS] log-spaced points (1/24 octave): against a
 * 200 000-point integral of the cookbook formulas the error is below 0.001 dB. The preamp the chain applies (the
 * user's, or the automatic headroom's) adds straight on top, so A's level is `preamp + L`.
 *
 * What it does NOT model, on purpose: the spatial stage (its level depends on how much the song's channels differ)
 * and the de-esser (dynamic, only on loud sibilants). Safe Volume, the true-peak limiter, stereo width and the glue
 * compressor run identically in A and B, so they cancel out.
 */
object EqLevelMatch {
    const val F_LOW = 20.0
    const val F_HIGH = 20_000.0
    const val STEPS = 240

    /** Pink-noise level change (dB) of the cascade [bands]: 0 for a flat curve. */
    fun pinkLevelDb(bands: List<ParametricEQBand>, sampleRate: Double = 48_000.0): Double {
        val active = bands.filter { it.enabled }
        if (active.isEmpty()) return 0.0
        var sum = 0.0
        for (i in 0 until STEPS) {
            val f = F_LOW * (F_HIGH / F_LOW).pow((i + 0.5) / STEPS)
            sum += 10.0.pow(EqResponse.combinedMagnitudeDb(active, f, sampleRate) / 10.0)
        }
        return 10.0 * log10(sum / STEPS)
    }

    /**
     * Gain (dB) for the reference B so it plays at A's level: A's preamp ([effectivePreampDb], automatic headroom
     * included) plus the pink-noise level of A's curve ([activeBands], and the Tidal signature while it sounds).
     * Bounded to the preamp range the engine is driven with.
     */
    fun referenceGainDb(
        activeBands: List<ParametricEQBand>,
        tidalSignatureOn: Boolean,
        effectivePreampDb: Double,
        sampleRate: Double = 48_000.0,
    ): Double {
        val curve = if (tidalSignatureOn) activeBands + TIDAL_SIGNATURE_MODEL_BANDS else activeBands
        return (effectivePreampDb + pinkLevelDb(curve, sampleRate))
            .coerceIn(EqConstants.PREAMP_MIN.toDouble(), EqConstants.PREAMP_MAX.toDouble())
    }
}
