/**
 * Aura Hi-Res Player (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package iad1tya.echo.music.ui.component

import android.content.Context
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.ui.component.backdrop.Backdrop
import iad1tya.echo.music.ui.component.backdrop.drawBackdrop
import iad1tya.echo.music.ui.component.backdrop.effects.blur
import iad1tya.echo.music.ui.component.backdrop.effects.colorControls
import iad1tya.echo.music.ui.component.backdrop.effects.lens
import iad1tya.echo.music.ui.component.backdrop.highlight.Highlight
import iad1tya.echo.music.ui.component.backdrop.shadow.Shadow
import iad1tya.echo.music.utils.DeviceCapabilities
import iad1tya.echo.music.utils.DeviceForm
import iad1tya.echo.music.utils.DeviceTier
import iad1tya.echo.music.utils.PerformanceMode

/**
 * User-configurable parameters of the liquid glass effect, sourced from DataStore
 * preferences in [iad1tya.echo.music.MainActivity] and distributed through
 * [LocalGlassEffectConfig].
 */
@Stable
data class GlassEffectConfig(
    val globalEnabled: Boolean = false,
    val vibrancy: Float = 1f,
    /** Blur in dp applied to glass pills. Defaults follow Kyant's Apple-matched recipe. */
    val blurRadius: Float = 8f,
    /** 0..1, mapped to 0..[LENS_MAX_DP] dp of lens refraction height. 0.5 = Apple's 24dp. */
    val lensHeight: Float = 0.5f,
    /** 0..1, mapped to 0..[LENS_MAX_DP] dp of lens refraction amount. 0.5 = Apple's 24dp. */
    val lensAmount: Float = 0.5f,
    val chromaticAberration: Boolean = true,
    val depthEffect: Boolean = true,
    /** [Color.Unspecified] means adaptive: light glass on light theme, dark on dark. */
    val surfaceTintColor: Color = Color.Unspecified,
    val surfaceOpacity: Float = 0.4f,
    val textColor: Color = Color.Unspecified,
    val playerEnabled: Boolean = true,
    val miniPlayerEnabled: Boolean = true,
    val navBarEnabled: Boolean = true,
) {
    /**
     * Whether the glass effect should be rendered for [component], taking the master
     * switch and the per-component switch into account.
     */
    fun isEnabledFor(component: GlassComponent): Boolean =
        globalEnabled && when (component) {
            GlassComponent.PLAYER -> playerEnabled
            GlassComponent.MINI_PLAYER -> miniPlayerEnabled
            GlassComponent.NAV_BAR -> navBarEnabled
        }

    /**
     * True when at least one glass surface that is actually DRAWN is on, so backdrop
     * recording (the full-screen layer in [iad1tya.echo.music.MainActivity]) can be
     * skipped even if the master switch is on.
     *
     * [playerEnabled] is deliberately NOT part of this term: nothing calls
     * `isEnabledFor(GlassComponent.PLAYER)`, so the full screen player never renders a
     * glass surface. Including it made the app pay for a full-screen backdrop layer
     * (GPU + battery) that nothing ever sampled.
     */
    val anyComponentEnabled: Boolean
        get() = miniPlayerEnabled || navBarEnabled
}

/** UI surfaces that can individually opt in or out of the liquid glass effect. */
enum class GlassComponent {
    PLAYER,
    MINI_PLAYER,
    NAV_BAR,
}

/**
 * Maximum lens refraction in dp when the 0..1 preference sliders are at 1. The
 * defaults (0.5) land on the 24dp height/amount used by the library author's
 * Apple-matched LiquidBottomTabs recipe.
 */
internal const val LENS_MAX_DP = 48f

/**
 * The full screen player uses a much heavier blur than the glass pills, matching
 * Apple Music where the now playing background is a deep-blurred material while
 * only the small controls are clear liquid glass.
 */
internal const val PLAYER_BLUR_MULTIPLIER = 4f

