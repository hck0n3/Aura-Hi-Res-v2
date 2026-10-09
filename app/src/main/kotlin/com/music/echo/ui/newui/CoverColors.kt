package iad1tya.echo.music.ui.newui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Qué tres colores de una portada pinta la app (fondo animado, acento, cristal).
 *
 * 🔴 Dueño (2026-10-07): *"una paleta de colores más amplia… para que sí o sí pueda reflejar el color
 * de las portadas exacto"*.
 *
 * ## Por qué no salían los colores exactos
 * La extracción pedía 8 colores a `Palette` y luego tomaba los "perfiles" con nombre en un orden fijo
 * (vibrante → vibrante claro → vibrante oscuro → dominante…). Dos consecuencias:
 *  · con 8 cubos, dos tonos cercanos de la portada se fundían en uno intermedio que no está en ella;
 *  · el primer color era SIEMPRE el "vibrante", aunque fuera un detalle mínimo de la portada, y el
 *    color que de verdad la llena quedaba tercero o fuera.
 *
 * ## La regla
 * [pick] recibe TODOS los cubos de `Palette` (ahora 24) con su población y elige:
 *  1. **principal** — el color con tono que MÁS SUPERFICIE ocupa en la portada. No el más intenso:
 *    *"no quiero que agarre el color más fuerte de la portada, quiero que agarre los colores de la
 *    portada"* (dueño, 2026-10-07, fila 342) — antes la saturación multiplicaba el peso y un color muy
 *    vivo le ganaba al que llena la portada. Si la portada no tiene ningún color con
 *    tono (blanco y negro), el cubo más poblado: un gris, nunca un tono inventado.
 *  2. **segundo** y **tercero** — los siguientes por peso cuyo tono se aparta al menos [MIN_HUE_GAP]
 *    de los ya elegidos, para que el degradado muestre la variedad real de la portada en vez de tres
 *    versiones del mismo color. Las motas de menos del [MIN_SHARE] de la imagen no cuentan para el
 *    segundo y tercero: un píxel de ruido no debe teñir la pantalla.
 *  Si la portada solo tiene un tono, segundo y tercero quedan en null y quien llama decide.
 *
 * ## Negro y blanco también son colores de la portada (dueño, 2026-10-09)
 * *"cuando las portadas son negras y otros colores omite el color negro… los colores blancos los omite
 * también"*. La causa raíz estaba aquí: el filtro de la línea `chromatic` tiraba TODO cubo sin tono en
 * cuanto la portada tenía un solo color con tono, así que el negro y el blanco no llegaban a la pantalla.
 * Ahora [pick] además resume la portada en [Trio.tones]:
 *  · **DARK** — todo lo que el ojo lee como negro (Oklab L < [DARK_L], o un gris por debajo de
 *    [NEUTRAL_SPLIT_L]), mezclado en Oklab por población: un negro con algo de azul marino da un negro
 *    azulado, no un negro genérico. Su [Tone.share] decide cuánto se oscurece el fondo.
 *  · **LIGHT** — blancos, cremas y grises claros sin tono, mezclados igual: la luz de la portada.
 *  · **CHROMA** — hasta [MAX_CHROMATIC] colores con tono: los tres de siempre más hasta dos "extra" que
 *    se distinguen de verdad de los ya elegidos en Oklab (dueño: *"que sea más vasto… muy repetitivo"*).
 * [AuraCoverMix] convierte esos tonos en fondo + lóbulos.
 *
 * Coste: lo mismo que antes — una pasada de `Palette` sobre la miniatura de 100×100 ya decodificada,
 * una vez por canción, en `Dispatchers.Default`. Unas conversiones Oklab sobre ≤ 24 cubos, una vez.
 *
 * Funciones puras sobre ARGB, sin Android, para fijarlas por test.
 */
object CoverColors {

    /** Un cubo de `Palette`: su color y cuántos píxeles de la miniatura cayeron en él. */
    data class Swatch(val rgb: Int, val population: Int)

    /** Qué papel juega un tono de la portada en la mezcla. */
    enum class Kind { CHROMA, LIGHT, DARK }

    /** Un tono de la portada: color opaco, fracción de la portada (0..1) y su papel. */
    data class Tone(val rgb: Int, val share: Float, val kind: Kind)

    /**
     * Los colores elegidos, en ARGB opaco. [primary]/[secondary]/[tertiary] son los de siempre (el acento);
     * [tones] es la portada entera: todos los colores con tono elegidos (primero el principal), su blanco
     * y su negro. [chromatic] es false en una portada sin ningún color con tono: [primary] es entonces el
     * gris más poblado.
     */
    data class Trio(
        val primary: Int,
        val secondary: Int?,
        val tertiary: Int?,
        val chromatic: Boolean = true,
        val tones: List<Tone> = emptyList(),
    ) {
        /** Fracción de la portada que se lee como negro (0 si no tiene). */
        val darkShare: Float get() = tones.firstOrNull { it.kind == Kind.DARK }?.share ?: 0f

        /** Fracción de la portada que es blanco / gris claro sin tono. */
        val lightShare: Float get() = tones.firstOrNull { it.kind == Kind.LIGHT }?.share ?: 0f
    }

    /** Cubos que se piden a `Palette` (antes 8). */
    const val MAX_COLORS = 24

    /** Colores con tono como máximo: principal, segundo, tercero y dos extra. */
    const val MAX_CHROMATIC = 5

