package iad1tya.echo.music.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Same value as the GMS flavor's (the callers size their outer box with it). */
val CAST_TOUCH_TARGET = 64.dp

/**
 * Stub CastButton for F-Droid builds.
 * Does not render anything - Cast not available without GMS.
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    // No-op: Cast not available in FOSS build
}