/** Lowest resolution fraction glass surfaces are rendered at (heavy blur hides it). */
internal const val MIN_GLASS_RESOLUTION_SCALE = 0.33f

/** Blur radius (dp) at or above which the minimum resolution scale is safe to use. */
internal const val FULL_QUALITY_BLUR_DP = 8f

/**
 * Resolution fraction at which a glass surface records and processes its backdrop.
 * Blur masks the upscaling, so the more blur, the lower the resolution can go: at
 * [FULL_QUALITY_BLUR_DP]+ dp of blur the surface renders at
 * [MIN_GLASS_RESOLUTION_SCALE]; with no blur it stays at full resolution so the
 * clear glass center remains crisp.
 */
fun glassResolutionScale(blurRadiusDp: Float): Float {
    val t = (blurRadiusDp / FULL_QUALITY_BLUR_DP).coerceIn(0f, 1f)
    return 1f - t * (1f - MIN_GLASS_RESOLUTION_SCALE)
}

/**
 * The backdrop blur pipeline requires [android.graphics.RenderEffect] on a
 * [android.graphics.RenderNode], which is available from Android 12 (API 31).
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/**
 * Aura eligibility gate for Liquid Glass (heat/battery rule): besides RenderEffect support
 * (API 31+), the device must be genuinely capable — RAW tier MID/HIGH (never LOW, and never
 * forced through Performance Mode's ULTRA), not a TV/car form factor (excluded from heavy
 * visuals), and Performance Mode must be OFF. When this returns false the Liquid Glass
 * option is hidden/marked unavailable in settings and the effect never renders, regardless
 * of stored preferences.
 */
fun isGlassEligible(context: Context): Boolean =
    isGlassSupported() &&
        DeviceCapabilities.tier(context).let { it == DeviceTier.MID || it == DeviceTier.HIGH } &&
        !DeviceForm.isTvOrCar(context) &&
        !PerformanceMode.isOn(context)

/**
 * Text/icon color to use on glass surfaces. Honors an explicit user color; otherwise
 * ADAPTIVE per theme — dark text on the light glass of a light theme, white on dark —
 * instead of upstream's hardcoded white default that was illegible on light themes.
 */
@Composable
fun glassContentColor(config: GlassEffectConfig): Color =
    if (config.textColor.isSpecified) {
        config.textColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFF1B1B1B)
    } else {
        Color.White
    }

/**
 * Maps the user-facing vibrancy preference (0..2, default 1) to a saturation multiplier.
 * A value of 1 matches the library's built-in vibrancy effect (saturation x1.5), 0 leaves
 * colors untouched and 2 doubles the saturation.
 */
fun glassSaturation(vibrancy: Float): Float = 1f + 0.5f * vibrancy.coerceIn(0f, 2f)

val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }

/** The backdrop content (app UI) that glass surfaces sample from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop].
 *
 * Applies the configured vibrancy, blur and lens refraction effects, then draws the
 * surface tint (theme-adaptive unless the user picked a color). Effects whose
 * parameters make them a no-op are skipped entirely to keep the RenderEffect chain
 * as short as possible, and the backdrop is processed at [glassResolutionScale] of
 * the surface resolution. Returns the receiver unchanged on devices without
 * RenderEffect support.
 *
 * [applyEdgeEffects] controls the edge treatment that makes small pills read as
 * physical glass: lens refraction, the specular highlight rim and the drop shadow.
 * It should be false for large surfaces such as the full screen player, where the
 * rim renders as a stray band of light.
 *
 * [blurRadiusDp] overrides the configured blur; the full screen player passes a
 * heavier value ([PLAYER_BLUR_MULTIPLIER]x) than the clear glass pills.
 *
 * [shape] is restricted to [CornerBasedShape] because the backdrop lens effect throws
 * [UnsupportedOperationException] for any other shape type.
 */
