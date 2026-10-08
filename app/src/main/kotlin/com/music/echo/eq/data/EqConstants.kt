package iad1tya.echo.music.eq.data

import kotlin.math.abs

/** Canonical 10-band ISO standard equalizer, optimized for 32-bit floating point processing. */
object EqConstants {
    val FREQUENCIES = doubleArrayOf(
        31.5, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
    )

    /** Compact axis labels aligned 1:1 with [FREQUENCIES]. */
    val FREQUENCY_LABELS = listOf(
        "31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"
    )

    /** Musical octave Q for 10-band. */
    const val Q = 1.414
    const val BAND_COUNT = 10
    const val GAIN_MIN = -18f
    const val GAIN_MAX = 18f
    const val PREAMP_MIN = -20f
    const val PREAMP_MAX = 6f

    /**
     * The house preamp, and the only place it is written down.
     *
     * 🔴 OWNER ORDER (2026-09-16): *"el preamp quiero que esté +2.2 dB, y a veces cuando reviso está en
     * 0.0 dB"*. Both halves of that sentence had the same cause — the value lived in four places that
     * disagreed: the EQ screen defaulted to 2.2, three one-time migrations in `App.kt` walked it
     * 2.2 → 3.0 → 2.0, and `MusicService.resetEqPreamp` set it to 0.0 every time Safe Volume was switched
     * off. So "sometimes it is at 0.0" was not a glitch he imagined: the app really did zero it behind
     * him, and which value he saw depended on which of the four had spoken last.
     *
     * Everything that needs a default preamp now reads THIS. A number that means "the tuning this player
     * ships with" must have exactly one home, or it drifts apart again the next time one copy is edited.
     *
     * 🔴 OWNER ORDER (2026-09-25): raised from +2.2 to +2.5 dB. See
     * [iad1tya.echo.music.App.migratePreampDefault20260925] for the one-time forced migration that
     * settles every existing install on this value too, same "sí o sí" contract as
     * `migrateAudioDefaults20260913` before it.
     */
    const val DEFAULT_PREAMP_DB = 2.5f
}

/** Band filter shape — code matches desktop bandType (0=Peak, 1=LowShelf, 2=HighShelf). */
enum class EqBandType(val code: Int) {
    PEAK(0),
    LOW_SHELF(1),
    HIGH_SHELF(2);

    companion object {
        fun fromCode(code: Int): EqBandType = entries.firstOrNull { it.code == code } ?: PEAK
    }
}

/**
 * Factory EQ presets — optimized for Audiophile/Superpowered sound signatures.
 *
 * 2026-08-18: briefly trimmed to only the published/measurement-backed research curves (Harman, Diffuse
 * Field, Free Field, Olive-Welti), then RESTORED in full the same day — the owner tested the trimmed set
 * across their actual devices and it sounded worse than "Audiophile" + "Acoustic / Live", which real
 * listening had already validated as their reference combo. Ear feedback on the owner's own hardware
 * overrides theoretical "what reviewers cite" curation every time.
 *
 * 2026-10-08 (fila 357): the owner himself retired thirteen of them (see [RETIRED_PRESET_GAINS]) and made
 * "Aura Hi-Res v3" the default. Do not bring them back without his order.
 */
enum class FactoryPreset(val displayName: String, val description: String, val gains: FloatArray) {
    FLAT("Bypass", "Sonido original sin alteraciones.", FloatArray(10) { 0f }),

    HARMAN_TARGET("Harman Target", "La curva perfecta de estudio. Sub-bajos presentes y agudos naturales.", floatArrayOf(4.5f, 3.5f, 1.0f, -0.5f, 0f, 0f, 1.5f, 2.5f, 1.0f, 0.5f)),

    AUDIOPHILE("Audiophile", "Referencia de monitor. Domestica frecuencias sucias, añade calidez y aire.", floatArrayOf(2.0f, 1.5f, -0.5f, -1.0f, 0f, 0f, 0.5f, 1.0f, 1.5f, 2.0f)),

    SPATIAL_AIR("Spatial & Air", "Maximiza la imagen estéreo y la separación de instrumentos.", floatArrayOf(1.0f, 0.5f, -1.5f, -2.0f, -1.0f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f)),

