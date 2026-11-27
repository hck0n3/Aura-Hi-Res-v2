package iad1tya.echo.music.eq.audio

import iad1tya.echo.music.eq.EqResponse
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Pure audio-gain helpers (no Android/media3) so they can be unit-tested on the JVM.
 *
 * The signal path never hard-clips: loud masters are attenuated to a reference, and quiet tracks are
 * brought UP toward that reference (loudness target, TIDAL-style) by a bounded makeup gain. The EQ
 * preamp, the bands and that makeup can push peaks past full scale; the native chain in
 * SuperpoweredBridge.cpp catches them with its true-peak limiter (ceiling -1 dBTP, row 911) and a hard clamp
 * at -0.1 dBFS that never lets a sample reach 1.0.
 */

/**
 * Effective EQ pre-amp (dB): the user's pre-amp minus the largest positive band gain (plus 1 dB
 * safety when any band boosts), so a boosted band can never push the signal above its original
 * level and hard-clip. Mirrors the AutoEq/Wavelet convention.
 */
fun headroomPreampDb(userPreampDb: Double, enabledBandGainsDb: List<Double>): Double {
    val maxBoost = (enabledBandGainsDb.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
    return userPreampDb - maxBoost - if (maxBoost > 0.0) 1.0 else 0.0
}

/**
 * Row 911: the native «firma Tidal» stage (the `tidal*` filters built in the SuperpoweredProcessor constructor of
 * SuperpoweredBridge.cpp) as RBJ bands with the same frequencies and gains: 60 Hz low shelf +1 dB (slope 0.5), 250 Hz
 * peak -0.5 dB (1 octave), 3 kHz peak +0.5 dB (1.2 octaves), 12 kHz high shelf +1.5 dB (slope 0.5); its 6.5 kHz
 * filter is at 0 dB. Superpowered's own filter formulas are not published, so this is a MODEL used only to decide the
 * automatic headroom (`EqResponse.shelfSlope`: slope 0.5 is Q 0.707; 1.2 octaves is Q 1.17).
 */
val TIDAL_SIGNATURE_MODEL_BANDS: List<ParametricEQBand> = listOf(
    ParametricEQBand(frequency = 60.0, gain = 1.0, q = 0.707, filterType = FilterType.LSC),
    ParametricEQBand(frequency = 250.0, gain = -0.5, q = 1.414, filterType = FilterType.PK),
    ParametricEQBand(frequency = 3000.0, gain = 0.5, q = 1.17, filterType = FilterType.PK),
    ParametricEQBand(frequency = 12000.0, gain = 1.5, q = 0.707, filterType = FilterType.HSC),
)

/**
 * Row 911: the preamp «Headroom automático» applies: the user's preamp minus the highest boost of the WHOLE combined
 * curve the chain will run — the AutoEQ and manual bands together and, while it sounds, the Tidal signature
 * ([TIDAL_SIGNATURE_MODEL_BANDS]) — measured on the combined response ([EqResponse.peakBoostDb]), not band by band.
 * So the curve itself never pushes the signal above where the user's preamp puts it (its 12 kHz shelf on top of a
 * treble boost included). The user's preamp still applies on top, unchanged (row 790).
 */
fun autoHeadroomPreampDb(
    userPreampDb: Double,
    activeBands: List<ParametricEQBand>,
    tidalSignatureOn: Boolean,
): Double = userPreampDb - EqResponse.peakBoostDb(if (tidalSignatureOn) activeBands + TIDAL_SIGNATURE_MODEL_BANDS else activeBands)

/**
 * Attenuate-only loudness-normalization multiplier (linear, in (0, 1]). [loudnessDb] is the stream's
 * loudness relative to the reference (YouTube convention: apply `-loudnessDb` to normalize). We clamp
 * the gain to <= 0 dB so loud masters are tamed but nothing is ever boosted into clipping; quiet
 * tracks are left untouched. Floored at -[maxAttenuationDb].
 */
fun normalizationMultiplier(
    loudnessDb: Double?,
    enabled: Boolean,
    maxAttenuationDb: Double = 12.0,
): Float {
    if (!enabled || loudnessDb == null) return 1f
    val gainDb = (-loudnessDb).coerceIn(-maxAttenuationDb, 0.0)
    return 10.0.pow(gainDb / 20.0).toFloat()
}

/**
 * Loudness makeup gain (dB, >= 0) that brings a quiet track UP toward the reference loudness so it
 * plays as loud as a streaming service (TIDAL/Spotify-style), instead of being left quiet. [loudnessDb]
 * is the stream's loudness relative to reference (YouTube convention: normalize by `-loudnessDb`). Only
 * the positive (boost) part is returned here — attenuation of loud masters is handled separately by
 * [normalizationMultiplier]. Capped at [maxBoostDb] so a very quiet track isn't boosted so hard that it
 * over-drives the downstream true-peak limiter — too much makeup makes the limiter reduce gain heavily,
 * which sounds like harmonic saturation / a harsh, "boxy" voice, especially at max volume.
 *
 * Capped at [maxBoostDb] = **+12 dB** (row 152; the native side clamps Safe Volume's gain to x4 = +12 dB
 * as well).
 *
 * +12 was validated in the v0.0.9 setup against `TruePeakLimiterAudioProcessor`, whose `softLimit` is a
 * STATIC tanh knee — it saturates but has no time constant, so it physically cannot pump. That processor
 * is now a dead stub, and the makeup is applied ahead of Superpowered's Limiter instead, which is a
 * GAIN-RIDING limiter (threshold -3 dB, ceiling -0.3 dBFS, release 0.05 s). Feeding +12 dB into that on a
 * dynamic master (YouTube loudnessDb ~ -9, i.e. quiet-but-peaky classical / jazz / hi-res — exactly the
 * material this player exists for) demands 9-12 dB of gain reduction on every transient, with the
 * envelope recovering between them: audible pumping, plus flattened macro-dynamics. Loud masters are
 * unaffected (they get attenuation, makeup = 0), so the artifact would appear ONLY on the best recordings.
 *
 * The cap was briefly +3 dB for that reason and went back to +12 dB because quiet tracks played quieter
 * than the rest (row 152). Raising or lowering it safely needs PEAK data
 * (`makeupDb = min(cap, -0.3 - truePeakDbFS)`), which needs a peak column on FormatEntity and a way to
 * fill it; neither exists.
 */
fun loudnessMakeupDb(
    loudnessDb: Double?,
    enabled: Boolean,
    maxBoostDb: Double = 12.0,
): Double {
    if (!enabled || loudnessDb == null) return 0.0
    return (-loudnessDb).coerceIn(0.0, maxBoostDb)
}

/**
 * Loudness (dB vs reference) to assume for a track with NO loudness metadata. Many non-YouTube sources
 * (Saavn/Qobuz/local) don't report loudness; treating them as a typical loud master (positive value →
 * a small attenuation, never a boost) keeps them from playing noticeably LOUDER than normalized tracks,
 * so the whole library stays at a consistent volume.
 */
const val DEFAULT_UNKNOWN_LOUDNESS_DB: Double = 7.0

/**
 * The loudness value (dB vs reference) to normalize a track by: the real [loudnessDb] if present (for a
 * local file, its ReplayGain tag — rows 724-725), else [perceptualLoudnessDb], else [measuredLoudnessDb],
 * else [DEFAULT_UNKNOWN_LOUDNESS_DB]. Ensures EVERY track is normalized (none plays at its raw, un-leveled
 * volume).
 *
 * [measuredLoudnessDb] is the `format.measuredLoudnessDb` column. Nothing in the app measures loudness
 * (the playback-time measurer, `NormalizationGainAudioProcessor`, is an inert stub, and its dead reader in
 * MusicService was removed — row 791), so it is normally null; a value already stored there is still read.
 */
fun effectiveLoudnessDb(
    loudnessDb: Double?,
    perceptualLoudnessDb: Double?,
    measuredLoudnessDb: Double? = null,
): Double =
    loudnessDb ?: perceptualLoudnessDb ?: measuredLoudnessDb ?: DEFAULT_UNKNOWN_LOUDNESS_DB

/**
 * Once a play has locked its gain, later loudness (auto-download on like, a measurement finishing)
 * must not change the live volume. The new value is cached for the NEXT play, which starts already
 * levelled. A different [playingId] is a new track and may take a new gain.
 */
fun isPlayingLoudnessFrozen(playingId: String?, lockedId: String?): Boolean =
    playingId != null && playingId == lockedId

/** Linear amplitude multiplier for a dB gain (e.g. -6 dB → 0.501, +6 dB → 1.995). */
fun dbToLinear(db: Double): Float = 10.0.pow(db / 20.0).toFloat()

/**
 * Makeup (dB, >= 0) that cancels the EQ's *auto*-headroom attenuation downstream, so boosting bands
 * no longer drops the overall volume. Equals how much [headroomPreampDb] pulled the signal below the
 * user's own pre-amp (= max positive band boost + 1 dB safety), capped at [maxCompDb]. The signal is
 * boosted back up after the EQ inside the true-peak limiter, which catches the boosted peaks → loud
 * EQ without clipping. The user's own pre-amp setting is preserved (not compensated).
 */
fun eqMakeupDb(
    userPreampDb: Double,
    enabledBandGainsDb: List<Double>,
    maxCompDb: Double = 12.0,
): Double {
    val comp = userPreampDb - headroomPreampDb(userPreampDb, enabledBandGainsDb)
    return comp.coerceIn(0.0, maxCompDb)
}

/**
 * Bounded soft limiter: transparent below [knee], then a `tanh` knee whose asymptote sits exactly at
 * [ceiling], so the output magnitude can NEVER exceed [ceiling] (no hard clip) yet stays clean. Used
 * by the true-peak limiter in an oversampled domain so inter-sample / treble transient peaks are
 * caught with minimal aliasing. [ceiling] defaults to ~-0.45 dBFS.
 */
fun softLimit(x: Float, ceiling: Float = 0.95f, knee: Float = 0.80f): Float {
    val ax = abs(x)
    if (ax <= knee) return x
    val range = ceiling - knee
    val comp = knee + range * tanh((ax - knee) / range)
    return if (x < 0f) -comp else comp
}

/**
 * One reconstructed sample inside a clipped run: continues the pre-clip linear trajectory
 * ([anchorNewest] + slope * [stepsIntoClip]) so a flat clipped top is rounded into a peak instead of
 * a harsh corner. Bounded to +/-[maxOvershoot] so it can never run away (the limiter still caps
 * output). Mitigation only — hard clipping is destructive and cannot be fully restored in real time.
 */
fun declipSample(
    anchorNewest: Float,
    anchorPrev: Float,
    stepsIntoClip: Int,
    maxOvershoot: Float = 1.35f,
): Float {
    val slope = anchorNewest - anchorPrev
    return (anchorNewest + slope * stepsIntoClip).coerceIn(-maxOvershoot, maxOvershoot)
}
