package iad1tya.echo.music.ui.newui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import coil3.toBitmap
import iad1tya.echo.music.LocalPlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The ambient bloom: three soft radial gradients behind the content of every new screen
 * (`.bl { inset:-12% -22% 48%; filter:blur(26px); radial-gradient ×3 }` in the reference render).
 *
 * ## Thermal / battery contract — READ THIS BEFORE TOUCHING IT
 * The owner's permanent quality gate is that nothing new may heat the device or drain the battery.
 * Two rules follow, and both are enforced by the implementation below:
 *
 *  1. **Computed per TRACK, not per frame.** The colours are plain data ([AuraBloomColors]) resolved
 *     once per media id and memoised here. A screen that scrolls, a progress bar that ticks 60×/s, a
 *     recomposing row — none of them recompute anything. Only a track change does.
 *  2. **No `Modifier.blur`.** A 26 dp render-effect blur over a full-screen layer is a GPU cost paid on
 *     every single frame, and below API 31 it silently degrades. Radial gradients that fade to
 *     transparent already produce the render's look. Since HALLAZGO-055 the gradient field is
 *     rasterized ONCE per (size, colours, intensity) into a capped-resolution bitmap
 *     ([rasterizeBloomLobes]) and every frame after that is a single blit — re-shading three
 *     translucent radial gradients per frame (six during the per-track dissolve) was the fill-rate
 *     cost behind the owner's jank report. No offscreen layer on the draw path, no readback, no
 *     per-frame allocation.
 *
 *     HALLAZGO-060 tightened this: the raster is cached on the [AuraBloomState] instance keyed by
 *     (size, colours) — NOT intensity, which is draw-time alpha — because the drawWithCache block
 *     re-runs on every recomposition of the screen, and the rhythm bucket crossings rebuild the
 *     player's ground layer several times per second while music plays.
 *
 *  3. **Row 342 — the lobes MOVE (owner 2026-10-07: "los colores de la portada… animados").** Each
 *     lobe is now its own small raster (256 px, once per track) and every frame is three blits whose
 *     position and size follow [AuraAmbientMotion.phase]. That phase is ONE app-wide clock at 15 fps,
 *     read only in the draw phase (nothing recomposes), stopped in background, Performance Mode, a hot
 *     device, battery saver, low-tier devices and with system animations off. The screen's content
 *     sits in its own layer ([auraBloom]'s `graphicsLayer`), so a drift step re-records the lobes only.
 *
 * ## The bloom REACTS to the artwork (0.6.146)
 * It used to claim to and did not: [AuraBloomCache.put] had no caller anywhere in `app/src/main`, so
 * [AuraBloomCache.get] always fell through to [AuraBloomColors.Brand] and every screen, every song and
 * every session got the identical teal/violet wash — a shipped placebo with a settings comment
 * documenting it. [rememberAuraBloom] now resolves the real thing:
 *
 *  · one 100×100 `Precision.INEXACT` Coil request per track, on [Dispatchers.IO]. That is byte for byte
 *    the request `MainActivity` already issues for the dynamic theme, so with the dynamic theme on this
 *    is a memory-cache HIT and costs no decode at all; with it off it is one 100×100 decode per track
 *    change, i.e. ~40 KB and a few ms, once.
 *  · [Palette] on [Dispatchers.Default], `maximumColorCount(24)` since row 341 ([CoverColors]).
 *  · at most ONE extraction in flight per media id ([AuraBloomCache.claim]), because Inicio, the
 *    player sheet and the queue can all be composed at the same instant with the same id.
 *
 * The draw path rasterizes the lobes once per track and blits them — still no `Modifier.blur`.
 */
object AuraBloomCache {

    /**
     * Bounded, snapshot-aware map keyed by media id. Snapshot-aware so a bloom stored *after* a screen
     * already composed still reaches it; bounded because a long session must not grow without limit.
     * 32 entries is a whole session's worth of tracks at a few bytes each.
     */
    private const val MAX_ENTRIES = 32

    private val entries = mutableStateMapOf<String, AuraBloomEntry>()
    private val insertionOrder = ArrayDeque<String>()

    /** Media ids whose extraction is currently running, so two screens never decode the same cover. */
    private val inFlight = HashSet<String>()

    /** Returns the stored bloom for [mediaId], or [AuraBloomColors.Brand] when nothing was stored. */
    fun get(mediaId: String?): AuraBloomColors {
        if (mediaId.isNullOrEmpty()) return AuraBloomColors.Brand
        return entries[mediaId]?.colors ?: AuraBloomColors.Brand
    }

    /**
     * Opaque accent seed extracted with the bloom for [mediaId], or null when the cover has not been
     * resolved yet / there is no now-playing id. [AuraPaletteSync] reads this so Teal / buttons /
     * section titles follow the same cover as the wash — not only the ambient gradients.
     */
    fun accentSeed(mediaId: String?): Color? = entry(mediaId)?.accentSeed

    /** The whole extraction for [mediaId] (seed + the cover's other real colours), or null. */
    fun entry(mediaId: String?): AuraBloomEntry? {
        if (mediaId.isNullOrEmpty()) return null
        return entries[mediaId]
    }

    /**
     * Reserves [mediaId] for extraction. Returns `true` only for the FIRST caller: `false` means the
     * bloom is already stored or another composition is already resolving it. Every `true` must be
     * paired with a [put] or a [release], or that id can never be retried in this process.
     */
    @Synchronized
    fun claim(mediaId: String): Boolean {
        if (mediaId.isEmpty()) return false
        if (entries.containsKey(mediaId)) return false
        return inFlight.add(mediaId)
    }

    /** Gives up a [claim] that produced nothing, so a later screen may try again. */
    @Synchronized
    fun release(mediaId: String) {
        inFlight.remove(mediaId)
    }

    /**
     * Stores a bloom (+ accent seed) for [mediaId]. Call this from a coroutine on a background
     * dispatcher when a track changes — NEVER from a composable body or a draw lambda.
     */
    @Synchronized
    fun put(mediaId: String, entry: AuraBloomEntry) {
        if (mediaId.isEmpty()) return
        inFlight.remove(mediaId)
        if (entries.put(mediaId, entry) == null) {
            insertionOrder.addLast(mediaId)
            while (insertionOrder.size > MAX_ENTRIES) {
                entries.remove(insertionOrder.removeFirst())
            }
        }
    }

    /** Test / diagnostic hook. */
    @Synchronized
    fun clear() {
        entries.clear()
        insertionOrder.clear()
        inFlight.clear()
    }

    /**
     * Ensures [mediaId] has a bloom+seed entry. Safe to call from several compositions: [claim]
     * dedupes in-flight work. Used by [rememberAuraBloom] and [AuraPaletteSync].
     */
    suspend fun ensure(context: android.content.Context, mediaId: String, thumbnailUrl: String) {
        if (!claim(mediaId)) return
        val entry = runCatching { extractAuraBloom(context, thumbnailUrl) }.getOrNull()
        if (entry != null) put(mediaId, entry) else release(mediaId)
    }
}

/** One cover extraction: ambient lobes + the opaque seed [AuraPalette] uses for chrome. */
@Immutable
data class AuraBloomEntry(
    val colors: AuraBloomColors,
    val accentSeed: Color,
    /**
     * The cover's second and third REAL colours (row 341, [CoverColors]), opaque, or null when the
     * cover has a single hue. [AuraAccent.fromCover] uses them for the accent trio instead of hues
     * rotated off the seed, so the gradients show the cover's own colours.
     */
    val coverSecondary: Color? = null,
    val coverTertiary: Color? = null,
)

/**
 * The bloom a screen is currently painting, plus the dissolve between the previous one and it.
 *
 * A track change must not CUT from one wash to another — that is a full-screen colour jump on every
 * song. [progress] runs 0→1 on a very low-stiffness spring (~1 s) and the two blooms are cross-faded
 * in the DRAW phase, so the dissolve costs three extra transparent gradient fills for one second and
 * recomposes nothing at all.
 */
@Stable
class AuraBloomState internal constructor(initial: AuraBloomColors) {
    /** What we are dissolving FROM (the previous bloom, sampled at the instant it was interrupted). */
    internal var from by mutableStateOf(initial)

    /** What we are dissolving TO — the current track's bloom. */
    internal var to by mutableStateOf(initial)

    /** 0f = fully [from], 1f = fully [to]. Read in the draw phase only. */
    internal var progress by mutableFloatStateOf(1f)

    // HALLAZGO-060: the raster cache lives on the state instance (one per screen composition,
    // remembered) instead of inside the drawWithCache block, which re-runs on every recomposition.
    // Row 342 (owner 2026-10-07: "los colores de la portada… animados"): ONE small raster PER LOBE —
    // a radial gradient in that lobe's colour — keyed by the colours only. The geometry (where each
    // lobe is, how big) is applied at draw time, so the lobes can move without ever re-rasterizing.
    // Intensity is not part of the key either: it is draw-time alpha.
    private var lobeFromColors: AuraBloomColors? = null
    private var lobeToColors: AuraBloomColors? = null
    private var lobeFromImages: List<ImageBitmap>? = null
    private var lobeToImages: List<ImageBitmap>? = null

    /**
     * The lobe rasters for ([from], [to]): rasterizes only on a track change (and reuses the previous
     * track's images as the dissolve's "from"), never per frame, per size or per intensity.
     */
    internal fun lobesFor(
        from: AuraBloomColors,
        to: AuraBloomColors,
    ): Pair<List<ImageBitmap>?, List<ImageBitmap>> {
        val previousToColors = lobeToColors
        val previousToImages = lobeToImages
        val toImages = if (previousToImages != null && previousToColors == to) {
            previousToImages
        } else {
            rasterizeBloomLobes(to).also {
                lobeToColors = to
                lobeToImages = it
            }
        }
        val fromImages = when {
            from == to -> null
            lobeFromColors == from && lobeFromImages != null -> lobeFromImages
            previousToColors == from && previousToImages != null -> previousToImages
            else -> rasterizeBloomLobes(from)
        }
        if (fromImages != null) {
            lobeFromColors = from
            lobeFromImages = fromImages
        }
        return fromImages to toImages
    }

    /** Applied as ONE snapshot so no frame can ever see the new colours at the old progress. */
    internal fun retarget(next: AuraBloomColors) {
        Snapshot.withMutableSnapshot {
            from = blend(from, to, progress)
            to = next
            progress = 0f
        }
    }

    private fun blend(a: AuraBloomColors, b: AuraBloomColors, t: Float): AuraBloomColors =
        if (t >= 1f) b else AuraBloomColors(
            topLeft = lerp(a.topLeft, b.topLeft, t),
            topRight = lerp(a.topRight, b.topRight, t),
            center = lerp(a.center, b.center, t),
        )
}

/**
 * Resolves the bloom for the currently playing track, once per track, and animates the dissolve.
 *
 * @param mediaId the current media id; pass `null` on screens with no now-playing context (the brand
 *   bloom is used, which is exactly what the render shows for Inicio / Biblioteca / Ajustes).
 */
@Composable
fun rememberAuraBloom(mediaId: String?): AuraBloomState {

    // The cover to extract from. Read from the SAME PlayerConnection the caller derived [mediaId] from,
    // and only used when the ids still agree — a stale url must never colour the wrong track.
    val playerConnection = LocalPlayerConnection.current
    val thumbnailUrl: String? = if (mediaId.isNullOrEmpty() || playerConnection == null) {
        null
    } else {
        val metadata by playerConnection.mediaMetadata.collectAsState()
        metadata?.takeIf { it.id == mediaId }?.thumbnailUrl
    }

    return rememberAuraBloomFromUrl(mediaId, thumbnailUrl)
}

/**
 * Fila 360 — the bloom of [key] extracted from [thumbnailUrl] (a now-playing track, or a screen's own cover —
 * see [rememberAuraBloomForCover]), with the same dissolve.
 */
@Composable
internal fun rememberAuraBloomFromUrl(key: String?, thumbnailUrl: String?): AuraBloomState {
    val target = AuraBloomCache.get(key)
    val state = remember { AuraBloomState(target) }
    val context = LocalContext.current
    LaunchedEffect(key, thumbnailUrl) {
        val id = key
        val url = thumbnailUrl
        if (id.isNullOrEmpty() || url.isNullOrEmpty()) return@LaunchedEffect
        AuraBloomCache.ensure(context, id, url)
    }

    LaunchedEffect(target) {
        if (target == state.to) return@LaunchedEffect
        state.retarget(target)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                // Deliberately the softest spring in the app: this is a full-screen colour change and
                // anything faster reads as a cut. ~1 s.
                stiffness = Spring.StiffnessVeryLow,
            ),
        ) { value, _ -> state.progress = value }
    }

    return state
}

