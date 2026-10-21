package iad1tya.echo.music.ui.newui

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.constants.LiquidGlassGlobalEnabledKey
import iad1tya.echo.music.utils.DeviceCapabilities
import iad1tya.echo.music.utils.DeviceForm
import iad1tya.echo.music.utils.DeviceTier
import iad1tya.echo.music.utils.PrefsBridge
import iad1tya.echo.music.utils.isWindowBlurSupported
import iad1tya.echo.music.utils.rememberPreference
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp

/**
 * Alpha of the veil behind a premium floating plate (sheet scrim / dialog dim). With real
 * window blur the light 0.22 veil is enough — the background is softened, not hidden. When
 * the framework does NOT blur (Samsung, HALLAZGO-034 H1) the same veil leaves sharp,
 * readable content around the plate: the "transparente sin blur" the owner reported. The
 * veil then goes nearly opaque so the plate reads as a surface, not a hole.
 *
 * `glassForced` (owner directive 2026-08-29): with the Liquid Glass master switch ON the
 * owner ACCEPTS the sharp background — he asked for the translucent look on Samsung, not
 * for the near-opaque veil — so the light 0.22 veil is kept even without window blur.
 * Callers pass the switch state; the pure function stays testable.
 */
fun auraFloatingScrimAlpha(windowBlurSupported: Boolean, glassForced: Boolean = false): Float =
    if (windowBlurSupported || glassForced) 0.22f else 0.88f

/** Dialog-window dim amount paired with [auraFloatingScrimAlpha] (HALLAZGO-034 H1). */
fun auraDialogDimAmount(windowBlurSupported: Boolean, glassForced: Boolean = false): Float =
    if (windowBlurSupported || glassForced) 0.22f else 0.60f

/**
 * Non-composable read of the Liquid Glass master switch for the veil/dim functions above.
 * PrefsBridge is a process-wide volatile snapshot — never a blocking DataStore read on the
 * UI thread; before the first emission (cold start) it yields the safe default (false = the
 * HALLAZGO-034 opaque veil), which is what shipped before this hook existed.
 */
private fun glassForcedPreference(): Boolean =
    PrefsBridge.peek(LiquidGlassGlobalEnabledKey) ?: false

/**
 * Shared floating chrome for New UI overlays (dialogs, sheets, menus).
 *
 * Owner: everything should feel like the same premium shell. Overlays that are not full Aura
 * screens use a **frosted translucent plate** so what is behind shows through — never a flat
 * Material card, never a naked transparent hole.
 *
 * Real translucency comes from [AuraPalette.FrostFill] (true alpha) + a lighter dim. On capable
 * devices whose framework actually blurs (API 31+ AND `config_windowBlurEnabled` — Samsung
 * ships it false, see [isWindowBlurSupported] / HALLAZGO-034), dialog windows also get system
 * backdrop blur; everywhere else [AuraPalette.FrostFill] falls back to an opaque plate. Account
 * sheet uses the same frost plate ([SettingDialoge] → [AuraFloatingSurface]).
 */
/**
 * El TOPE de altura de una hoja que sube desde abajo — **tope, no objetivo**.
 *
 * 🔴 Reporte del dueño (2026-09-17): *"las ventanas que se desplazan hacia arriba, por ejemplo
 * cuando toco el botón cast, la ventana se desplaza muy alto, y quiero que se desplacen según su
 * contenido: si su contenido es poco que se desplace solo hasta mostrar todo el contenido y no más
 * […] y así sucesivamente con las ventanas que se desplazan de esa manera"*.
 *
 * Tenía razón y la causa era literal: los contenedores compartidos de hojas y menús pedían
 * `fillMaxHeight(fraction = 0.85f)` y dentro un `Column(fillMaxSize())`. Eso no es "hasta donde llegue
 * el contenido", es **siempre el 85 % de la pantalla**, tenga la hoja tres opciones o treinta — y la
 * tarjeta de salida de audio, que es la que él nombra, tiene tres.
 *
 * El arreglo es cambiar el 85 % de objetivo a límite: la hoja mide lo que mide su contenido y solo deja
 * de crecer al llegar aquí. Nada se pierde por arriba: un `LazyColumn` o un `verticalScroll` dentro
 * recibe este valor como altura máxima acotada y se desplaza igual que antes; y una hoja larga sin
 * desplazamiento se recorta en el mismo sitio donde ya se recortaba.
 *
 * El 85 % se conserva tal cual porque es lo que hay que dejar libre para que se vea que hay app
 * detrás y para que el toque fuera cierre la hoja.
 */
