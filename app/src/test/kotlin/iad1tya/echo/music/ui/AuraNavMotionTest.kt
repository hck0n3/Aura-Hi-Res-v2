package iad1tya.echo.music.ui

import iad1tya.echo.music.ui.component.AuraNavMotion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El barrido entre pestañas, clonado de SimpMusic.
 *
 * 🔴 ORDEN DEL DUEÑO (2026-09-16): *"quiero las mismas animaciones de SimpMusic en Aura, animaciones
 * que se mira que se desplaza a alta velocidad de un lado a otro mientras toco los botones: de inicio,
 * novedades, biblioteca, buscar"*.
 *
 * Este test cubre dos cosas distintas y las dos hacen falta:
 *
 * 1. **La dirección** — predicados puros de `Int, Int -> Boolean`, así que se prueban de verdad.
 *    Son los mismos que llevaban años en `MainActivity`; lo que se verifica aquí es que al sacarlos
 *    de allí no cambiaron de sentido, porque invertir uno solo se ve como una pantalla que entra
 *    por el lado equivocado y nadie lo nota hasta tenerlo en el teléfono.
 *
 * 2. **El recorrido y la curva** — esto NO se puede afirmar desde un test unitario: `EnterTransition`
 *    no expone ni el offset ni el `animationSpec`, así que no hay nada que leer. Lo que sí se puede
 *    comprobar es el código fuente, y es justo donde estuvo el bug: `it / 8` en vez de `it` y un
 *    `tween(200)` en vez del resorte por defecto. Ese es el motivo del escaneo de abajo.
 */
class AuraNavMotionTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    // Los índices de la barra: 0 Inicio, 1 Novedades, 2 Biblioteca, 3 Buscar (el orden real da igual,
    // lo que importa es que subir de índice va hacia un lado y bajar hacia el otro).
    private val inicio = 0
    private val novedades = 1
    private val biblioteca = 2

    // -1 = la ruta no es una pestaña (un álbum, un artista, ajustes...).
    private val noEsPestana = -1

    @Test
    fun `avanzar de una pestana a la siguiente va hacia adelante`() {
        assertTrue(AuraNavMotion.forward(inicio, novedades))
        assertTrue(AuraNavMotion.forward(inicio, biblioteca))
        assertTrue(AuraNavMotion.forward(novedades, biblioteca))
    }

    @Test
    fun `retroceder a una pestana anterior va hacia atras`() {
        assertFalse(AuraNavMotion.forward(biblioteca, inicio))
        assertFalse(AuraNavMotion.forward(biblioteca, novedades))
        assertFalse(AuraNavMotion.forward(novedades, inicio))
    }

    @Test
    fun `abrir algo que no es pestana siempre entra hacia adelante`() {
        // Abrir un álbum desde cualquier pestaña: entra desde la derecha, como en cualquier app.
        assertTrue(AuraNavMotion.forward(inicio, noEsPestana))
        assertTrue(AuraNavMotion.forward(biblioteca, noEsPestana))
        assertTrue(AuraNavMotion.forward(noEsPestana, noEsPestana))
    }

    @Test
    fun `volver atras deshace el movimiento en vez de repetirlo`() {
        // Esta es la asimetría deliberada con `forward`, y el motivo de que sean dos funciones:
        // volver de un álbum (-1) a la pestaña que lo abrió tiene que ir hacia ATRÁS. Con `forward`
        // iría hacia adelante y la pantalla entraría por el mismo lado por el que se fue.
        assertFalse(AuraNavMotion.popForward(noEsPestana, inicio))
        assertFalse(AuraNavMotion.popForward(noEsPestana, biblioteca))
        assertFalse(AuraNavMotion.popForward(biblioteca, inicio))
        assertTrue(AuraNavMotion.popForward(inicio, biblioteca))
    }

    @Test
    fun `la direccion de ida y la de vuelta son opuestas para el mismo par`() {
        // Si esto deja de cumplirse, ir y volver entre dos pestañas se ve como dos movimientos
        // hacia el mismo lado, que es exactamente lo contrario de "de un lado a otro".
        for (from in 0..3) {
            for (to in 0..3) {
                if (from == to) continue
                assertEquals(
                    "ida y vuelta entre $from y $to apuntan al mismo lado",
                    AuraNavMotion.forward(from, to),
                    !AuraNavMotion.popForward(to, from),
                )
            }
        }
    }

    @Test
    fun `el barrido recorre el ancho completo y no un octavo`() {
        val source = File(
            repoRoot,
            "app/src/main/kotlin/com/music/echo/ui/component/AuraNavMotion.kt",
        )
        assertTrue("AuraNavMotion.kt se movió — actualiza este test para seguirlo", source.isFile)
        val code = source.readLines()
            .filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }
            .joinToString("\n")

        assertTrue(
            "el desplazamiento tiene que ser el ancho completo (`it` / `-it`), como SimpMusic. " +
                "Una fracción del ancho se lee como un empujón, no como el barrido que pidió el dueño.",
            code.contains("slideInHorizontally { if (forward) it else -it }") &&
                code.contains("slideOutHorizontally { if (forward) -it else it }"),
        )
        assertFalse(
            "volvió a aparecer una fracción del ancho en el desplazamiento entre pestañas",
            Regex("""it\s*/\s*\d""").containsMatchIn(code),
        )
    }

    @Test
    fun `no se le pasa animationSpec propio a las transiciones`() {
        val source = File(
            repoRoot,
            "app/src/main/kotlin/com/music/echo/ui/component/AuraNavMotion.kt",
        )
        val code = source.readLines()
            .filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }
            .joinToString("\n")

        // SimpMusic llama `fadeIn()`, `fadeOut()` y los slide sin spec: usa el resorte por defecto de
        // Compose (StiffnessMediumLow), que arranca rapidísimo y frena al final. Eso es la "alta
        // velocidad" del pedido. Un `tween` reparte el recorrido a ritmo plano y mata el efecto.
        assertTrue("fadeIn() tiene que ir sin argumentos, como en SimpMusic", code.contains("fadeIn()"))
        assertTrue("fadeOut() tiene que ir sin argumentos, como en SimpMusic", code.contains("fadeOut()"))
        assertFalse(
            "apareció un tween en el barrido entre pestañas: eso es exactamente lo que hacía que " +
                "no se sintiera como SimpMusic",
            code.contains("tween("),
        )
        assertFalse(
            "apareció un animationSpec explícito; copiar a mano el valor por defecto de Compose deja " +
                "de seguirlo cuando Compose lo afine",
            code.contains("animationSpec"),
        )
    }

    @Test
    fun `MainActivity usa AuraNavMotion en las cuatro transiciones`() {
        val main = File(repoRoot, "app/src/main/kotlin/com/music/echo/MainActivity.kt")
        assertTrue("MainActivity.kt se movió — actualiza este test", main.isFile)
        val code = main.readText()

        // Hay UN solo NavHost en toda la app y las dos apariencias (la clásica y la nueva) pasan por
        // él, así que esto es lo que garantiza que el barrido se ve en ambas.
        for (slot in listOf("enterTransition", "exitTransition", "popEnterTransition", "popExitTransition")) {
            val body = Regex("""\b$slot = \{(.*?)\n {36}\}""", RegexOption.DOT_MATCHES_ALL)
                .find(code)
                ?.groupValues
                ?.get(1)
                ?: error("no se encontró $slot en MainActivity.kt")
            assertTrue(
                "$slot dejó de usar AuraNavMotion: el barrido de esa transición se quedó atrás",
                body.contains("AuraNavMotion."),
            )
        }
    }
}