/**
 * One 100×100 decode (shared with the dynamic theme's Coil memory-cache slot) → [Palette] → three
 * lobe colours + an opaque accent seed for [AuraPalette]. Suspends on IO/Default; never call it from
 * composition or a draw lambda.
 */
private suspend fun extractAuraBloom(
    context: android.content.Context,
    thumbnailUrl: String,
): AuraBloomEntry? {
    val bitmap: Bitmap = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(thumbnailUrl)
            // 100×100 + INEXACT is the request MainActivity already makes for the dynamic theme, and
            // the memory-cache key of a request without transformations does not include the size —
            // so this SHARES that slot instead of forcing a second decode. Do not make it EXACT.
            .size(100, 100)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
        context.imageLoader.execute(request).image?.toBitmap()
    } ?: return null

    // Row 341 (owner 2026-10-07: "reflejar el color de las portadas exacto"): 24 buckets instead of 8, so
    // neighbouring tones of the cover are no longer merged into an in-between colour it does not have,
    // and the colours are chosen by how much of the cover they fill ([CoverColors.pick]) instead of
    // "vibrant first" — the colour the eye names wins, not a small vivid detail.
    val palette = withContext(Dispatchers.Default) {
        Palette.from(bitmap).maximumColorCount(CoverColors.MAX_COLORS).resizeBitmapArea(100 * 100).generate()
    }
    val trio = CoverColors.pick(palette.swatches.map { CoverColors.Swatch(it.rgb, it.population) })
        ?: return null

    // A single-hue cover still gets some depth in the wash: its named light/dark variants fill the
    // empty lobes (same hue, other lightness), exactly as before row 341.
    val variants = listOfNotNull(
        palette.vibrantSwatch,
        palette.lightVibrantSwatch,
        palette.darkVibrantSwatch,
        palette.dominantSwatch,
        palette.mutedSwatch,
    ).map { it.rgb or (0xFF shl 24) }.filter { it != trio.primary }.distinct()
    val primary = trio.primary
    val secondary = trio.secondary ?: variants.getOrNull(0) ?: primary
    val tertiary = trio.tertiary ?: variants.firstOrNull { it != secondary } ?: secondary

    // Owner 2026-10-06: "que los colores animados que generan las portadas sean más notables". The
    // render's alphas (.32 / .30 / .24) are raised by about a third for COVER colours only — still a
    // wash, never three opaque blobs, and the brand fallback (no cover) keeps the render's values.
    return AuraBloomEntry(
        colors = AuraBloomColors(
            topLeft = bloomLobeColor(primary, COVER_BLOOM_ALPHA_TOP_LEFT),
            topRight = bloomLobeColor(secondary, COVER_BLOOM_ALPHA_TOP_RIGHT),
            center = bloomLobeColor(tertiary, COVER_BLOOM_ALPHA_CENTER),
        ),
        // Opaque, chroma-floored seed — same HSV floors as the lobes, so chrome and wash agree.
        accentSeed = bloomLobeColor(primary, 1f),
        coverSecondary = trio.secondary?.let { bloomLobeColor(it, 1f) },
        coverTertiary = trio.tertiary?.let { bloomLobeColor(it, 1f) },
    )
}

