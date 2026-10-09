package iad1tya.echo.music.ui.newui

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Oklab / OKLCH on plain ARGB ints (Björn Ottosson, 2020). Pure Kotlin, no `android.*`, so the whole
 * colour pipeline of the covers is pinned by JVM tests.
 *
 * Why Oklab and not HSV: HSV "value" is not lightness. At the same value a yellow is ~4× as luminous as
 * a blue, and black, navy and dark brown all collapse to "low value" — which is how the old clamps
 * turned yellows into flares, blues into dim smudges and black into a mid grey. Oklab's L is perceived
 * lightness, so one L band gives every hue the same visual weight.
 */
internal object Oklab {

    /** ARGB (alpha ignored) → [L 0..1, a, b]. */
    fun fromArgb(argb: Int): DoubleArray {
        val r = linear(((argb shr 16) and 0xFF) / 255.0)
        val g = linear(((argb shr 8) and 0xFF) / 255.0)
        val b = linear((argb and 0xFF) / 255.0)
        val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    fun lightness(argb: Int): Double = fromArgb(argb)[0]

    fun chroma(argb: Int): Double = fromArgb(argb).let { hypot(it[1], it[2]) }

    /** Hue angle in degrees, 0..360. Meaningless for a neutral (chroma ~0). */
    fun hue(argb: Int): Double = fromArgb(argb).let { lab ->
        val h = Math.toDegrees(atan2(lab[2], lab[1]))
        if (h < 0.0) h + 360.0 else h
    }

    /** Euclidean distance in Oklab — a perceptual "how different do these two look". */
    fun distance(a: DoubleArray, b: DoubleArray): Double {
        val dl = a[0] - b[0]
        val da = a[1] - b[1]
        val db = a[2] - b[2]
        return kotlin.math.sqrt(dl * dl + da * da + db * db)
    }

    /** OKLCH → ARGB, gamut-mapped (see [toArgb]). */
    fun fromLch(lightness: Double, chroma: Double, hueDegrees: Double, alpha: Float = 1f): Int {
        val rad = Math.toRadians(hueDegrees)
        return toArgb(lightness, chroma * cos(rad), chroma * sin(rad), alpha)
    }

    /**
     * Oklab → ARGB. Out-of-gamut colours keep their lightness and hue and lose chroma (bisection), which
     * is the perceptually honest way to fit a colour into sRGB — clipping channels shifts the hue.
     */
    fun toArgb(lightness: Double, a: Double, b: Double, alpha: Float = 1f): Int {
        val l = lightness.coerceIn(0.0, 1.0)
        val alphaBits = (alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24
        if (abs(a) < NEUTRAL_EPSILON && abs(b) < NEUTRAL_EPSILON) {
            // A neutral: exactly r = g = b, so a grey stays a grey to the last bit.
            val v = encode(l * l * l)
            return alphaBits or (v shl 16) or (v shl 8) or v
        }
        var rgb = toLinear(l, a, b)
        if (!inGamut(rgb)) {
            var lo = 0.0
            var hi = 1.0
            repeat(22) {
                val mid = (lo + hi) / 2.0
                if (inGamut(toLinear(l, a * mid, b * mid))) lo = mid else hi = mid
            }
            rgb = toLinear(l, a * lo, b * lo)
        }
        return alphaBits or (encode(rgb[0]) shl 16) or (encode(rgb[1]) shl 8) or encode(rgb[2])
    }

    private fun toLinear(lightness: Double, a: Double, b: Double): DoubleArray {
        val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).let { it * it * it }
        val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).let { it * it * it }
        val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).let { it * it * it }
        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    private fun inGamut(rgb: DoubleArray): Boolean =
        rgb.all { it >= -GAMUT_EPSILON && it <= 1.0 + GAMUT_EPSILON }

    private fun linear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    /** Linear channel → 8-bit sRGB. */
    private fun encode(linear: Double): Int {
        val c = linear.coerceIn(0.0, 1.0)
        val srgb = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
        return (srgb * 255.0).roundToInt().coerceIn(0, 255)
    }

