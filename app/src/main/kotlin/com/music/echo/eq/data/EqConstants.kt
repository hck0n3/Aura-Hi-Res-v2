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
 * overrides theoretical "what reviewers cite" curation every time. Keep all of these.
 */
enum class FactoryPreset(val displayName: String, val description: String, val gains: FloatArray) {
    FLAT("Bypass", "Sonido original sin alteraciones.", FloatArray(10) { 0f }),

    HARMAN_TARGET("Harman Target", "La curva perfecta de estudio. Sub-bajos presentes y agudos naturales.", floatArrayOf(4.5f, 3.5f, 1.0f, -0.5f, 0f, 0f, 1.5f, 2.5f, 1.0f, 0.5f)),

    AUDIOPHILE("Audiophile", "Referencia de monitor. Domestica frecuencias sucias, añade calidez y aire.", floatArrayOf(2.0f, 1.5f, -0.5f, -1.0f, 0f, 0f, 0.5f, 1.0f, 1.5f, 2.0f)),

    SPATIAL_AIR("Spatial & Air", "Maximiza la imagen estéreo y la separación de instrumentos.", floatArrayOf(1.0f, 0.5f, -1.5f, -2.0f, -1.0f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f)),

    SPARKLE_DETAIL("Sparkle & Detail", "Realza los micro-detalles sutiles sin generar fatiga auditiva.", floatArrayOf(0f, 0f, -0.5f, -1.0f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f)),

    DEEP_PUNCH("Deep Punch", "Bajos profundos y rápidos que no ahogan a los cantantes.", floatArrayOf(5.0f, 4.0f, 1.0f, -1.5f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 1.0f)),