/**
 * Normalises one extracted swatch into something the ground can carry: a cover that is nearly black,
 * blown out or muddy must still produce a legible wash, not a grey smear or a white flare.
 */
private fun bloomLobeColor(rgb: Int, alpha: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(rgb, hsv)
    // An ACHROMATIC cover (black-and-white sleeve) keeps its zero saturation — forcing colour into it
    // would invent a hue the artwork does not have. Anything with a hue gets a floor so it survives
    // the low alpha.
    // Floors raised 2026-10-06 (0.35 / 0.55 → 0.45 / 0.62) so a muted or dark cover still reads as its
    // colour on the screen instead of a grey haze.
    if (hsv[1] > 0.05f) hsv[1] = hsv[1].coerceIn(0.45f, 0.95f)
    hsv[2] = hsv[2].coerceIn(0.62f, 0.95f)
    return Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = alpha)
}

/** Cover-colour lobe alphas (owner 2026-10-06). The brand fallback keeps the render's .32/.30/.24. */
// Row 342 (owner 2026-10-07: "que agarre los colores de la portada", not just the strongest): the three
// cover colours get (almost) the same weight, so the second and third are as visible as the first.
private const val COVER_BLOOM_ALPHA_TOP_LEFT = 0.42f
private const val COVER_BLOOM_ALPHA_TOP_RIGHT = 0.42f
private const val COVER_BLOOM_ALPHA_CENTER = 0.38f