    private const val GAMUT_EPSILON = 1e-4

    /** Below this |a|,|b| a colour is a neutral (Oklab of an exact grey carries ~1e-5 of matrix noise). */
    private const val NEUTRAL_EPSILON = 1e-4
}

/**
 * How ALL the colours of a cover become the app's ground and its moving lobes.
 *
 * 🔴 Dueño (2026-10-09): *"cuando las portadas son negras y otros colores omite el color negro, no quiero
 * que omita los colores de las carátulas, que los mezcle bien"* · *"los colores blancos los omite
 * también"* · *"que sea más vasto, porque siento que es muy repetitivo"*.
 *
 * ## Por qué se perdían el negro y el blanco (causa raíz)
 *  1. `CoverColors.pick` descartaba todo cubo sin tono (saturación < 0.18 o valor < 0.15) en cuanto la
 *     portada tenía UN color con tono: el negro y el blanco no llegaban nunca a la pantalla.
 *  2. `bloomLobeColor` (HSV) forzaba el valor a 0.62..0.95: un negro que sí llegaba (portada en blanco
 *     y negro) salía gris medio, y un azul marino o un marrón oscuro salían "encendidos".
 *  3. `auraArtworkGround` solo miraba el tono del color principal: el fondo nunca era negro aunque la
 *     portada lo fuera en un 80 %.
 *
 * ## La mezcla
 *  · **El negro de la portada es el fondo.** Cuanto más negro tiene la portada ([CoverColors.Tone] DARK),
 *    más se acerca el fondo a un casi-negro con el matiz de ese negro (L 0.22 → 0.15), y si el negro ocupa
 *    al menos [MIN_DARK_LOBE_SHARE], además un lóbulo oscuro en el centro le da profundidad.
 *  · **El blanco de la portada es luz.** Un lóbulo de luz suave (L 0.70..0.88, alpha baja), nunca un
 *    destello: no puede tapar el texto más que un color normal.
 *  · **Todos los colores con tono** (hasta [CoverColors.MAX_CHROMATIC]) son lóbulos con la MISMA presencia
 *    visual: L en [CHROMA_L_MIN]..[CHROMA_L_MAX] conservando el orden claro/oscuro de la portada.
 *  · **Fondo a dos tonos:** el tono de arriba del color principal, el de abajo del segundo, los dos a la
 *    misma L perceptual — el contraste del texto es el mismo en toda la pantalla.
 */
internal object AuraCoverMix {

    /** The resolved look of one cover. ARGB ints so it stays pure. */
    data class Mix(
        /** Opaque ground, top tone. */
        val ground: Int,
        /** Opaque ground, bottom tone (same perceptual lightness as [ground]). */
        val groundBottom: Int,
        /** Lobes with their alpha, in slot order: top-left, top-right, centre, bottom-left, bottom-right. */
        val lobes: List<Int>,
        /** Opaque accent seed (the cover's main colour, perceptually normalised). */
        val seed: Int,
        val second: Int?,
        val third: Int?,
        /** The cover's light-carrying colours (all hues + its white), opaque, for gradients. */
        val spectrum: List<Int>,
        /** Kind of each lobe, same order as [lobes] (for tests and diagnostics). */
        val lobeKinds: List<CoverColors.Kind>,
    )

    const val SLOT_COUNT = 5
    private const val SLOT_CENTRE = 2

    // ── Lightness bands (Oklab L) ───────────────────────────────────────────────────────────────────
    /** Coloured lobes: one band for every hue, so a yellow no longer shouts over a blue. */
    const val CHROMA_L_MIN = 0.58
    const val CHROMA_L_MAX = 0.80

    // L' = PIVOT + SLOPE·(L − 0.60): the cover's lightness differences are HALVED, not erased (a gold stays
    // lighter than a red), and a red keeps reading red instead of being lifted to coral. Navy / dark brown
    // land on the floor — visible on the ground — while the cover's black itself becomes the ground.
    private const val CHROMA_L_PIVOT = 0.64
    private const val CHROMA_L_SLOPE = 0.5
    private const val CHROMA_MIN = 0.06
    private const val CHROMA_MAX = 0.20
    private const val CHROMA_BOOST = 1.15

