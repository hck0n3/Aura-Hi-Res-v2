package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The WIDE hero of an album / EP / single / playlist page (an unfolded foldable, a tablet — pane ≥ [AURA_WIDE_HERO_MIN_WIDTH]).
 *
 * Owner screenshot 2026-10-18 (Fold, unsplit): the square cover was cropped into a full-width banner, so a face was blown
 * up and a logo cut in half. Here the cover is drawn WHOLE — a [AURA_WIDE_HERO_COVER] square with the Apple Music frame —
 * centred over a dimmed, softened copy of itself that fills the band (Apple Music's iPad album page). The phone hero is
 * unchanged.
 */
internal val AURA_WIDE_HERO_HEIGHT = 400.dp
internal val AURA_WIDE_HERO_COVER = 192.dp

/** The dissolve into the page is shorter (30 %) so the whole cover stays above it. */
internal const val AURA_WIDE_HERO_DISSOLVE = 0.30f

/** Below the status bar and the back / share / more row. */
private val AURA_WIDE_HERO_COVER_TOP = 84.dp

/**
 * The two layers of the wide hero: the soft backdrop and the cover on top of it. [coverModifier] carries the shared
 * element of the page (the album's hero transition), which belongs to the visible square, not to the backdrop.
 * Lean mode (registry 395) draws the backdrop without the blur and decodes smaller.
 */
@Composable
internal fun BoxScope.AuraWideHeroCover(
    thumbnailUrl: String?,
    heroWidth: Dp,
    seed: String?,
    lean: Boolean,
    coverModifier: Modifier = Modifier,
) {
    AuraCover(
        thumbnailUrl = thumbnailUrl,
        size = heroWidth,
        modifier = Modifier
            .then(if (lean) Modifier else Modifier.blur(28.dp))
            .graphicsLayer { alpha = 0.55f },
        seed = seed,
        shape = RectangleShape,
        decodeTo = if (lean) 320 else 640,
        ratio = heroWidth / AURA_WIDE_HERO_HEIGHT,
    )
    AuraCover(
        thumbnailUrl = thumbnailUrl,
        size = AURA_WIDE_HERO_COVER,
        modifier = coverModifier
            .align(Alignment.TopCenter)
            .padding(top = AURA_WIDE_HERO_COVER_TOP),
        seed = seed,
        shape = AuraShapes.Artwork,
        decodeTo = if (lean) 640 else 900,
        ratio = 1f,
    )
}
