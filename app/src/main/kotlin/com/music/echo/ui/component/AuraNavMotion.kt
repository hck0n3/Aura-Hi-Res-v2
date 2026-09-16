package iad1tya.echo.music.ui.component

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * # El desplazamiento entre pestañas, clonado de SimpMusic
 *
 * 🔴 ORDEN DEL DUEÑO (2026-09-16): *"quiero las mismas animaciones de SimpMusic en Aura, animaciones
 * que se mira que se desplaza a alta velocidad de un lado a otro mientras toco los botones: de inicio,
 * novedades, biblioteca, buscar, y en todo lado donde Simp tenga animaciones quiero que las clones
 * para Aura"*.
 *
 * ## Por qué el de Aura NO se sentía así
 * No era la duración, era **cuánto recorre**. Los dos números que lo explican:
 *
 * | | recorrido | curva |
 * |---|---|---|
 * | Aura (antes) | `it / 8` — un octavo del ancho | `tween(200)` |
 * | SimpMusic | `it` — el **ancho completo** | resorte por defecto de Compose |
 *
 * Un octavo de pantalla es un empujoncito: se nota que algo se movió pero nunca se lee como
 * "se desplazó de un lado a otro". El ancho completo es lo que produce el barrido. Y la curva
 * importa igual: `tween(200)` sale y llega al mismo ritmo, mientras que el resorte
 * (`StiffnessMediumLow`, que es lo que `fadeIn()` y `slideInHorizontally {}` usan cuando no les
 * pasas nada — que es justo como los llama SimpMusic) arranca rapidísimo y frena al final. Eso es
 * la "alta velocidad" que él describe: no es más corto, es que el grueso del recorrido pasa en los
 * primeros milisegundos.
 *
 * Por eso aquí NO se especifica `animationSpec` en ninguna parte. Pasar uno propio, aunque fuera
 * un `spring` con los mismos números, sería una copia a mano de un valor por defecto que Compose
 * puede afinar; llamarlos vacíos como SimpMusic es la clonación literal.
 *
 * ## La única diferencia deliberada con SimpMusic
 * SimpMusic tiene una dirección FIJA: siempre entra desde la izquierda y siempre sale hacia la
 * derecha, sin importar a qué pestaña vayas. Aura ya tenía dirección según el índice de la pestaña
 * (ir de Inicio a Biblioteca se mueve al revés que de Biblioteca a Inicio) y eso se conserva: es lo
 * que hace que se lea como *"de un lado a otro"* y no siempre hacia el mismo lado. Los predicados
 * de [forward] y [popForward] son exactamente los que ya estaban en `MainActivity`, extraídos tal
 * cual para poder probarlos; lo único que cambió es la transición que producen.
 *
 * ## Los índices
 * `-1` significa "esta ruta no es una pestaña de la barra" (álbum, artista, ajustes…). Se trata
 * como avanzar hacia adelante, que es lo que ya hacía Aura.
 */
object AuraNavMotion {
    /**
     * Dirección para `enterTransition` / `exitTransition` (navegación hacia adelante).
     *
     * @param fromIndex índice de la pestaña de la que se sale (`initialState`), o -1.
     * @param toIndex índice de la pestaña a la que se entra (`targetState`), o -1.
     */
    fun forward(fromIndex: Int, toIndex: Int): Boolean = toIndex == -1 || toIndex > fromIndex

    /**
     * Dirección para `popEnterTransition` / `popExitTransition` (volver atrás).
     *
     * Ojo con la asimetría respecto a [forward]: aquí un destino que no es pestaña (-1) cuenta como
     * ir hacia ATRÁS, no hacia adelante, porque volver de un álbum a la pestaña que lo abrió tiene
     * que deshacer el movimiento que lo trajo.
     */
    fun popForward(fromIndex: Int, toIndex: Int): Boolean = fromIndex != -1 && fromIndex < toIndex

    /** Entra la pantalla nueva: barrido de ancho completo, resorte por defecto. */
    fun enter(forward: Boolean): EnterTransition =
        fadeIn() + slideInHorizontally { if (forward) it else -it }

    /** Sale la pantalla vieja hacia el lado contrario, para que se lean como una sola tira. */
    fun exit(forward: Boolean): ExitTransition =
        fadeOut() + slideOutHorizontally { if (forward) -it else it }
}