    DEEP_PUNCH("Deep Punch", "Bajos profundos y rápidos que no ahogan a los cantantes.", floatArrayOf(5.0f, 4.0f, 1.0f, -1.5f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 1.0f)),

    ACOUSTIC_LIVE("Acoustic / Live", "Preserva el timbre orgánico de instrumentos como si fuera un concierto en vivo.", floatArrayOf(1.0f, 1.5f, 0.5f, -1.0f, 0f, 1.5f, 2.5f, 1.5f, 1.0f, 1.5f)),

    LOW_VOLUME_LOUDNESS("Low Vol. Enhancer", "Compensa la pérdida de audición en bajos y agudos a volúmenes bajos.", floatArrayOf(5.0f, 4.0f, 2.0f, 0f, -1.0f, 0f, 1.0f, 2.5f, 3.5f, 4.0f)),

    // Certified-style listening curves (approximate published targets on a 10-band graphic EQ).
    FREE_FIELD("Free Field", "Respuesta en campo libre: leve refuerzo de agudos para monitores abiertos.", floatArrayOf(0.5f, 0f, -0.5f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f)),

    STUDIO_MIX("Studio Mix", "Balance de consola de mezcla: voces e instrumentos claros, graves controlados.", floatArrayOf(1.0f, 1.0f, 0f, -0.5f, 0f, 0.5f, 1.5f, 2.0f, 1.0f, 0.5f)),

    CRYSTAL_CLEAR("Crystal Clear", "Transparencia total: máximo detalle y aire en los agudos, medios limpios.", floatArrayOf(0f, -0.5f, -1.0f, -1.0f, 0f, 1.0f, 1.5f, 2.5f, 2.5f, 3.0f)),

    BASS_HEAD("Bass Head", "Graves masivos para amantes del bajo, sin enturbiar la voz.", floatArrayOf(6.0f, 5.0f, 3.0f, 1.0f, 0f, -0.5f, 0f, 0.5f, 1.0f, 1.0f)),

    AR_SOUND("AR-SOUND", "Firma personal del dueño: sub-bajo profundo al frente, medios despejados y agudos con aire.", floatArrayOf(9.0f, 0f, 0f, -3.0f, -3.0f, -2.0f, 1.0f, 1.0f, 2.0f, 2.0f)),

    AR_SOUND_V2("AR-SOUND V2", "Evolución de la firma del dueño: bajo y sub-bajo con más cuerpo, medios bajos limpios y agudos con más aire.", floatArrayOf(9.0f, 6.0f, 1.0f, -1.0f, -2.0f, 0f, 1.0f, 0f, 2.0f, 3.0f)),

    // The house curve and the DEFAULT since fila 357 (owner 2026-10-08: "de predeterminado dejas Aura Hi-Res
    // v3"; "Aura Hi-Res" and "Aura Hi-Res v2" were retired — see RETIRED_PRESET_GAINS). Origin (owner request
    // 2026-10-06): v2's signature with 1 dB of 6-8 kHz treble moved to the 16 kHz "air" band and a light 500 Hz
    // de-box — less harshness, less de-esser and limiter work at the +2.5 dB house preamp. LAST entry, so it can
    // never steal another preset's chip; verified outside the 0.5 dB match tolerance of every other preset.
    AURA_HI_RES_V3("Aura Hi-Res v3", "Evolución de la v2: el mismo bajo con pegada, medios más despejados y agudos más suaves en las eses, con más aire arriba. Menos fatiga y menos trabajo del limitador.", floatArrayOf(3.5f, 3.5f, 1.0f, -1.0f, -0.5f, 0f, 1.0f, 1.5f, 2.0f, 3.0f))
}

/**
 * Fila 357 — presets RETIRED by the owner (2026-10-08): *"de los presets quiero que elimines todos los que digan
 * Aura Hi-Res, exceptuando Aura Hi-Res v3; también elimina Analog Tape, Olive-Welti, Diffuse Field, Vocal
 * Presence, Sub-Bass Rumble, Cinematic Warmth, Sparkle & Detail, Reference Natural, y de predeterminado dejas
 * Aura Hi-Res v3"* — y enseguida: *"también elimina el preset Tube Amp, Vinyl, Harman IE"*. Their curves are kept ONLY so `App.migrateRetiredPresetsToV3` can recognise an EQ still
 * sitting on one of them and move it to [FactoryPreset.AURA_HI_RES_V3]. Never shown, never selectable.
 */
