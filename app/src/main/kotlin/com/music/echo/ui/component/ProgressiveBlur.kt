package iad1tya.echo.music.ui.component

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * # Progressive blur: the mask half
 *
 * 🔴 OWNER REQUEST (2026-09-16), about SimpMusic's artist page: *"me gusta de aquí que cuando uno va al
 * artista, la parte de abajo de la portada del artista está desenfocada para que el título del artista y
 * suscriptores y visualizaciones se vea mejor; se ve más premium"*.
 *
 * A blur cannot be applied to PART of a node — `Modifier.blur` and `RenderEffect` blur the whole layer.
 * The way this is done everywhere (and the way the rest of this codebase already masks gradients, e.g.
 * `LyricsV2.kt:214` and `Player.kt:1896`) is to draw a SECOND copy of the image, blur that copy whole,
 * and then mask it so it only exists where you want it. This modifier is that mask: it clips the band,
 * forces an offscreen layer (required — `BlendMode.DstIn` against the window would punch a hole through
 * everything below) and erases the copy upward with a vertical alpha ramp.
 *
 * `DstIn` keeps the destination where the source's alpha is high, so the ramp reads bottom-up: opaque
 * black at the bottom (blurred copy fully visible) fading to transparent at the top (copy gone). The
 * seam is therefore never a line, which is the whole point — a hard edge is what makes a cheap blur
 * look cheap.
 *
 * ## Why this is shared and not written twice
 * The owner wants it on the artist page of BOTH interfaces. Two copies of a visual effect drift the
 * moment one is tuned, so the ramp lives here once. Callers supply their own blurred copy because only
 * they know how their cover is laid out.
 *
 * @see BlurBandSupported — below API 31 `Modifier.blur` silently does nothing, so callers skip the
 * whole band (a second decode and an offscreen layer paid for an invisible result) and keep whatever
 * scrim they already draw.
 */
fun Modifier.progressiveBottomBlurMask(): Modifier = this
    .clipToBounds()
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.35f to Color.Black.copy(alpha = 0.35f),
                0.7f to Color.Black.copy(alpha = 0.85f),
                1f to Color.Black,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Whether a real blur is available. `Modifier.blur` is a no-op below API 31 (`minSdk` here is 26), so
 * this is a capability check and not a version check dressed up as one: on an older phone the caller
 * must fall back to its gradient scrim instead of drawing an unblurred second cover on top of the
 * first, which would look like a rendering bug.
 */
val BlurBandSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
