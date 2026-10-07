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
 *  1. **principal** — el color con tono que más pesa en la portada (población × saturación), es decir,
 *    el que el ojo identifica como "el color de la portada". Si la portada no tiene ningún color con
 *    tono (blanco y negro), el cubo más poblado: un gris, nunca un tono inventado.
 *  2. **segundo** y **tercero** — los siguientes por peso cuyo tono se aparta al menos [MIN_HUE_GAP]
 *    de los ya elegidos, para que el degradado muestre la variedad real de la portada en vez de tres
 *    versiones del mismo color. Las motas de menos del [MIN_SHARE] de la imagen no cuentan para el
 *    segundo y tercero: un píxel de ruido no debe teñir la pantalla.
 *  Si la portada solo tiene un tono, segundo y tercero quedan en null y quien llama decide.
 *
 * Coste: lo mismo que antes — una pasada de `Palette` sobre la miniatura de 100×100 ya decodificada,
 * una vez por canción, en `Dispatchers.Default`. 24 cubos en vez de 8 no cambian el orden de magnitud.
 *
 * Funciones puras sobre ARGB, sin Android, para fijarlas por test.
 */
object CoverColors {

    /** Un cubo de `Palette`: su color y cuántos píxeles de la miniatura cayeron en él. */
    data class Swatch(val rgb: Int, val population: Int)

    /** Los colores elegidos, en ARGB opaco. */
    data class Trio(val primary: Int, val secondary: Int?, val tertiary: Int?)

    /** Cubos que se piden a `Palette` (antes 8). */
    const val MAX_COLORS = 24

    /** Separación mínima de tono, en grados, entre los colores elegidos. */
    const val MIN_HUE_GAP = 25f

    /** Porcentaje mínimo de la imagen para que un color sea segundo o tercero. */
    const val MIN_SHARE = 0.015f

    /** Por debajo de esta saturación un color se trata como gris: su tono no es fiable. */
    private const val CHROMA_MIN_SATURATION = 0.18f

    /** Por debajo de este valor un color es casi negro: su tono tampoco se percibe. */
    private const val CHROMA_MIN_VALUE = 0.15f

    fun pick(swatches: List<Swatch>): Trio? {
        val usable = swatches.filter { it.population > 0 }
        if (usable.isEmpty()) return null
        val total = usable.sumOf { it.population }.toFloat()

        val chromatic = usable
            .map { it to hsv(it.rgb) }
            .filter { (_, h) -> h[1] >= CHROMA_MIN_SATURATION && h[2] >= CHROMA_MIN_VALUE }
            .sortedByDescending { (s, h) -> weight(s.population, h) }

        if (chromatic.isEmpty()) {
            return Trio(usable.maxBy { it.population }.rgb.opaque(), null, null)
        }

        val chosen = mutableListOf(chromatic.first())
        for (candidate in chromatic.drop(1)) {
            if (chosen.size == 3) break
            if (candidate.first.population / total < MIN_SHARE) continue
            if (chosen.all { hueDistance(it.second[0], candidate.second[0]) >= MIN_HUE_GAP }) {
                chosen += candidate
            }
        }
        return Trio(
            primary = chosen[0].first.rgb.opaque(),
            secondary = chosen.getOrNull(1)?.first?.rgb?.opaque(),
            tertiary = chosen.getOrNull(2)?.first?.rgb?.opaque(),
        )
    }

    /**
     * Peso de un color en la portada: su superficie, multiplicada por lo colorido que es — un rojo que
     * ocupa un cuarto de la portada pesa más que un granate apagado que ocupa lo mismo, porque es el
     * que el ojo nombra. Los muy oscuros pesan menos: en pantalla se leen como negro.
     */
    private fun weight(population: Int, hsv: FloatArray): Float =
        population * (0.35f + hsv[1]) * (if (hsv[2] < 0.30f) 0.6f else 1f)

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