const val AuraSheetMaxHeightFraction = 0.85f

/**
 * Ancho máximo de las hojas inferiores y de los diálogos (dueño, 2026-10-17, plegable abierto: «estas
 * ventanas que abren no me parecen bien estéticamente»). Antes ocupaban TODO el ancho de la ventana
 * (~750 dp en un plegable abierto): el contenido quedaba pegado a la izquierda y vacío a la derecha. Es un
 * TOPE: en un móvil normal (< 560 dp) no cambia nada, y en pantallas anchas la hoja queda centrada.
 */
const val AuraSheetMaxWidthDp = 560

/** La hoja que ocupa toda la pantalla (la búsqueda rápida) admite algo más de ancho. */
const val AuraFullScreenSheetMaxWidthDp = 720

/** Diálogos centrados: tarjetas, no láminas. */
const val AuraDialogMaxWidthDp = 520

/**
 * El tope en dp para una ventana de [screenHeightDp] de alto.
 *
 * Separado de la función composable para poder fijarlo por test: lo que hay que impedir que vuelva no
 * es el cálculo, es que alguien cambie el tope por un alto FIJO y todas las hojas vuelvan a medir lo
 * mismo pase lo que pase con su contenido.
 */
fun auraSheetMaxHeightDp(screenHeightDp: Int): Float = screenHeightDp * AuraSheetMaxHeightFraction

/** El [AuraSheetMaxHeightFraction] de la altura de la ventana, en dp. */
@Composable
fun rememberAuraSheetMaxHeight(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return remember(screenHeightDp) { auraSheetMaxHeightDp(screenHeightDp).dp }
}

object AuraFloating {
    val BlurRadiusPx = 56
    val Shape: CornerBasedShape get() = AuraShapes.Card
}

/**
 * When true, list rows inside this overlay use a transparent resting fill so they sit ON the frost
 * plate instead of painting opaque [AuraPalette.Ground] cards on top of it.
 */
val LocalAuraFloatingChrome = staticCompositionLocalOf { false }

@Composable
fun rememberAuraFrostBlurAllowed(): Boolean {
    val (highPerf) = rememberPreference(HighPerformanceModeKey, false)
    val context = LocalContext.current
    return remember(highPerf, context) {
        // isWindowBlurSupported, NOT isGlassSupported: the RenderEffect pipeline works on
        // Samsung, but window blur is disabled there at the framework level (HALLAZGO-034).
        !highPerf &&
            isWindowBlurSupported() &&
            DeviceCapabilities.tier(context).let { it == DeviceTier.MID || it == DeviceTier.HIGH } &&
            !DeviceForm.isTvOrCar(context)
    }
}

/**
 * Makes a Compose Dialog window see-through and optionally frosted (system blur of whatever is
 * behind the window). No-op when New UI premium path is off.
 */
@Composable
fun AuraDialogWindowEffects(enabled: Boolean) {
    val view = LocalView.current
    val blurOk = rememberAuraFrostBlurAllowed()
    DisposableEffect(enabled, blurOk, view) {
        if (!enabled) return@DisposableEffect onDispose { }
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            // HALLAZGO-034 H1: dim keyed on REAL blur support (not blurOk, which also folds
            // in highPerf/tier gates) — without blur the dim must nearly hide the background.
            window.setDimAmount(auraDialogDimAmount(isWindowBlurSupported(), glassForcedPreference()))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurOk) {
                try {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply {
                        blurBehindRadius = AuraFloating.BlurRadiusPx
                    }
                    window.setBackgroundBlurRadius(AuraFloating.BlurRadiusPx)
                } catch (_: Throwable) {
                    // OEM / TV builds may reject blur flags; translucency still applies.
                }
            }
        }
        onDispose { }
    }
}

