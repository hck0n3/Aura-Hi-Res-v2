package iad1tya.echo.music.ui.newui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/**
 * The cover's colours for the CLASSIC pages the shell hosts (every `settings/…` sub-screen).
 *
 * 🔴 Dueño (2026-10-15, imagen 2): *«cada vez que me meto a un apartado de los ajustes no coinciden los colores
 * de la portada de la app en ellos»*. [auraShellScreenBackground] gave those pages the cover's ground and bloom;
 * this gives them the cover's ACCENTS and ink. The two never matched before for a concrete reason: the app theme's
 * accent comes from its own extraction of the artwork (`extractThemeColor`), the Aura screens' from
 * [AuraCoverMix]. Two pipelines, two different blues for the same cover.
 *
 * Inside the scope `MaterialTheme.colorScheme` is the app's own scheme with:
 *  · the accent families (primary / secondary / tertiary and their containers) = [AuraPalette.Teal] / [AuraPalette.Blue]
 *    / [AuraPalette.Violet] — the very values every Aura screen draws with;
 *  · the surfaces on the LIVE ground ([AuraPalette.Ground], eased on a track change) and the ink [AuraPalette.OnGround];
 *  · `background` and `surface` transparent, so the bloom the shell paints under the page shows through.
 *
 * Scoped, not global: the app theme (and the user's accent pick, Dynamic theme and reset button on the classic
 * screens elsewhere) is untouched, and there is no feedback loop with [AuraPaletteSync], which reads the app scheme.
 *
 * [enabled] false hands the OUTER scheme straight back: the group structure is the same either way, so flipping it
 * (leaving Ajustes) never tears down the NavHost inside.
 */
@Composable
fun AuraCoverThemeScope(enabled: Boolean, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val ground = AuraPalette.Ground
    val teal = AuraPalette.Teal
    val blue = AuraPalette.Blue
    val violet = AuraPalette.Violet
    val ink = AuraPalette.OnGround
    val onAccent = AuraPalette.OnAccent
    val scheme = remember(enabled, base, ground, teal, blue, violet, ink, onAccent) {
        if (!enabled) {
            base
        } else {
            auraCoverScheme(base, ground, teal, blue, violet, ink, onAccent)
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/** The scheme [AuraCoverThemeScope] provides. Pure on colours, so it can be pinned by a test. */
internal fun auraCoverScheme(
    base: androidx.compose.material3.ColorScheme,
    ground: Color,
    teal: Color,
    blue: Color,
    violet: Color,
    ink: Color,
    onAccent: Color,
): androidx.compose.material3.ColorScheme {
    val card = AuraPalette.SurfaceFill.compositeOver(ground)
    val cardHigh = AuraPalette.SurfaceLine.compositeOver(ground)
    val cardHighest = AuraPalette.TrackEmpty.compositeOver(ground)
    return base.copy(
        primary = teal,
        onPrimary = onAccent,
        primaryContainer = lerp(ground, teal, 0.30f),
        onPrimaryContainer = ink,
        secondary = blue,
        onSecondary = onAccent,
        secondaryContainer = lerp(ground, blue, 0.26f),
        onSecondaryContainer = ink,
        tertiary = violet,
        onTertiary = onAccent,
        tertiaryContainer = lerp(ground, violet, 0.26f),
        onTertiaryContainer = ink,
        surfaceTint = teal,
        background = Color.Transparent,
        surface = Color.Transparent,
        surfaceDim = ground,
        surfaceContainerLowest = ground,
        surfaceContainerLow = AuraPalette.GroundRaised,
        surfaceContainer = card,
        surfaceContainerHigh = cardHigh,
        surfaceContainerHighest = cardHighest,
        surfaceBright = cardHighest,
        surfaceVariant = cardHigh,
        onBackground = ink,
        onSurface = ink,
        onSurfaceVariant = ink.copy(alpha = 0.70f).compositeOver(ground),
        outlineVariant = cardHigh,
    )
}