    /** White / light-grey light. */
    const val LIGHT_L_MIN = 0.70
    const val LIGHT_L_MAX = 0.88
    private const val LIGHT_MAX_CHROMA = 0.025

    /** The dark lobe: a near-black that deepens the middle of the screen. */
    const val DARK_LOBE_L = 0.08
    private const val DARK_LOBE_MAX_CHROMA = 0.03

    /** A cover with no light colour at all (all black): a soft grey light, never nothing. */
    private const val SOFT_GREY_L = 0.62

    /** Ground of a coloured / light cover. 0.22 keeps every text step ≥ AA on every hue (tests). */
    const val GROUND_L = 0.22

    /** Ground of a cover that is mostly black: close to the shipped `#060A12` (L ≈ 0.144). */
    const val DARK_GROUND_L = 0.15
    private const val GROUND_CHROMA_SCALE = 0.45
    private const val GROUND_MAX_CHROMA = 0.05
    private const val DARK_GROUND_MAX_CHROMA = 0.03
    private const val NEUTRAL_CHROMA = 0.02

    /** Black share where the ground starts / finishes turning near-black. */
    private const val DARK_GROUND_FROM = 0.30f
    private const val DARK_GROUND_TO = 0.65f

    const val MIN_LIGHT_SHARE = 0.06f
    const val MIN_DARK_LOBE_SHARE = 0.12f

    // Alphas per slot (TL, TR, centre, BL, BR). The two top lobes keep the owner's 0.42 (2026-10-06); the
    // lower ones are softer because lists, the mini player and the nav bar live there.
    private val CHROMA_ALPHA = floatArrayOf(0.42f, 0.42f, 0.36f, 0.30f, 0.30f)
    private val LIGHT_ALPHA = floatArrayOf(0.30f, 0.30f, 0.26f, 0.22f, 0.22f)
    private const val DARK_ALPHA = 0.50f
    private const val SOFT_GREY_ALPHA_SCALE = 0.7f

    private class Lobe(val rgb: Int, val kind: CoverColors.Kind)