/**
 * Paints the ambient bloom behind the content of a screen. Put it on the ROOT container of a new
 * screen, above the [AuraPalette.Ground] fill and below everything else — or simply use
 * [auraScreenBackground], which does both.
 *
 * @param intensity global multiplier, 0f..1f. The render dims the bloom on the denser screens
 *   (Cola `.45`, Biblioteca `.40`, Ajustes `.32`); pass those values, do not restyle the colours.
 */
fun Modifier.auraBloom(
    colors: AuraBloomState,
    intensity: Float = 1f,
): Modifier = this.drawWithCache {
    // HALLAZGO-060: this block re-runs on every recomposition of the screen, so it only does a cache
    // lookup (the rasters live on [AuraBloomState]) and cheap geometry; intensity is draw-time alpha.
    // `colors.from` / `colors.to` are read HERE so a track change rebuilds the rasters once;
    // `colors.progress` (the dissolve) and [AuraAmbientMotion.phase] (the drift) are read in
    // `onDrawBehind`, so both invalidate DRAW only.
    val w = size.width
    val h = size.height
    val a = intensity.coerceIn(0f, 1f)

    if (a <= 0f || w <= 0f || h <= 0f) {
        return@drawWithCache onDrawBehind { }
    }

    val (fromImages, toImages) = colors.lobesFor(colors.from, colors.to)
    val geometry = bloomLobeGeometry(w, h)

    onDrawBehind {
        val phase = AuraAmbientMotion.phase
        val p = colors.progress
        if (fromImages != null && p < 1f) {
            drawBloomLobes(fromImages, geometry, phase, (1f - p) * a)
            drawBloomLobes(toImages, geometry, phase, p * a)
        } else {
            drawBloomLobes(toImages, geometry, phase, a)
        }
    }
}
    // Row 342: the screen's content gets its own layer, so each drift step re-records ONLY the ground
    // and the lobes (three blits), never the rows, text and images of the screen above them.
    .graphicsLayer()

