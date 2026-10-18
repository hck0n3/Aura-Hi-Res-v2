package iad1tya.echo.music.ui.newui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Cerrar una ventana inferior ARRASTRANDO su barrita hacia abajo (dueño, 2026-10-18: *«todas las ventanas flotantes
 * tienen arriba un botón de mini pestaña; que desde allí, si lo deslizo hacia abajo, se cierre»*).
 *
 * Las hojas de cristal («en la ventana», no `ModalBottomSheet`) dibujaban la barrita pero no hacían nada con ella; las
 * de Material sí la arrastran, así que el gesto solo faltaba en las de cristal: [BottomSheetPage], [BottomSheetMenu]
 * y [AuraInWindowDialog] (hoja inferior).
 *
 * Cómo funciona: mientras se arrastra, la hoja entera baja con el dedo ([offsetPx], aplicado con `offset` y no con
 * `graphicsLayer`, para que el cristal vuelva a muestrear lo que queda detrás en su nueva posición). Al soltar: si
 * bajó más de [DISMISS_DISTANCE] o iba rápido hacia abajo, se cierra (la animación de salida sigue desde donde está);
 * si no, vuelve a su sitio con un muelle. Solo la barrita arrastra, a propósito: arrastrar el contenido chocaría con
 * las listas que se desplazan dentro. No corre nada por fotograma fuera del gesto.
 */
@Stable
class AuraSheetDragState {
    /** Cuánto ha bajado la hoja respecto a su sitio, en píxeles (>= 0). */
    var offsetPx by mutableFloatStateOf(0f)

    companion object {
        val DISMISS_DISTANCE: Dp = 96.dp
        const val DISMISS_VELOCITY_PX_PER_S = 1500f

        /** ¿Este arrastre cierra la hoja? Separado para fijarlo en un test sin Compose. */
        fun shouldDismiss(offsetPx: Float, velocityPxPerS: Float, dismissDistancePx: Float): Boolean =
            offsetPx > dismissDistancePx || velocityPxPerS > DISMISS_VELOCITY_PX_PER_S
    }
}

/**
 * true dentro del panel compacto de la búsqueda rápida ([BottomSheetPageState.spotlight]): el contenido se mide a sí
 * mismo en vez de llenar la pantalla (sin título, resultados que solo ocupan lo que necesitan).
 */
val LocalAuraSpotlightSheet = compositionLocalOf { false }

@Composable
fun rememberAuraSheetDragState(): AuraSheetDragState = remember { AuraSheetDragState() }

/** Aplica el desplazamiento del arrastre a la hoja (va en el modificador de la hoja, no en el de la barrita). */
fun Modifier.auraSheetDragOffset(state: AuraSheetDragState): Modifier =
    this.offset { IntOffset(0, state.offsetPx.roundToInt()) }

/**
 * La barrita de arriba de una hoja inferior, ahora arrastrable. Su zona táctil es de 36 dp de alto y todo el ancho
 * (antes eran 4 dp de línea: imposible de agarrar). [barWidth] conserva el ancho que ya tenía cada hoja.
 */
@Composable
fun AuraSheetHandle(
    state: AuraSheetDragState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    barWidth: Dp = 32.dp,
) {
    val scope = rememberCoroutineScope()
    val dismissAt = with(LocalDensity.current) { AuraSheetDragState.DISMISS_DISTANCE.toPx() }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val draggableState = rememberDraggableState { delta ->
        state.offsetPx = (state.offsetPx + delta).coerceAtLeast(0f)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (AuraSheetDragState.shouldDismiss(state.offsetPx, velocity, dismissAt)) {
                        currentOnDismiss()
                    } else {
                        scope.launch {
                            animate(
                                initialValue = state.offsetPx,
                                targetValue = 0f,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            ) { value, _ -> state.offsetPx = value }
                        }
                    }
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .size(width = barWidth, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AuraPalette.OnGround.copy(alpha = 0.28f)),
        )
    }
}