    fun resolve(trio: CoverColors.Trio, variants: List<Int> = emptyList()): Mix {
        val tones = trio.tones
        val chroma = tones.filter { it.kind == CoverColors.Kind.CHROMA }.ifEmpty {
            if (trio.chromatic) {
                listOfNotNull(trio.primary, trio.secondary, trio.tertiary)
                    .map { CoverColors.Tone(it, 0f, CoverColors.Kind.CHROMA) }
            } else {
                emptyList()
            }
        }
        val lightTone = tones.firstOrNull { it.kind == CoverColors.Kind.LIGHT }
            ?.takeIf { chroma.isEmpty() || it.share >= MIN_LIGHT_SHARE }
        val darkTone = tones.firstOrNull { it.kind == CoverColors.Kind.DARK }
        val darkShare = darkTone?.share ?: 0f
        val hasDarkLobe = darkTone != null && darkShare >= MIN_DARK_LOBE_SHARE

        // ── Bright sources: the main colour first (it is the cover's identity and the accent), then every
        // other colour and the white light by how much of the cover they fill.
        val first = chroma.firstOrNull()
        val rest = (chroma.drop(1) + listOfNotNull(lightTone)).sortedByDescending { it.share }
        val brights = ArrayList<Lobe>()
        if (first != null) brights += Lobe(chromaLobe(first.rgb), CoverColors.Kind.CHROMA)
        rest.forEach { tone ->
            brights += if (tone.kind == CoverColors.Kind.LIGHT) {
                Lobe(lightLobe(tone.rgb), CoverColors.Kind.LIGHT)
            } else {
                Lobe(chromaLobe(tone.rgb), CoverColors.Kind.CHROMA)
            }
        }
        if (brights.isEmpty()) {
            brights += Lobe(Oklab.toArgb(SOFT_GREY_L, 0.0, 0.0), CoverColors.Kind.LIGHT)
        }
        val spectrum = brights.map { it.rgb }

        // ── Slots. With a dark lobe it takes the centre and the brights take the four corners.
        val brightSlots = if (hasDarkLobe) intArrayOf(0, 1, 4, 3) else intArrayOf(0, 1, SLOT_CENTRE, 4, 3)
        val fillers = ArrayList<Lobe>(brights.take(brightSlots.size))
        if (fillers.size < brightSlots.size) {
            // A cover with few colours borrows its own lighter/darker variants before repeating itself.
            for (variant in variants) {
                if (fillers.size >= brightSlots.size) break
                val lobe = variantLobe(variant) ?: continue
                val lab = Oklab.fromArgb(lobe.rgb)
                if (fillers.all { Oklab.distance(Oklab.fromArgb(it.rgb), lab) >= VARIANT_MIN_DISTANCE }) {
                    fillers += lobe
                }
            }
        }
        val slotLobes = arrayOfNulls<Lobe>(SLOT_COUNT)
        brightSlots.forEachIndexed { index, slot ->
            slotLobes[slot] = fillers.getOrNull(index) ?: fillers[index % fillers.size]
        }
        val lobes = IntArray(SLOT_COUNT)
        val kinds = ArrayList<CoverColors.Kind>(SLOT_COUNT)
        for (slot in 0 until SLOT_COUNT) {
            if (hasDarkLobe && slot == SLOT_CENTRE) {
                lobes[slot] = withAlpha(darkLobe(darkTone!!.rgb), DARK_ALPHA)
                kinds += CoverColors.Kind.DARK
                continue
            }
            val lobe = slotLobes[slot]!!
            val synthetic = first == null && lightTone == null
            val alpha = when (lobe.kind) {
                CoverColors.Kind.CHROMA -> CHROMA_ALPHA[slot]
                else -> LIGHT_ALPHA[slot] * (if (synthetic) SOFT_GREY_ALPHA_SCALE else 1f)
            }
            lobes[slot] = withAlpha(lobe.rgb, alpha)
            kinds += lobe.kind
        }

        // ── Ground: the main colour's hue on top, the second colour's below, both pulled towards the
        // cover's own black as the black fills more of it.
        val darkness = smoothstep(DARK_GROUND_FROM, DARK_GROUND_TO, darkShare)
        val topSource = first?.rgb
        val bottomSource = chroma.getOrNull(1)?.rgb ?: lightTone?.rgb ?: topSource
        val ground = groundFor(topSource, darkTone?.rgb, darkness)
        val groundBottom = groundFor(bottomSource, darkTone?.rgb, darkness)

        val seed = brights.first().rgb
        return Mix(
            ground = ground,
            groundBottom = groundBottom,
            lobes = lobes.toList(),
            seed = seed,
            second = chroma.getOrNull(1)?.let { chromaLobe(it.rgb) },
            third = chroma.getOrNull(2)?.let { chromaLobe(it.rgb) },
            spectrum = spectrum,
            lobeKinds = kinds,
        )
    }

    /** A coloured swatch → its lobe: same hue, chroma kept (lightly floored), L into the shared band. */
    fun chromaLobe(argb: Int): Int {
        val lab = Oklab.fromArgb(argb)
        val c = hypot(lab[1], lab[2])
        val l = (CHROMA_L_PIVOT + CHROMA_L_SLOPE * (lab[0] - 0.60)).coerceIn(CHROMA_L_MIN, CHROMA_L_MAX)
        if (c < 1e-6) return Oklab.toArgb(l, 0.0, 0.0)
        val target = (c * CHROMA_BOOST).coerceIn(CHROMA_MIN, CHROMA_MAX)
        val k = target / c
        return Oklab.toArgb(l, lab[1] * k, lab[2] * k)
    }

