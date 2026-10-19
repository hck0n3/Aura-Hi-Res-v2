package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
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
    /**
     * La píldora del minirreproductor es estrecha (< 460 dp): como la de Spotify, deja los botones de «me gusta»,
     * «salida de audio» y play/pausa, y anterior/siguiente quedan en el reproductor y en el deslizamiento.
     */
    val miniCompact: Boolean = false,
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

// ── Tarjetas, como Spotify ────────────────────────────────────────────────────────────────────────

/** Hueco entre las dos tarjetas y hasta el borde de la ventana. */
val AuraSplitCardGap: Dp = 10.dp

/** Esquinas de las tarjetas. */
val AuraSplitCardCorner: Dp = 22.dp

/** Aire extra bajo la barra de estado: lo justo para que la tarjeta no pise los iconos del sistema. */
val AuraSplitCardTopExtra: Dp = 6.dp

/** Aire extra sobre el inset de abajo; MENOR que el margen de la cápsula (8 dp) para que ésta quede DENTRO de la tarjeta. */
val AuraSplitCardBottomExtra: Dp = 2.dp

/**
 * Rectángulos de las dos tarjetas en una ventana de [width] × [height] px, en el orden (izquierda, derecha). Pura,
 * para test. La izquierda termina medio hueco antes del borde del panel y la derecha empieza medio hueco después, así
 * que el hueco entre ellas mide [gap] enteros y está centrado en el límite del panel.
 */
fun auraSplitCardRects(
    width: Float,
    height: Float,
    paneWidth: Float,
    gap: Float,
    top: Float,
    bottom: Float,
): Pair<Rect, Rect> {
    val boundary = width - paneWidth
    val y0 = top
    val y1 = height - bottom
    return Rect(gap, y0, boundary - gap / 2f, y1) to Rect(boundary + gap / 2f, y0, width - gap, y1)
}

/**
 * Marco de las dos tarjetas del reproductor dividido (dueño, 2026-10-15, captura de Spotify: *«los bordes de las
 * divisiones… estilo tarjetas, pero que siempre se vean bien con mi diseño»*).
 *
 * Es una capa SIN medidas ni toques: pinta el fondo de la app ([AuraPalette.Ground]) en todo lo que queda FUERA de dos
 * rectángulos de esquinas redondeadas (trazado par-impar). La pantalla de la izquierda y el reproductor de la derecha
 * siguen componiéndose exactamente como antes, solo que se ven recortados en tarjeta con su hueco — por eso no puede
 * descolocar el contenido, que es lo que habría hecho meter márgenes en la hoja del reproductor (su posición
 * colapsada se calcula con la ventana entera).
 *
 * Aparece con el progreso de la hoja ([progress], leído en la fase de dibujo: ninguna recomposición por fotograma) y
 * con el reproductor colapsado tiene alfa 0, así que no se dibuja. Estática: sin animaciones ni muestreo de pantalla.
 */
@Composable
fun AuraSplitCardsFrame(
    paneWidth: Dp,
    topInset: Dp,
    bottomInset: Dp,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val ground = AuraPalette.Ground
    Box(
        modifier = modifier
            .fillMaxSize()
            // No `graphicsLayer { alpha }` here: a window-sized alpha layer is an OFFSCREEN buffer (≈16 MB on an
            // unfolded Fold) re-composited on every frame of the flip. The path is ONE draw op, so its alpha goes
            // straight into the paint, read in the draw phase — identical pixels, no buffer, no recomposition.
            .drawWithCache {
                val (left, right) = auraSplitCardRects(
                    width = size.width,
                    height = size.height,
                    paneWidth = paneWidth.toPx(),
                    gap = AuraSplitCardGap.toPx(),
                    top = (topInset + AuraSplitCardTopExtra).toPx(),
                    bottom = (bottomInset + AuraSplitCardBottomExtra).toPx(),
                )
                val corner = CornerRadius(AuraSplitCardCorner.toPx())
                val frame = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(RoundRect(left, corner))
                    addRoundRect(RoundRect(right, corner))
                }
                onDrawBehind {
                    val a = ((progress() - 0.15f) / 0.6f).coerceIn(0f, 1f)
                    if (a > 0f) drawPath(frame, ground, alpha = a)
                }
            },
    )
}