@Composable
fun AuraFrostWindowIfPremium() {
    val skin = rememberAuraPanelSkin()
    AuraDialogWindowEffects(enabled = skin.enabled && skin.darkGround)
}

/**
 * Premium floating plate: translucent frost + hairline + floating-chrome locals for children.
 */
@Composable
fun AuraFloatingSurface(
    modifier: Modifier = Modifier,
    shape: Shape = AuraFloating.Shape,
    content: @Composable BoxScope.() -> Unit,
) {
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    if (!premium) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            content = { Box { content() } },
        )
        return
    }

    Box(modifier = modifier.clip(shape)) {
        Box(
            Modifier
                .matchParentSize()
                // Fila 495: the SAME translucent plate as the ⋯ sheets ([AuraPalette.FloatingWindowPlate]); the opaque
                // FrostFill made every dialog a solid slab next to them.
                .background(AuraPalette.FloatingWindowPlate, shape),
        )
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, AuraPalette.SurfaceLine),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {}
        CompositionLocalProvider(LocalAuraFloatingChrome provides true) {
            Box(content = content)
        }
    }
}

/**
 * Fila 495 — the hairline of every pop-up MENU (dropdowns), the same line the sheets and dialogs draw ([AuraPalette.SurfaceLine]).
 * Owner 2026-10-21: *«las ventanas flotantes quiero que tengan el diseño de las opciones de los tres puntos»*: the ⋯ menu is
 * the translucent cover-coloured plate with this line; the dropdowns still painted the opaque [AuraPalette.FrostFill] slab.
 */
val AuraMenuBorder: BorderStroke
    get() = BorderStroke(1.dp, AuraPalette.SurfaceLine)

@Composable
fun auraFloatingContainerColor(): Color {
    val skin = rememberAuraPanelSkin()
    // Owner 2026-10-19: the player's download sheet (a Material sheet, painted with THIS colour) stopped being transparent,
    // and every window must stay see-through: the translucent window plate, not the opaque FrostFill.
    return if (skin.enabled && skin.darkGround) AuraPalette.FloatingWindowPlate
    else MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
fun auraFloatingContentColor(): Color {
    val skin = rememberAuraPanelSkin()
    return if (skin.enabled && skin.darkGround) AuraPalette.OnGround
    else MaterialTheme.colorScheme.onSurface
}

@Composable
fun auraFloatingScrimColor(): Color {
    val skin = rememberAuraPanelSkin()
    return if (skin.enabled && skin.darkGround) {
        // HALLAZGO-034 H1: every sheet scrim funnels through here, so raising the veil when
        // the framework cannot blur covers all sheets/menus in one place. glassForced =
        // the Liquid Glass master switch (owner directive 2026-08-29): with the effect ON
        // the owner keeps the light veil on no-blur OEMs too.
        AuraPalette.Teal.copy(alpha = 0.10f).compositeOver(Color.Black)
            .copy(alpha = auraFloatingScrimAlpha(isWindowBlurSupported(), glassForcedPreference()))
    } else MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
}

@Composable
fun auraFloatingTextFieldColors() =
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AuraPalette.Teal,
        unfocusedBorderColor = AuraPalette.SurfaceLine,
        focusedContainerColor = AuraPalette.SurfaceFill,
        unfocusedContainerColor = AuraPalette.SurfaceFill,
        cursorColor = AuraPalette.Teal,
        focusedTextColor = AuraPalette.OnGround,
        unfocusedTextColor = AuraPalette.OnGround,
        focusedPlaceholderColor = AuraPalette.OnGroundMuted,
        unfocusedPlaceholderColor = AuraPalette.OnGroundMuted,
        focusedLeadingIconColor = AuraPalette.Teal,
        unfocusedLeadingIconColor = AuraPalette.OnGroundMuted,
        focusedTrailingIconColor = AuraPalette.OnGroundMuted,
        unfocusedTrailingIconColor = AuraPalette.OnGroundMuted,
    )
