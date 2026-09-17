package iad1tya.echo.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * # Liquid Glass interactivo — la mitad que se puede probar
 *
 * 🔴 ORDEN DEL DUEÑO (2026-09-16): *"quiero que clones liquid glass de SimpMusic para configurarlo manual
 * desde temas… tanto en la apariencia vieja como en la nueva… lo quiero exacto, pero como otra versión de
 * liquid glass que se pueda aplicar a las dos versiones de mi apariencia"*.
 *
 * ## Lo que ya había, y lo que de verdad falta
 * Aura **ya** tiene cristal líquido y sobre la MISMA librería que SimpMusic: el backdrop de kyant
 * (`io.github.kyant0:backdrop`), vendorizado en `ui/component/backdrop/`, con sus diez ajustes en Temas
 * (vibrancia, radio de desenfoque, altura y cantidad de lente, aberración cromática, profundidad, tinte,
 * opacidad…). Así que "clonarlo" no es traer una librería nueva: lo que a Aura le falta del de SimpMusic
 * es **la reacción al tacto**, que es justo lo que lo hace parecer líquido en vez de un cristal pintado.
 *
 * En SimpMusic, al mantener pulsada una superficie de cristal: escala un poco, la refracción se hace más
 * profunda, y un brillo radial **sigue al dedo** y vuelve a su sitio con un muelle al soltar. El de Aura
 * es estático.
 *
 * Este archivo es esa reacción, y está separada a propósito de lo que dibuja: es la única parte que se
 * puede comprobar sin un teléfono delante. La parte de dibujo entra después, cuando haya superficies
 * donde colgarla en las dos apariencias — **y el interruptor en Temas llega con ellas, no antes**: un
 * ajuste que no dibuja nada es exactamente el placebo que el dueño lleva todo el día pidiendo que no
 * exista.
 *
 * ## Por qué el gesto es de solo observar
 * El cristal envuelve botones que tienen sus propios `onClick`. Si este reconocedor consumiera los
 * eventos, el botón dejaría de funcionar en cuanto se le pusiera cristal encima: el efecto rompería lo
 * que decora. Por eso nunca consume nada — mira pasar los eventos y anima.
 */

/** Cuánto dura el muelle del pulsado. Los valores son los de SimpMusic, sin retocar. */
private val PressSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)

/**
 * Estado de pulsado de UNA superficie de cristal.
 *
 * Reimplementación local del `InteractiveHighlight` del catálogo de kyant (sus ayudantes internos no son
 * públicos en backdrop 2.0.0), igual que hace SimpMusic: un `Animatable` con muelle para el progreso y la
 * posición del dedo en coordenadas locales para centrar el brillo.
 */
class GlassPressState(
    private val animationScope: CoroutineScope,
) {
    private val pressAnimation = Animatable(0f, 0.001f)

    /** 0 en reposo, 1 mientras está pulsado. Se lee en los bloques de dibujo y de capa. */
    val pressProgress: Float get() = pressAnimation.value

    /** Punto del dedo en coordenadas locales: el centro del brillo radial. */
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    suspend fun observePress(pointer: PointerInputScope) = with(pointer) {
        inspectDragGestures(
            onDragStart = { down ->
                touchPosition = down.position
                animationScope.launch { pressAnimation.animateTo(1f, PressSpring) }
            },
            onDragEnd = { animationScope.launch { pressAnimation.animateTo(0f, PressSpring) } },
            onDragCancel = { animationScope.launch { pressAnimation.animateTo(0f, PressSpring) } },
        ) { change, _ ->
            touchPosition = change.position
        }
    }
}

@Composable
fun rememberGlassPressState(): GlassPressState {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassPressState(scope) }
}

/**
 * Las cuentas del cristal, puras y en un solo sitio.
 *
 * Están fuera del dibujo por un motivo práctico: en este repo el dibujo solo se puede comprobar en el
 * teléfono, y estas curvas sí se pueden fijar en un test. Son las de SimpMusic, con los extremos atados a
 * los ajustes que el dueño ya tiene en Temas en vez de a constantes.
 */
