package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * # Pantalla ancha de Aura — plegable abierto, tableta
 *
 * 🔴 Dueño (2026-10-15): *«en el modo plegable no me gusta la interfaz, no se parece a la normal»* y, con
 * capturas de Spotify: *«quiero solo la barra flotante de abajo»*, la barra de abajo que comparte fila con el
 * minirreproductor al sonar, y *«si toco el minirreproductor se expande el reproductor en pantalla dividida…
 * pero podemos elegir si no queremos verlo dividido, con el botón de arriba a la izquierda»*.
 *
 * ## Qué hay (todo en ventanas ≥ 600 dp, y solo con la interfaz nueva)
 *  · **Barra flotante de abajo** ([AuraWideNavCapsule], en `AuraShell.kt`): las MISMAS celdas de la barra del
 *    móvil dentro de una cápsula. Sin música, centrada (~78 % del ancho); con música, a la izquierda y el
 *    minirreproductor a su derecha, en la misma fila.
 *  · **Reproductor dividido** (por defecto): al expandir el minirreproductor el reproductor ocupa el panel
 *    DERECHO ([auraWidePlayerPaneWidth]) y la pantalla en la que estabas se queda a la izquierda, con su barra de
 *    navegación. El botón de arriba a la izquierda ([AuraWidePlayerMode.onToggle]) lo pasa a pantalla completa y
 *    de vuelta; la elección se recuerda (`AuraWidePlayerSplitKey`).
 *
 * Se quitó el riel lateral y el panel «sonando ahora» de la primera versión de esta ronda: eran la respuesta
 * equivocada a la misma queja (la barra de Spotify es horizontal y flotante).
 */

/**
 * Cómo está el reproductor en una ventana ancha. `null` (móvil y clásico) = no hay modo: el reproductor es el de
 * siempre. Lo provee `MainActivity` alrededor del reproductor y lo lee [AuraPlayer] para (a) elegir su forma por
 * el ancho de SU panel y no por el de la ventana, y (b) dibujar el botón de alternar.
 */
@Immutable
class AuraWidePlayerMode(
    /** true = el reproductor es el panel derecho; false = pantalla completa. */
    val split: Boolean,
    val onToggle: () -> Unit,
)

val LocalAuraWidePlayerMode = staticCompositionLocalOf<AuraWidePlayerMode?> { null }

/**
 * Margen extra de la izquierda del minirreproductor cuando comparte fila con la cápsula de navegación y el
 * reproductor NO es un panel (modo pantalla completa). 0 en cualquier otro caso.
 */
val LocalAuraMiniStartInset = staticCompositionLocalOf { 0.dp }

/** Alto de aire entre la fila de la barra/minirreproductor y el borde de abajo, en ventanas anchas. */
val AuraWideBarMargin: Dp = 8.dp

/**
 * Ancho del panel derecho del reproductor (y de la fila del minirreproductor) en una ventana de [windowWidth].
 * ~46 % del ancho, entre 340 y 520 dp: en un plegable abierto (~725 dp) es del tamaño de un móvil, en una tableta
 * no crece sin límite. Pura, para test.
 */
fun auraWidePlayerPaneWidth(windowWidth: Dp): Dp = (windowWidth * 0.46f).coerceIn(340.dp, 520.dp)

/**
 * Ancho de la cápsula de navegación. Con música comparte fila con el minirreproductor: lo que sobra a la
 * izquierda del panel. Sin música: ~78 % de la ventana, con tope. Pura, para test.
 */
fun auraWideNavWidth(windowWidth: Dp, playerVisible: Boolean): Dp =
    if (playerVisible) {
        (windowWidth - auraWidePlayerPaneWidth(windowWidth) - AuraFloatingInset * 2 - AuraWideNavGap)
            .coerceAtLeast(240.dp)
    } else {
        (windowWidth * 0.78f).coerceAtMost(720.dp)
    }

/** Hueco entre la cápsula y el minirreproductor. */
val AuraWideNavGap: Dp = 10.dp

/**
 * Ancho de la ventana desde el que el héroe de un álbum / artista / playlist deja de ser un cuadrado a todo el
 * ancho y pasa a ser una banda de 320 dp. Era 840 dp, que ningún plegable alcanza: el panel de un Z Fold abierto
 * mide ~645 dp, así que su héroe era un cuadrado de 645 dp — «todo gigante» (dueño, 2026-10-15).
 */
internal val AURA_WIDE_HERO_MIN_WIDTH = 560.dp

/** Ancho de ventana máximo que sigue la escala de tarjetas de Inicio — un móvil grande. Más allá no crecen. */
internal val AURA_HOME_REFERENCE_MAX_WIDTH = 430.dp

/**
 * Columnas de una cuadrícula «ver todo» (álbumes favoritos, álbumes/vídeos de un artista…). Dos en un móvil — lo
 * que ya se enviaba — y más según crece el panel, para que una tarjeta mantenga el tamaño del móvil en lugar de
 * estirarse a 300 dp en un plegable abierto. Pura, para test.
 */
internal fun auraSeeAllColumns(paneWidth: Dp, videoHeavy: Boolean = false): Int {
    val spacing = 14.dp
    val available = paneWidth - AuraSpacing.Gutter * 2
    val minCard = if (videoHeavy) 300.dp else 168.dp
    val fit = ((available + spacing) / (minCard + spacing)).toInt()
    return fit.coerceAtLeast(if (videoHeavy) 1 else 2)
}