/** Ground fill + bloom in one modifier, for a screen root. */
fun Modifier.auraScreenBackground(
    colors: AuraBloomState,
    intensity: Float = 1f,
): Modifier = this
    .drawBehind { drawRect(AuraPalette.Ground) }
    .auraBloom(colors, intensity)

/**
 * HALLAZGO-055: longest side of the bloom raster, in px. The lobes are soft radial gradients —
 * low-frequency by design — so capping the raster costs no visible fidelity and keeps the
 * per-track allocation around a couple of MB even on QHD+ 120 Hz panels.
 */
const val BLOOM_RASTER_MAX_DIM = 1080

/**
 * HALLAZGO-055: downscale factor for the bloom raster: full resolution up to
 * [maxDim] on the longest side, proportionally smaller above it. Pure and JVM-testable.
 */
fun bloomRasterScale(width: Float, height: Float, maxDim: Int): Float {
    val largest = maxOf(width, height)
    if (largest <= 0f || maxDim <= 0) return 1f
    return (maxDim.toFloat() / largest).coerceAtMost(1f)
}

/** Side of one lobe raster, in px. A lobe is one soft radial gradient: 256 px upscaled is indistinguishable. */
private const val BLOOM_LOBE_RASTER_DIM = 256

/** The three lobes of [colors], each as a small full-strength radial gradient (colour → transparent). */
private fun rasterizeBloomLobes(colors: AuraBloomColors): List<ImageBitmap> =
    listOf(colors.topLeft, colors.topRight, colors.center).map { color ->
        val dim = BLOOM_LOBE_RASTER_DIM
        val radius = dim / 2f
        val image = ImageBitmap(dim, dim)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(image),
            Size(dim.toFloat(), dim.toFloat()),
        ) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(radius, radius),
                    radius = radius,
                ),
            )
        }
        image
    }

