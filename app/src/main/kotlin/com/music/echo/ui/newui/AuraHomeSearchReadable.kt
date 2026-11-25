package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Longest line a block of prose may take on Inicio / Novedades (registry row 877). An unfolded Fold is ~840 dp wide and a
 * tablet far more: an explanatory sentence across all of it is read in one long line. 560 dp is wider than any phone row
 * (a phone or the Fold's cover is under 430 dp), so this only ever bites on a medium or expanded pane.
 */
internal val AURA_READABLE_MAX_WIDTH = 560.dp

/**
 * Centres a block of prose and caps it at [AURA_READABLE_MAX_WIDTH]. On a row narrower than that nothing moves: the text
 * wraps at the same width as before and sits centred where its centred column already put it.
 */
internal fun Modifier.auraReadableWidth(): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = AURA_READABLE_MAX_WIDTH)