    /** Separación mínima de tono, en grados, entre los colores elegidos. */
    const val MIN_HUE_GAP = 25f

    /** Porcentaje mínimo de la imagen para que un color sea segundo o tercero. */
    const val MIN_SHARE = 0.015f

    /**
     * Distancia Oklab mínima para un color "extra" (4.º y 5.º): puede compartir tono con otro si se ve
     * claramente distinto (un azul claro junto a un azul oscuro), pero dos casi iguales no cuentan dos veces.
     */
    const val MIN_EXTRA_DISTANCE = 0.10

    /** Oklab L por debajo de la cual un color se lee como negro, tenga el tono que tenga. */
    const val DARK_L = 0.30

    /** Un gris sin tono por debajo de esta L cuenta como negro; por encima, como luz. */
    const val NEUTRAL_SPLIT_L = 0.40

    /** Por debajo de esta saturación un color se trata como gris: su tono no es fiable. */
    private const val CHROMA_MIN_SATURATION = 0.18f

    /** Por debajo de este valor un color es casi negro: su tono tampoco se percibe. */
    private const val CHROMA_MIN_VALUE = 0.15f

    private class Measured(val swatch: Swatch, val hsv: FloatArray, val lab: DoubleArray) {
        val chromatic: Boolean get() = hsv[1] >= CHROMA_MIN_SATURATION && hsv[2] >= CHROMA_MIN_VALUE
    }

    fun pick(swatches: List<Swatch>): Trio? {
        val usable = swatches.filter { it.population > 0 }
        if (usable.isEmpty()) return null
        val total = usable.sumOf { it.population }.toFloat()
        val measured = usable.map { Measured(it, hsv(it.rgb), Oklab.fromArgb(it.rgb)) }

        // The cover's black and its white/light greys, each mixed in Oklab by population.
        val darkTone = pool(
            measured.filter { it.lab[0] < DARK_L || (!it.chromatic && it.lab[0] < NEUTRAL_SPLIT_L) },
            total,
            Kind.DARK,
        )
        val lightTone = pool(
            measured.filter { !it.chromatic && it.lab[0] >= NEUTRAL_SPLIT_L },
            total,
            Kind.LIGHT,
        )

        val chromatic = measured
            .filter { it.chromatic }
            .sortedByDescending { weight(it.swatch.population, it.hsv) }

        if (chromatic.isEmpty()) {
            return Trio(
                primary = usable.maxBy { it.population }.rgb.opaque(),
                secondary = null,
                tertiary = null,
                chromatic = false,
                tones = listOfNotNull(lightTone, darkTone),
            )
        }

        val chosen = mutableListOf(chromatic.first())
        for (candidate in chromatic.drop(1)) {
            if (chosen.size == 3) break
            if (candidate.swatch.population / total < MIN_SHARE) continue
            if (chosen.all { hueDistance(it.hsv[0], candidate.hsv[0]) >= MIN_HUE_GAP }) {
                chosen += candidate
            }
        }
        // Extras (4th, 5th): any other real colour of the cover that LOOKS different from all the chosen
        // ones. Near-blacks are already the DARK tone, so they are not counted twice.
        val extras = mutableListOf<Measured>()
        for (candidate in chromatic) {
            if (chosen.size + extras.size >= MAX_CHROMATIC) break
            if (chosen.any { it === candidate }) continue
            if (candidate.swatch.population / total < MIN_SHARE) continue
            if (candidate.lab[0] < DARK_L) continue
            if ((chosen + extras).all { Oklab.distance(it.lab, candidate.lab) >= MIN_EXTRA_DISTANCE }) {
                extras += candidate
            }
        }
        val chromaTones = (chosen + extras).map {
            Tone(it.swatch.rgb.opaque(), it.swatch.population / total, Kind.CHROMA)
        }
        return Trio(
            primary = chosen[0].swatch.rgb.opaque(),
            secondary = chosen.getOrNull(1)?.swatch?.rgb?.opaque(),
            tertiary = chosen.getOrNull(2)?.swatch?.rgb?.opaque(),
            chromatic = true,
            tones = chromaTones + listOfNotNull(lightTone, darkTone),
        )
    }

    /** Population-weighted Oklab mean of [members] — "mixes them well" — or null when empty. */
    private fun pool(members: List<Measured>, total: Float, kind: Kind): Tone? {
        if (members.isEmpty()) return null
        var l = 0.0
        var a = 0.0
        var b = 0.0
        var population = 0L
        members.forEach {
            val p = it.swatch.population.toLong()
            l += it.lab[0] * p
            a += it.lab[1] * p
            b += it.lab[2] * p
            population += p
        }
        val n = population.toDouble()
        return Tone(Oklab.toArgb(l / n, a / n, b / n), (population / total.toDouble()).toFloat(), kind)
    }

    /**
     * Peso de un color en la portada: su superficie (fila 342: sin premiar la saturación). Los muy
     * oscuros pesan menos: en pantalla se leen como negro.
     */
    private fun weight(population: Int, hsv: FloatArray): Float =
        population * (if (hsv[2] < 0.30f) 0.6f else 1f)

    fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    /** ARGB → [tono 0..360, saturación 0..1, valor 0..1]. */
    fun hsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val s = if (mx == 0f) 0f else d / mx
        return floatArrayOf(h, s, mx)
    }

    private fun Int.opaque(): Int = this or (0xFF shl 24)
}