val RETIRED_PRESET_GAINS: Map<String, FloatArray> = mapOf(
    "Sparkle & Detail" to floatArrayOf(0f, 0f, -0.5f, -1.0f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f),
    "Sub-Bass Rumble" to floatArrayOf(6.0f, 3.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Vocal Presence" to floatArrayOf(-1.0f, -0.5f, -1.5f, -0.5f, 1.0f, 3.0f, 3.5f, 2.0f, 0.5f, 0f),
    "Cinematic Warmth" to floatArrayOf(3.0f, 2.5f, 2.0f, 1.0f, 0f, -0.5f, -1.0f, -1.0f, -1.5f, -2.0f),
    "Reference Neutral" to floatArrayOf(0.5f, 0f, -0.5f, 0f, 0f, 0f, 0f, 0.5f, 0.5f, 1.0f),
    "Diffuse Field" to floatArrayOf(1.0f, 0.5f, 0f, -0.5f, -1.0f, 0f, 1.0f, 2.0f, 2.5f, 2.0f),
    "Olive-Welti" to floatArrayOf(4.0f, 3.0f, 1.5f, 0f, -0.5f, 0f, 1.0f, 1.5f, 1.0f, 0.5f),
    "Analog Tape" to floatArrayOf(2.0f, 2.5f, 1.5f, 0.5f, 0f, -0.5f, -0.5f, -1.0f, -1.5f, -2.0f),
    "Aura Hi-Res" to floatArrayOf(5.0f, 4.0f, 2.0f, 0f, 0f, 1.0f, 0f, 1.0f, 2.0f, 3.0f),
    "Aura Hi-Res v2" to floatArrayOf(3.0f, 4.0f, 1.0f, -1.0f, 0f, 0f, 1.0f, 2.0f, 3.0f, 2.0f),
    "Tube Amp" to floatArrayOf(-0.5f, 0.5f, 1.5f, 2.0f, 2.5f, 2.0f, 1.0f, 0.5f, -1.0f, -2.0f),
    "Harman IE" to floatArrayOf(5.0f, 3.5f, 1.0f, -0.5f, -1.0f, 0f, 1.0f, 2.0f, 1.5f, 0.5f),
    "Vinyl" to floatArrayOf(1.5f, 2.0f, 1.0f, 0f, -0.5f, 0f, 0.5f, 0f, -1.0f, -1.5f),
)

/** The 2026-09-05 "Aura Hi-Res v2" curve, kept only so the 2026-09-13 migration can recognise it. */
val AURA_HI_RES_V2_PREVIOUS_GAINS = floatArrayOf(4.0f, 4.0f, 2.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 2.0f, 3.0f)

/**
 * True when [gains] is still exactly (±0.05 dB) one of the [RETIRED_PRESET_GAINS] curves or the 2026-09-05 v2
 * curve — what the fila-357 migration moves to "Aura Hi-Res v3". Pure, JVM-tested.
 */
fun isRetiredPresetCurve(gains: FloatArray): Boolean =
    (RETIRED_PRESET_GAINS.values + AURA_HI_RES_V2_PREVIOUS_GAINS).any { curve ->
        gains.size == curve.size && gains.indices.all { abs(gains[it] - curve[it]) < 0.05f }
    }

/** Per-band tolerance (dB) used to decide whether the live gains still "are" a factory preset. */
const val FACTORY_PRESET_MATCH_TOLERANCE_DB = 0.5f

/**
 * Matches a set of band gains against the factory presets.
 *
 * Derived from the CANONICAL enum order on purpose: several curves can match within the
 * [FACTORY_PRESET_MATCH_TOLERANCE_DB] tolerance (FLAT matches anything near zero), so the winner
 * must not depend on display order. Pure and allocation-light so the EQ screen can call it on
 * every recomposition — and so the match rule is testable off-device (registry #181).
 */
fun eqFactoryPresetMatch(bandGains: FloatArray): FactoryPreset? =
    FactoryPreset.entries.firstOrNull { preset ->
        bandGains.size == preset.gains.size &&
            bandGains.indices.all { abs(bandGains[it] - preset.gains[it]) < FACTORY_PRESET_MATCH_TOLERANCE_DB }
    }
