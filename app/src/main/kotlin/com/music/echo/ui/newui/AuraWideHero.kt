package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The WIDE top of an album / EP / single / playlist page (an unfolded foldable, a tablet — pane ≥
 * [AURA_WIDE_HERO_MIN_WIDTH]): the cover on the LEFT, the title, artist and actions on its RIGHT, the track list right
 * below. It is Apple Music's iPad album page.
 *
 * Owner 2026-10-18 (Fold, unsplit), twice. First: the square cover cropped into a banner blew a face up and cut a
 * logo in half. Then, after it drew the cover whole and centred: *«no me gusta cómo se ven las portadas de los álbumes
 * en modo plegable»* — a small cover floating in a 400 dp band, a hole of empty ground around it, and the title and
 * buttons stacked far below so the first song sat off the first screen. Here the cover and the information share the
 * same row, so everything the page is for is in view without scrolling.
 *
 * Layers: a soft, dimmed copy of the cover as the backdrop (fixed height, dissolving into the page — the dissolve
 * covers the BACKDROP only, never the content), and the row on top. No parallax and no haze sampling: nothing here
 * redraws per frame while the list scrolls (heat). The cover's frame line is softened by its own size
 * ([auraArtworkEdgeStrength]): a big cover with a bright outline reads as a white box, which is what the owner pointed out.
 */
internal val AURA_WIDE_TOP_COVER = 260.dp

/** Height of the soft backdrop behind the row. */
private val AURA_WIDE_TOP_BACKDROP_HEIGHT = 380.dp

/** The row starts below the status bar and the back / share / more row. */
private val AURA_WIDE_TOP_PADDING_TOP = 84.dp

@Composable
internal fun AuraWideTop(
    thumbnailUrl: String?,
    seed: String?,
    lean: Boolean,
    modifier: Modifier = Modifier,
    /** The shared element of the page (the album's hero transition) belongs to the visible square. */
    coverModifier: Modifier = Modifier,
    /** Drawn over the cover square (the album's animated artwork / canvas video) — nothing is lost in the wide layout. */
    coverOverlay: (@Composable BoxScope.() -> Unit)? = null,
    info: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val topWidth = maxWidth
        Box(modifier = Modifier.fillMaxWidth()) {
            // The backdrop: its own box, so the dissolve fades IT into the page and never the information.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AURA_WIDE_TOP_BACKDROP_HEIGHT)
                    .auraHeroDissolve(),
            ) {
                AuraCover(
                    thumbnailUrl = thumbnailUrl,
                    size = topWidth,
                    modifier = Modifier
                        .then(if (lean) Modifier else Modifier.blur(32.dp))
                        .graphicsLayer { alpha = 0.50f },
                    seed = seed,
                    shape = RectangleShape,
                    decodeTo = if (lean) 320 else 640,
                    ratio = topWidth / AURA_WIDE_TOP_BACKDROP_HEIGHT,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AuraSpacing.Gutter,
                        end = AuraSpacing.Gutter,
                        top = AURA_WIDE_TOP_PADDING_TOP,
                        bottom = 20.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AuraCover(
                    thumbnailUrl = thumbnailUrl,
                    size = AURA_WIDE_TOP_COVER,
                    modifier = coverModifier,
                    seed = seed,
                    shape = AuraShapes.Artwork,
                    decodeTo = if (lean) 640 else 900,
                    ratio = 1f,
                    overlay = coverOverlay,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    content = info,
                )
            }
        }
    }
}
