package iad1tya.echo.music.ui.component

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * # El movimiento del cristal interactivo
 *
 * 🔴 ORDEN DEL DUEÑO (2026-09-16): *"cuando se active el liquid glass, el que dice cristal interactivo,
 * quiero que todo lo que pones ahí tenga una apariencia más bonita, flotante y animada según las acciones
 * que toque o que haga, así como lo hace SimpMusic"*, y después: *"pero que pase todo eso cuando yo active
 * el cristal interactivo"*.
 *
 * Lo segundo es la condición de todo este archivo: **nada de esto corre con el interruptor apagado**.
 *
 * ## Qué hace que el suyo se sienta vivo
 * No es una animación, son cuatro resortes a la vez con constantes distintas, leídos en la fase de dibujo:
 *
 *  · `value` — dónde está la pastilla entre pestañas. Resorte rígido y sin rebote (1.0 / 1000): llega
 *    rápido y no oscila, porque una pastilla que rebota al elegir pestaña marea.
 *  · `velocity` — a qué ritmo se mueve. Resorte BLANDO y con rebote (0.5 / 300) a propósito: es lo que
 *    alimenta el estirado, y quieres que sobrepase y vuelva.
 *  · `scaleX` / `scaleY` — dos resortes SEPARADOS (0.6 / 250 y 0.7 / 250). Separados es la clave: si
 *    fueran uno solo, la pastilla crecería como un globo. Con dos, se deforma.
 *  · `pressProgress` — reposo ↔ levantada.
 *
 * Los valores son los suyos, sin retocar. Están ajustados y no tengo forma de mejorarlos a ojo.
 *
 * ## Aplastar y estirar
 * Lo que de verdad lo hace parecer líquido está en [LiquidSquashStretch]: al moverse deprisa la pastilla
 * se **alarga** en el sentido del movimiento y se **estrecha** en el otro, como una gota. Es aritmética
 * pura y por eso está separada del dibujo y fijada en un test — el dibujo solo se juzga en el teléfono.
 *
 * Port del `DampedDragAnimation` del catálogo de kyant, que es de donde SimpMusic lo tomó.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    /** Gesto de solo observar, igual que el del cristal: nunca consume, así que los toques siguen vivos. */
    val modifier: Modifier =
        Modifier.pointerInput(Unit) {
            inspectDragGestures(
                onDragStart = { down ->
                    onDragStarted(down.position)
                    press()
                },
                onDragEnd = {
                    onDragStopped()
                    release()
                },
                onDragCancel = {
                    onDragStopped()
                    release()
                },
            ) { _, dragAmount ->
                onDrag(size, dragAmount)
            }
        }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    /**
     * Soltar NO desinfla de inmediato: primero espera a que la pastilla esté casi en su destino.
     * Sin esa espera, al tocar una pestaña lejana se desinfla a mitad de viaje y el efecto se pierde
     * justo cuando se está mirando.
     */
    fun release() {
        animationScope.launch {
            withFrameNanos {}
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange.start, valueRange.endInclusive)
        animationScope.launch {
            valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange.start, valueRange.endInclusive)
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val targetVelocity =
            velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

/**
 * Aplastar y estirar: la aritmética que hace que la pastilla parezca una gota y no una caja.
 *
 * Moviéndose deprisa se ALARGA en el sentido del movimiento y se ESTRECHA en el perpendicular, y al
 * frenar vuelve. Los dos ejes usan el mismo número de velocidad con pesos distintos (0.75 y 0.25) y en
 * sentidos opuestos — dividir en X y multiplicar en Y — que es justo lo que produce la deformación en
 * vez de un cambio de tamaño.
 *
 * El tope de ±0.2 no es decorativo: sin él, un gesto rápido puede llevar el divisor a cero o a negativo
 * y la escala se dispara al infinito o se da la vuelta.
 */
object LiquidSquashStretch {
    const val VELOCITY_CLAMP = 0.2f
    const val X_WEIGHT = 0.75f
    const val Y_WEIGHT = 0.25f

    fun scaleX(baseScaleX: Float, velocity: Float): Float =
        baseScaleX / (1f - (velocity * X_WEIGHT).fastCoerceIn(-VELOCITY_CLAMP, VELOCITY_CLAMP))

    fun scaleY(baseScaleY: Float, velocity: Float): Float =
        baseScaleY * (1f - (velocity * Y_WEIGHT).fastCoerceIn(-VELOCITY_CLAMP, VELOCITY_CLAMP))
}