    /** A white / light-grey swatch → soft light: its own faint tint, never a flare. */
    fun lightLobe(argb: Int): Int {
        val lab = Oklab.fromArgb(argb)
        val t = ((lab[0] - 0.45) / 0.55).coerceIn(0.0, 1.0)
        val l = LIGHT_L_MIN + (LIGHT_L_MAX - LIGHT_L_MIN) * t
        return scaledChroma(l, lab, LIGHT_MAX_CHROMA)
    }

    /** A black / near-black swatch → the dark lobe, keeping the faint hue a navy or brown black has. */
    fun darkLobe(argb: Int): Int = scaledChroma(DARK_LOBE_L, Oklab.fromArgb(argb), DARK_LOBE_MAX_CHROMA)

    /**
     * The ground for a cover whose main colour is [hueSource] (null = no hue: a neutral charcoal) and whose
     * black is [darkSource], [darkness] 0..1 of the way to that black.
     */
    fun groundFor(hueSource: Int?, darkSource: Int?, darkness: Float): Int {
        val top = colouredGround(hueSource)
        if (darkness <= 0f) return Oklab.toArgb(top[0], top[1], top[2])
        val darkLab = darkSource?.let { Oklab.fromArgb(it) }
        val hueLab = hueSource?.let { Oklab.fromArgb(it) }
        var da = (darkLab?.get(1) ?: 0.0) * 0.6 + (hueLab?.get(1) ?: 0.0) * 0.12
        var db = (darkLab?.get(2) ?: 0.0) * 0.6 + (hueLab?.get(2) ?: 0.0) * 0.12
        val dc = hypot(da, db)
        if (dc > DARK_GROUND_MAX_CHROMA) {
            da *= DARK_GROUND_MAX_CHROMA / dc
            db *= DARK_GROUND_MAX_CHROMA / dc
        }
        val t = darkness.toDouble()
        val l = top[0] + (DARK_GROUND_L - top[0]) * t
        val a = top[1] + (da - top[1]) * t
        val b = top[2] + (db - top[2]) * t
        return Oklab.toArgb(l, if (abs(a) < 1e-4) 0.0 else a, if (abs(b) < 1e-4) 0.0 else b)
    }

    /** [GROUND_L] in the hue of [hueSource], chroma scaled down and capped; neutral when it has no hue. */
    fun colouredGroundArgb(hueSource: Int?): Int = colouredGround(hueSource).let { Oklab.toArgb(it[0], it[1], it[2]) }

    private fun colouredGround(hueSource: Int?): DoubleArray {
        if (hueSource == null) return doubleArrayOf(GROUND_L, 0.0, 0.0)
        val lab = Oklab.fromArgb(hueSource)
        val c = hypot(lab[1], lab[2])
        if (c < NEUTRAL_CHROMA) return doubleArrayOf(GROUND_L, 0.0, 0.0)
        val k = (c * GROUND_CHROMA_SCALE).coerceAtMost(GROUND_MAX_CHROMA) / c
        return doubleArrayOf(GROUND_L, lab[1] * k, lab[2] * k)
    }

    private fun variantLobe(argb: Int): Lobe? {
        val lab = Oklab.fromArgb(argb)
        if (lab[0] < CoverColors.DARK_L) return null
        return if (hypot(lab[1], lab[2]) < NEUTRAL_LOBE_CHROMA) {
            Lobe(lightLobe(argb), CoverColors.Kind.LIGHT)
        } else {
            Lobe(chromaLobe(argb), CoverColors.Kind.CHROMA)
        }
    }

    private fun scaledChroma(l: Double, lab: DoubleArray, maxChroma: Double): Int {
        val c = hypot(lab[1], lab[2])
        if (c < 1e-3) return Oklab.toArgb(l, 0.0, 0.0)
        val k = c.coerceAtMost(maxChroma) / c
        return Oklab.toArgb(l, lab[1] * k, lab[2] * k)
    }

    private fun withAlpha(argb: Int, alpha: Float): Int =
        (argb and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24)

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private const val VARIANT_MIN_DISTANCE = 0.06
    private const val NEUTRAL_LOBE_CHROMA = 0.03
}