@Composable
fun Modifier.liquidGlass(
    config: GlassEffectConfig,
    shape: CornerBasedShape = RoundedCornerShape(0.dp),
    applyEdgeEffects: Boolean = true,
    blurRadiusDp: Float = config.blurRadius,
): Modifier {
    if (!isGlassSupported()) return this
    val backdrop = LocalAppBackdrop.current
    val density = LocalDensity.current
    val resolutionScale = glassResolutionScale(blurRadiusDp)
    // Pixel-sized effect parameters operate on the downscaled backdrop layer, so
    // they are pre-multiplied by the resolution scale to keep the same visual size.
    val blurPx = with(density) { blurRadiusDp.dp.toPx() } * resolutionScale
    val saturation = glassSaturation(config.vibrancy)
    val lensHeightPx = with(density) { (config.lensHeight * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val lensAmountPx = with(density) { (config.lensAmount * LENS_MAX_DP).dp.toPx() } * resolutionScale
    // Apple's glass is a light material on light content and dark on dark; honor an
    // explicit user color, otherwise follow the theme.
    val surfaceTintColor = if (config.surfaceTintColor.isSpecified) {
        config.surfaceTintColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (saturation != 1f) {
                colorControls(saturation = saturation)
            }
            if (blurPx > 0f) {
                blur(blurPx)
            }
            if (applyEdgeEffects &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (lensHeightPx > 0f || lensAmountPx > 0f)
            ) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = config.depthEffect,
                    chromaticAberration = config.chromaticAberration,
                )
            }
        },
        highlight = if (applyEdgeEffects) ({ Highlight.Default }) else null,
        shadow = if (applyEdgeEffects) ({ Shadow.Default }) else null,
        onDrawSurface = {
            if (config.surfaceOpacity > 0f) {
                drawRect(
                    color = surfaceTintColor.copy(alpha = config.surfaceOpacity),
                    size = size,
                )
            }
        },
        backdropScale = resolutionScale,
    )
}

/**
 * # El cristal líquido de SimpMusic, sobre la fontanería que Aura ya tiene
 *
 * 🔴 ORDEN DEL DUEÑO (2026-09-16): *"lo quiero exacto, pero como otra versión de liquid glass que se
 * pueda aplicar a las dos versiones de mi apariencia"*. PASO 2 de 3: el dibujo.
 *
 * Hermano de [liquidGlass], no sustituto: aquella sigue siendo el cristal de Aura y no cambia ni un
 * píxel. Esta es la variante de SimpMusic, y se diferencia en exactamente tres cosas — las tres
 * verificadas leyendo su código, no deducidas:
 *
 *  1. **Reacciona al tacto.** Al mantener pulsado, la superficie escala, la refracción se hace más
 *     profunda y un brillo radial sigue al dedo, volviendo con un muelle al soltar. Es lo que hace que
 *     parezca líquido y no cristal pintado. El gesto **no consume eventos**, así que los botones que
 *     haya dentro siguen recibiendo sus toques (ver `LiquidGlassSurface.kt`).
 *
 *  2. **La lente es proporcional a la píldora**, no un tamaño absoluto: altura `minDimension / 4` y
 *     cantidad `minDimension / 2`. Eso mantiene la refracción POR DEBAJO del eje medio, que es lo que
 *     evita la costura horizontal oscura en una superficie ancha — el fallo que SimpMusic documenta
 *     haber corregido. Por eso esta variante NO usa los deslizadores de altura/cantidad de lente: no es
 *     que los ignore por capricho, es que un valor absoluto en una píldora ancha rompe el efecto.
 *     `depthEffect` queda apagado por el mismo motivo (discontinuidad radial en el centro).
 *
 *  3. **Se adapta a la luminancia del fondo.** El desenfoque sube sobre fondo claro y baja sobre
 *     oscuro, y el oscurecido crece según se aclara el fondo (*"đục đen"*), que es lo que impide que el
 *     cristal se lave a blanco sobre una portada brillante. La curva está en [LiquidGlassMath], fijada
 *     por test.
 *
 * ## Qué respeta de Temas
 * Todos los ajustes del dueño que esta receta puede honrar sin romperse: el radio de desenfoque es la
 * BASE de la rampa de luminancia, la vibrancia entra como saturación igual que en [liquidGlass], el
 * tinte de superficie es el color del oscurecido, y **la opacidad de superficie es el tope de la rampa**
 * — o sea que su deslizador sigue mandando: marca cuánto llega a oscurecerse como mucho.
 *
 * @param backgroundLuminance 0..1 del fondo detrás de la superficie. 0.5 = neutro, que es lo que pasan
 * las superficies estáticas; quien pueda medir su fondo (el minirreproductor sobre la portada) pasa el
 * valor real y el cristal se adapta.
 */
