package iad1tya.echo.music.eq.autoeq

import iad1tya.echo.music.eq.EqResponse
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import kotlin.math.pow

/** One parsed AutoEq band: a peak (PK) or a low/high shelf (LSC/HSC) with AutoEq's own Q. */
data class AutoEqBand(val fc: Double, val gainDb: Double, val q: Double, val type: FilterType = FilterType.PK)

/** A parsed AutoEq "ParametricEQ" profile: a global pre-amp plus its PK/LSC/HSC bands. */
data class AutoEqProfile(val preampDb: Double, val bands: List<AutoEqBand>)

/**
 * Pure parser for AutoEq ParametricEQ text (jaakkopasanen/AutoEq), e.g.:
 *
 * ```
 * Preamp: -6.1 dB
 * Filter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70
 * Filter 2: ON PK Fc 8800 Hz Gain 5.1 dB Q 1.42
 * Filter 6: ON HSC Fc 10000 Hz Gain -2.1 dB Q 0.70
 * ```
 *
 * ON peaking (PK) and shelf (LSC/HSC) filters are kept. The preamp AutoEq writes is computed for ALL of
 * them, so dropping the shelves (as this parser once did) left the preamp cutting for a bass boost that
 * was never applied.
 */
object AutoEqParser {

    private val preampRegex = Regex("""Preamp:\s*(-?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val filterRegex = Regex(
        """Filter\s+\d+:\s*ON\s+(PK|LSC|HSC)\s+Fc\s+(\d+(?:\.\d+)?)\s*Hz\s+Gain\s+(-?\d+(?:\.\d+)?)\s*dB\s+Q\s+(\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): AutoEqProfile {
        val preamp = preampRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val bands = filterRegex.findAll(text).mapNotNull { m ->
            val type = FilterType.valueOf(m.groupValues[1].uppercase())
            val fc = m.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
            val gain = m.groupValues[3].toDoubleOrNull() ?: return@mapNotNull null
            val q = m.groupValues[4].toDoubleOrNull() ?: return@mapNotNull null
            if (fc <= 0.0 || q <= 0.0) null else AutoEqBand(fc, gain, q, type)
        }.toList()
        return AutoEqProfile(preamp, bands)
    }
}

/**
 * AutoEq defines its shelves as RBJ biquads with alpha = sin(w0) / (2·Q); the engine (EqResponse and
 * SuperpoweredBridge `auraRbjCoefficients`) uses the RBJ slope form, S = clamp(Q / [EqResponse.DEFAULT_Q],
 * 0.05, 1). Solving sqrt((A + 1/A)(1/S − 1) + 2) = 1/Q for S gives the engine Q that draws and plays the
 * same shelf. S above 1 (Q above ~0.707) cannot be expressed by the engine and falls back to S = 1.
 */
fun autoEqShelfQToEngineQ(q: Double, gainDb: Double): Double {
    val a = 10.0.pow(gainDb / 40.0)
    val s = 1.0 / ((1.0 / (q * q) - 2.0) / (a + 1.0 / a) + 1.0)
    return (if (s.isFinite() && s > 0.0) s.coerceIn(0.05, 1.0) else 1.0) * EqResponse.DEFAULT_Q
}

/** The profile as the engine's Auto-EQ correction stage: one band per AutoEq filter, same type and response. */
fun AutoEqProfile.toAutoBands(): List<ParametricEQBand> = bands.map { b ->
    ParametricEQBand(
        frequency = b.fc,
        gain = b.gainDb,
        q = if (b.type == FilterType.PK) b.q else autoEqShelfQToEngineQ(b.q, b.gainDb),
        filterType = b.type,
        enabled = true,
    )
}