    SUB_BASS_RUMBLE("Sub-Bass Rumble", "Solo levanta las frecuencias más profundas (31Hz). Ideal para cine y electrónica.", floatArrayOf(6.0f, 3.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),

    VOCAL_PRESENCE("Vocal Presence", "Limpia el lodo musical y resalta específicamente la voz humana.", floatArrayOf(-1.0f, -0.5f, -1.5f, -0.5f, 1.0f, 3.0f, 3.5f, 2.0f, 0.5f, 0f)),

    ACOUSTIC_LIVE("Acoustic / Live", "Preserva el timbre orgánico de instrumentos como si fuera un concierto en vivo.", floatArrayOf(1.0f, 1.5f, 0.5f, -1.0f, 0f, 1.5f, 2.5f, 1.5f, 1.0f, 1.5f)),

    TUBE_AMP_WARMTH("Tube Amp", "Simula el sonido cálido y envolvente de un amplificador de tubos clásico.", floatArrayOf(-0.5f, 0.5f, 1.5f, 2.0f, 2.5f, 2.0f, 1.0f, 0.5f, -1.0f, -2.0f)),

    CINEMATIC_WARMTH("Cinematic Warmth", "Sonido denso y rico con agudos suaves. Para inmersión total.", floatArrayOf(3.0f, 2.5f, 2.0f, 1.0f, 0f, -0.5f, -1.0f, -1.0f, -1.5f, -2.0f)),

    LOW_VOLUME_LOUDNESS("Low Vol. Enhancer", "Compensa la pérdida de audición en bajos y agudos a volúmenes bajos.", floatArrayOf(5.0f, 4.0f, 2.0f, 0f, -1.0f, 0f, 1.0f, 2.5f, 3.5f, 4.0f)),

    REFERENCE_NEUTRAL("Reference Neutral", "Monitoreo de estudio plano. Mínima coloración, máxima fidelidad a la mezcla original.", floatArrayOf(0.5f, 0f, -0.5f, 0f, 0f, 0f, 0f, 0.5f, 0.5f, 1.0f)),

    // Certified-style listening curves (approximate published targets on a 10-band graphic EQ).
    DIFFUSE_FIELD("Diffuse Field", "Curva DF clásica (B&K): graves contenidos, presencia y aire naturales.", floatArrayOf(1.0f, 0.5f, 0f, -0.5f, -1.0f, 0f, 1.0f, 2.0f, 2.5f, 2.0f)),

    FREE_FIELD("Free Field", "Respuesta en campo libre: leve refuerzo de agudos para monitores abiertos.", floatArrayOf(0.5f, 0f, -0.5f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f)),

    HARMAN_IE("Harman IE", "Variante Harman para IEM: sub-grave presente, medios limpios, agudos controlados.", floatArrayOf(5.0f, 3.5f, 1.0f, -0.5f, -1.0f, 0f, 1.0f, 2.0f, 1.5f, 0.5f)),

    OLIVE_WELTI("Olive-Welti", "Preferencia de escucha Olive–Welti: grave cálido sin enmascarar la voz.", floatArrayOf(4.0f, 3.0f, 1.5f, 0f, -0.5f, 0f, 1.0f, 1.5f, 1.0f, 0.5f)),

    ANALOG_TAPE("Analog Tape", "Calidez de cinta magnética: graves redondos y agudos suavizados, sin fatiga.", floatArrayOf(2.0f, 2.5f, 1.5f, 0.5f, 0f, -0.5f, -0.5f, -1.0f, -1.5f, -2.0f)),

    VINYL("Vinyl", "El cuerpo cálido y orgánico del vinilo, con agudos dulces y medios presentes.", floatArrayOf(1.5f, 2.0f, 1.0f, 0f, -0.5f, 0f, 0.5f, 0f, -1.0f, -1.5f)),

    STUDIO_MIX("Studio Mix", "Balance de consola de mezcla: voces e instrumentos claros, graves controlados.", floatArrayOf(1.0f, 1.0f, 0f, -0.5f, 0f, 0.5f, 1.5f, 2.0f, 1.0f, 0.5f)),

    CRYSTAL_CLEAR("Crystal Clear", "Transparencia total: máximo detalle y aire en los agudos, medios limpios.", floatArrayOf(0f, -0.5f, -1.0f, -1.0f, 0f, 1.0f, 1.5f, 2.5f, 2.5f, 3.0f)),

    BASS_HEAD("Bass Head", "Graves masivos para amantes del bajo, sin enturbiar la voz.", floatArrayOf(6.0f, 5.0f, 3.0f, 1.0f, 0f, -0.5f, 0f, 0.5f, 1.0f, 1.0f)),

    AR_SOUND("AR-SOUND", "Firma personal del dueño: sub-bajo profundo al frente, medios despejados y agudos con aire.", floatArrayOf(9.0f, 0f, 0f, -3.0f, -3.0f, -2.0f, 1.0f, 1.0f, 2.0f, 2.0f)),

    AR_SOUND_V2("AR-SOUND V2", "Evolución de la firma del dueño: bajo y sub-bajo con más cuerpo, medios bajos limpios y agudos con más aire.", floatArrayOf(9.0f, 6.0f, 1.0f, -1.0f, -2.0f, 0f, 1.0f, 0f, 2.0f, 3.0f)),

    // Owner directive 2026-08-31 (updated 2026-09-04): the headphone house curve is REPLACED by an
    // exact 10-band spec — 31 Hz +5, 62 +4, 125 +2, 250 0, 500 0, 1k +1, 2k 0, 4k +1, 8k +2,
    // 16k +3. Same entry, same name: only the gains change (was the 2026-08-31 spec
    // 6,4,2,0,0,0,1,1,2,3 — row 163/199 lineage; the original row-163 seed was 6,4,1,-1,0,0,1,0,1,2).
    // Fresh installs seed this curve via migrateAudioDefaultsV2 (App.kt); existing installs get
    // it by tapping the "Aura Hi-Res" chip once, same accepted scope as rows 163/199.
    AURA_HI_RES("Aura Hi-Res", "La firma de la casa: graves con cuerpo, medios limpios y agudos con aire. El perfil activo por defecto desde el primer inicio.", floatArrayOf(5.0f, 4.0f, 2.0f, 0f, 0f, 1.0f, 0f, 1.0f, 2.0f, 3.0f)),

    // Owner directive 2026-09-05: NEW house profile "Aura Hi-Res v2" — 31 Hz +4, 62 +4, 125 +2, 250 +1,
    // 500 +1, 1k +1, 2k +1, 4k +1, 8k +2, 16k +3 — with the global EQ preamp default raised to +2.3 dB.
    // Deliberately an ADDITION, not an edit: the AURA_HI_RES curve above is frozen by the owner's
    // 2026-09-04 spec (registry row 201, EqConstantsTest.auraHiResCurveMatchesOwnerSpec). This entry
    // inherits the default-seed role: migrateAudioDefaultsV2 (App.kt) now seeds THIS curve on fresh
    // installs; existing installs keep their tuned EQ untouched (the seed only runs behind the
    // AudioDefaultsV2AppliedKey gate). Verified band-by-band: no other factory preset is within the
    // 0.5 dB match tolerance of this curve (closest is OLIVE_WELTI, off by 1.0 dB at 62 Hz), so its
    // chip selects uniquely — and being the LAST enum entry it can never steal another preset's match.
    AURA_HI_RES_V2("Aura Hi-Res v2", "La firma de la casa, evolución 2026: graves firmes, medios presentes y agudos con aire. El perfil activo por defecto desde el primer inicio.", floatArrayOf(4.0f, 4.0f, 2.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 2.0f, 3.0f))
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
