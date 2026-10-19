package iad1tya.echo.music.ui.newui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.ui.utils.isWideLayoutWindow
import iad1tya.echo.music.utils.rememberLeanMode

/*
 * THE FLOATING-WINDOW RECIPE FOR WIDE WINDOWS (an unfolded foldable, a tablet).
 *
 * Owner 2026-10-19: *«todas las ventanas que salen flotantes en modo plegable… las veo muy toscas y básicas para lo que
 * es el programa y no las veo estéticas en modo plegable desplegado»*. On a phone a menu is a sheet that rises from the
 * bottom edge: the right shape for a thumb. On a 750 dp-wide pane the same sheet is a long slab glued to the bottom with
 * empty air on both sides. In a wide window every floating window is therefore a CARD:
 *
 *  - floating and centred, with a margin from the edges (and the system bars / keyboard), never attached to the bottom;
 *  - all four corners large and the same ([AuraWindowShapeWide]), no top-only sheet corners, and NO drag handle (it is
 *    not a bottom sheet any more: it closes with the scrim, with back, or with its own buttons);
 *  - a soft shadow OUTSIDE the card ([auraWindowShadow]) and a 1 dp edge that is lighter at the top
 *    ([auraWindowBorder]), the Apple Music cover edge the app already uses on artwork;
 *  - it arrives with a short rise + a 0.96 → 1 scale + a fade on a spring without bounce, and leaves with a quick fade.
 *
 * ONE recipe, three hosts (BottomSheetMenu, BottomSheetPage, AuraInWindowDialog): they read the same constants and call
 * the same helpers, so the windows cannot drift apart screen by screen. A PHONE (or a phone on its side, which is wide but
 * short) is untouched: [rememberAuraWideWindows] is the same width + height test the rest of the wide chrome uses.
 *
 * The glass itself (tint, transparency, blur) is NOT decided here — that is `floatingWindowGlass` / the FLOATING_WINDOW_*
 * constants. This file only decides the SHAPE, the EDGE, the SHADOW, the SIZE and the MOTION of the card.
 */

/** Is the current window big enough for the wide floating-window recipe? Same rule as the wide shell. */
@Composable
fun rememberAuraWideWindows(): Boolean {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    return remember(width, height) { isWideLayoutWindow(width.toFloat(), height.toFloat()) }
}

/** All four corners, large: a card, not a sheet. */
val AuraWindowShapeWide = RoundedCornerShape(24.dp)

/** Width of a menu card in a wide window — short menus do not need the 560 dp of a long list. */
const val AuraWideMenuWidthDp = 500

/** Width of a list-like page (add to playlist, long pickers) in a wide window. */
const val AuraWidePageWidthDp = 560

/** Width of a compact sheet (the audio output card, the download sheet) in a wide window. */
const val AuraWideCompactWidthDp = 460

/** Space kept between a wide card and the edges of the window (the system bars and the keyboard come on top). */
val AuraWideWindowMargin = 24.dp

/** The height a wide card may take: it is a card floating over the app, so the app has to remain visible around it. */
const val AuraWideWindowMaxHeightFraction = 0.80f

/**
 * Wraps the placement of a wide card: the area it floats in already discounts the status/navigation bars, the display cut-out
 * and the keyboard, so the card is never hidden behind them and never touches the window edge.
 */
fun Modifier.auraWideWindowArea(): Modifier =
    this
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(AuraWideWindowMargin)

/**
 * The entry of a floating window. Wide: a short rise (1/14 of its own height), a 0.96 → 1 scale and a fade, all on a spring
 * without bounce, so the card settles instead of sliding in from the screen edge. Phone: the slide from the bottom it always
 * had. With the performance mode on (low-end phones) a wide window just appears: no motion at all.
 */
@Composable
fun rememberAuraWindowEnter(wide: Boolean): EnterTransition {
    val lean = rememberLeanMode()
    return remember(wide, lean) {
        when {
            wide && lean -> EnterTransition.None
            wide -> fadeIn(animationSpec = tween(160)) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                ) +
                slideInVertically(
                    animationSpec = spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                ) { it / 14 }
            else -> slideInVertically(animationSpec = tween(220)) { it } + fadeIn(animationSpec = tween(180))
        }
    }
}

/** The exit of a floating window: wide leaves with a quick fade and a hair of scale; phone slides down as before. */
@Composable
fun rememberAuraWindowExit(wide: Boolean): ExitTransition {
    val lean = rememberLeanMode()
    return remember(wide, lean) {
        when {
            wide && lean -> ExitTransition.None
            wide -> fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.98f, animationSpec = tween(140))
            else -> slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(160))
        }
    }
}

/**
 * The 1 dp edge of a wide card: the app's hairline ([AuraPalette.SurfaceLine]) with a lighter top, like the edge of a piece
 * of glass catching light from above. Reads the palette in composition (it follows the user's own outline colour).
 */
@Composable
fun Modifier.auraWindowBorder(shape: Shape): Modifier {
    val line = AuraPalette.SurfaceLine
    val brush = remember(line) {
        Brush.verticalGradient(
            listOf(
                lerp(line, Color.White, 0.55f).copy(alpha = (line.alpha + 0.16f).coerceAtMost(0.34f)),
                line,
            ),
        )
    }
    return this.border(1.dp, brush, shape)
}

/**
 * A soft shadow around a wide card, drawn OUTSIDE its outline only.
 *
 * Why it is not `Modifier.shadow`: the card is translucent glass. An elevation shadow is a platform effect that sits in its
 * own layer under the node and would sit between the glass and whatever the glass samples; this one is painted into the
 * parent's layer, clipped so that nothing is drawn INSIDE the outline (a plain dark fill under translucent glass would dim
 * the very colours the glass is meant to show). Three expanded copies of the outline, pushed down a little, at low alpha.
 * Built once per size ([drawWithCache]) and not animated: it costs nothing while the card is open.
 *
 * It goes BEFORE the clip of the card in the modifier chain, so that the clip does not cut it.
 */
fun Modifier.auraWindowShadow(shape: Shape, enabled: Boolean = true): Modifier =
    if (!enabled) this else this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        val layers = floatArrayOf(10.dp.toPx(), 22.dp.toPx(), 40.dp.toPx())
        val alphas = floatArrayOf(0.10f, 0.07f, 0.045f)
        val lift = 10.dp.toPx()
        onDrawBehind {
            if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
            clipPath(path, clipOp = ClipOp.Difference) {
                for (i in layers.indices) {
                    val grow = layers[i]
                    translate(left = 0f, top = lift) {
                        scale(
                            scaleX = 1f + 2f * grow / size.width,
                            scaleY = 1f + 2f * grow / size.height,
                            pivot = Offset(size.width / 2f, size.height / 2f),
                        ) {
                            drawPath(path, Color.Black.copy(alpha = alphas[i]))
                        }
                    }
                }
            }
        }
    }