@Composable
fun Modifier.liquidGlassInteractive(
    config: GlassEffectConfig,
    shape: CornerBasedShape = CircleShape,
    backgroundLuminance: Float = 0.5f,
    interactive: Boolean = true,
    pressedScale: Float = 1.12f,
    minScrim: Float = 0.12f,
): Modifier {
    if (!isGlassSupported()) return this
    val backdrop = LocalAppBackdrop.current
    val density = LocalDensity.current
    val press = rememberGlassPressState()
    val resolutionScale = glassResolutionScale(config.blurRadius)
    // Igual que en [liquidGlass]: los parámetros en píxeles actúan sobre la capa reducida, así que se
    // premultiplican por la escala para que el tamaño visual no cambie.
    val baseBlurPx = with(density) { config.blurRadius.dp.toPx() } * resolutionScale
    val pressBlurPx = with(density) { 2.dp.toPx() } * resolutionScale
    val saturation = glassSaturation(config.vibrancy)
    val scrimColor = if (config.surfaceTintColor.isSpecified) {
        config.surfaceTintColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.White
    } else {
        Color.Black
    }
    // El deslizador del usuario es el TOPE de la rampa, nunca un valor fijo. Si lo bajó por debajo del
    // suelo, el suelo cede: mandar él es más importante que mi mínimo.
    val maxScrim = config.surfaceOpacity.coerceIn(0f, 1f)
    val floor = minScrim.coerceAtMost(maxScrim)

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (saturation != 1f) {
                colorControls(saturation = saturation)
            }
            val blurPx = LiquidGlassMath.blurRadiusPx(
                basePx = baseBlurPx,
                luminance = backgroundLuminance,
                pressPx = pressBlurPx * (if (interactive) press.pressProgress else 0f),
            )
            if (blurPx > 0f) {
                blur(blurPx)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = LiquidGlassMath.lensHeightPx(
                        minDimension = size.minDimension,
                        pressPx = pressBlurPx * (if (interactive) press.pressProgress else 0f),
                    ),
                    refractionAmount = LiquidGlassMath.lensAmountPx(size.minDimension),
                    // Apagado a propósito: ver el punto 2 de la documentación de arriba.
                    depthEffect = false,
                    chromaticAberration = config.chromaticAberration,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = {
            val darken = LiquidGlassMath.scrimAlpha(backgroundLuminance, floor, maxScrim)
            if (darken > 0f) {
                drawRect(color = scrimColor.copy(alpha = darken), size = size)
            }
            val p = if (interactive) press.pressProgress else 0f
            if (p > 0f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.18f * p), Color.Transparent),
                        center = press.touchPosition.takeIf { it != Offset.Zero }
                            ?: Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 1.2f,
                    ),
                    blendMode = BlendMode.Plus,
                )
            }
        },
        layerBlock = if (interactive) {
            {
                val scale = 1f + (pressedScale - 1f) * press.pressProgress
                scaleX = scale
                scaleY = scale
            }
        } else {
            null
        },
        backdropScale = resolutionScale,
    ).then(
        if (interactive) {
            Modifier.pointerInput(press) { press.observePress(this) }
        } else {
            Modifier
        },
    )
}