object LiquidGlassMath {
    /**
     * La luminancia del fondo, de 0..1, llevada a la curva con signo que usa SimpMusic: `2l - 1`
     * elevado al cuadrado **conservando el signo**. El cuadrado deja la zona media plana (los fondos
     * normales se ven igual) y solo reacciona de verdad en los extremos, que es donde el cristal se
     * rompe: se lava a blanco sobre un fondo brillante, o desaparece sobre uno negro.
     */
    fun luminanceCurve(luminance: Float): Float {
        val centred = luminance.coerceIn(0f, 1f) * 2f - 1f
        return if (centred < 0f) -(centred * centred) else centred * centred
    }

    /**
     * Radio de desenfoque en px. Sobre fondo claro sube hasta el doble del ajuste del usuario; sobre
     * fondo oscuro baja hasta un cuarto, porque un fondo oscuro ya no tiene detalle que tapar y
     * desenfocarlo solo cuesta batería. `press` añade un pelín al mantener pulsado.
     */
    fun blurRadiusPx(basePx: Float, luminance: Float, pressPx: Float): Float {
        val l = luminanceCurve(luminance)
        val base = if (l > 0f) {
            basePx + (basePx * 2f - basePx) * l
        } else {
            basePx + (basePx * 0.25f - basePx) * -l
        }
        return (base + pressPx).coerceAtLeast(0f)
    }

    /**
     * El oscurecido *đục đen* de SimpMusic: cuanto más brillante es el fondo, más se oscurece el
     * cristal, para que nunca se lave a blanco. La rampa empieza a 0.3 de luminancia y termina a 0.8;
     * por debajo se queda en [minScrim], que es lo que impide que un cristal sobre negro se vuelva
     * invisible.
     */
    fun scrimAlpha(luminance: Float, minScrim: Float, maxScrim: Float): Float {
        val t = ((luminance - 0.3f) / 0.5f).coerceIn(0f, 1f)
        return minScrim + (maxScrim - minScrim) * t
    }

    /**
     * Altura de refracción de la lente. Se mantiene POR DEBAJO del inradio de la píldora
     * (`minDimension / 2`) a propósito: si las refracciones de arriba y de abajo se tocan en el eje
     * medio de una píldora ancha, aparece una costura horizontal oscura — el fallo que SimpMusic
     * documenta en su propia receta.
     */
    fun lensHeightPx(minDimension: Float, pressPx: Float): Float = minDimension / 4f + pressPx

    /** Cantidad de refracción: el inradio completo, como en la receta original. */
    fun lensAmountPx(minDimension: Float): Float = minDimension / 2f
}

/**
 * Reconocedor de arrastre/pulsación que **no consume nada**, portado del `DragGestureInspector` del
 * catálogo de kyant. Es lo que permite que una superficie de cristal reaccione al dedo mientras los
 * botones que envuelve siguen recibiendo sus propios toques.
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        val down = awaitFirstDown(requireUnconsumed = false)

        onDragStart(down)
        onDrag(down, Offset.Zero)
        val upEvent = dragOrNull(down.id) { onDrag(it, it.positionChange()) }
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.dragOrNull(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

/**
 * Espera al siguiente movimiento del dedo o a que se levante.
 *
 * Va aquí y no se importa de Compose porque la versión de la librería (`awaitDragOrUp` en
 * `androidx.compose.foundation.gestures`) es `internal`. SimpMusic la copia por el mismo motivo; esta es
 * la misma lógica, incluido el caso que se olvida siempre: si este dedo se levanta pero **otro** sigue
 * apoyado, el gesto no termina — se sigue con ese, en vez de dejar el cristal pulsado para siempre.
 */
private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId,
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed } ?: return dragEvent
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