/**
 * Where the three lobes sit and how they move, for a [w]×[h] surface.
 *
 * Rest positions are the render's `.bl { inset: -12% -22% 48% }` band (owner 2026-10-06: it reaches 78 %
 * of the height): top-left, top-right and centre ellipses. Each lobe then orbits its rest point on its
 * own path — different directions and phases, integer frequencies so the cycle loops seamlessly — and
 * breathes ±10 % in size, so the cover's colours drift across each other instead of standing still
 * (row 342). The amplitudes are a fraction of the screen, so the motion reads the same on any panel.
 */
internal class BloomLobeGeometry(
    val centerX: FloatArray,
    val centerY: FloatArray,
    val radiusX: FloatArray,
    val radiusY: FloatArray,
    val amplitudeX: FloatArray,
    val amplitudeY: FloatArray,
)

internal fun bloomLobeGeometry(w: Float, h: Float): BloomLobeGeometry {
    val bandTop = -0.12f * h
    val bandHeight = 0.78f * h
    val bandLeft = -0.22f * w
    val bandWidth = 1.44f * w
    // x, y, x-radius, y-radius (fractions of the band) — the render's three radial gradients.
    val spec = arrayOf(
        floatArrayOf(0.26f, 0.20f, 0.44f, 0.38f),
        floatArrayOf(0.82f, 0.16f, 0.48f, 0.42f),
        floatArrayOf(0.50f, 0.50f, 0.52f, 0.38f),
    )
    return BloomLobeGeometry(
        centerX = FloatArray(3) { bandLeft + spec[it][0] * bandWidth },
        centerY = FloatArray(3) { bandTop + spec[it][1] * bandHeight },
        radiusX = FloatArray(3) { (spec[it][2] * bandWidth).coerceAtLeast(1f) },
        radiusY = FloatArray(3) { (spec[it][3] * bandHeight).coerceAtLeast(1f) },
        amplitudeX = floatArrayOf(0.18f * w, 0.16f * w, 0.24f * w),
        amplitudeY = floatArrayOf(0.10f * h, 0.12f * h, 0.14f * h),
    )
}

/** Per-lobe drift: (x frequency, y frequency, phase offset). Integer frequencies → seamless loop. */
private val LOBE_DRIFT = arrayOf(
    floatArrayOf(1f, 1f, 0f),
    floatArrayOf(-1f, 1f, 0.33f),
    floatArrayOf(1f, -2f, 0.66f),
)

/**
 * Offset of lobe [index] at [phase], as a fraction of its amplitude (-1..1 on each axis), plus its size
 * factor. Pure, for test.
 */
internal fun bloomLobeDrift(index: Int, phase: Float): Triple<Float, Float, Float> {
    val twoPi = 2f * kotlin.math.PI.toFloat()
    val drift = LOBE_DRIFT[index]
    val offset = drift[2]
    val dx = kotlin.math.sin(twoPi * (drift[0] * phase + offset))
    val dy = kotlin.math.cos(twoPi * (drift[1] * phase + offset))
    val breathe = 1f + 0.10f * kotlin.math.sin(twoPi * (2f * phase + offset))
    return Triple(dx, dy, breathe)
}

/** Three blits: the whole per-frame cost of the bloom besides a few sin/cos. */
private fun DrawScope.drawBloomLobes(
    images: List<ImageBitmap>,
    geometry: BloomLobeGeometry,
    phase: Float,
    alpha: Float,
) {
    if (alpha <= 0f) return
    for (i in 0 until 3) {
        val (dx, dy, breathe) = bloomLobeDrift(i, phase)
        val rx = geometry.radiusX[i] * breathe
        val ry = geometry.radiusY[i] * breathe
        val cx = geometry.centerX[i] + dx * geometry.amplitudeX[i]
        val cy = geometry.centerY[i] + dy * geometry.amplitudeY[i]
        drawImage(
            image = images[i],
            dstOffset = IntOffset((cx - rx).toInt(), (cy - ry).toInt()),
            dstSize = IntSize((rx * 2f).toInt().coerceAtLeast(1), (ry * 2f).toInt().coerceAtLeast(1)),
            alpha = alpha,
        )
    }
}
