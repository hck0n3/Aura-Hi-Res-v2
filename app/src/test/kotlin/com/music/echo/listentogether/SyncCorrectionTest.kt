package iad1tya.echo.music.listentogether

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alcanzar al anfitrión estirando el tiempo, como Sonos y AirPlay — no saltando.
 *
 * 🔴 PETICIÓN DEL DUEÑO (2026-09-17): *"hazlo entonces como lo hace Sonos o AirPlay"*, después de
 * preguntar por qué Escuchar juntos nunca sonaba sincronizado del todo.
 *
 * Lo que se fija aquí es lo que decide si el arreglo **se oye o no se oye**, que es el punto entero:
 * un recorte de velocidad demasiado grande deja de ser invisible y la canción "corre"; uno que no
 * atenúa al acercarse deja al invitado oscilando alrededor del anfitrión para siempre. Ninguna de
 * las dos cosas da error: las dos simplemente suenan mal.
 */
class SyncCorrectionTest {

    @Test
    fun `dentro de la banda muerta no se toca nada`() {
        // Perseguir el cero significa corregir eternamente; por debajo de unas décimas nadie
        // distingue dos fuentes.
        assertEquals(SyncAction.Hold, SyncCorrection.correct(0L, 1_000L))
        assertEquals(SyncAction.Hold, SyncCorrection.correct(SyncCorrection.IN_SYNC_MS, 1_000L))
        assertEquals(SyncAction.Hold, SyncCorrection.correct(-SyncCorrection.IN_SYNC_MS, 1_000L))
    }

    @Test
    fun `adelantado frena y atrasado acelera`() {
        val adelantado = SyncCorrection.correct(300L, 10_000L)
        val atrasado = SyncCorrection.correct(-300L, 10_000L)
        assertTrue("ir adelantado tiene que FRENAR: $adelantado", (adelantado as SyncAction.Trim).speed < 1f)
        assertTrue("ir atrasado tiene que ACELERAR: $atrasado", (atrasado as SyncAction.Trim).speed > 1f)
    }

    @Test
    fun `el recorte nunca pasa del dos por ciento`() {
        // ESTE es el caso que decide si se oye. Con el tono preservado ±2 % es inaudible; a partir
        // del 4 % se nota que la canción corre, y entonces el arreglo sería peor que el problema.
        for (error in longArrayOf(-100_000, -5_000, -1_000, -201, 201, 1_000, 5_000, 100_000)) {
            val speed = SyncCorrection.trimSpeed(error)
            assertTrue(
                "un error de $error ms pidió velocidad $speed, fuera del ±${SyncCorrection.MAX_TRIM}",
                abs(speed - 1f) <= SyncCorrection.MAX_TRIM + 1e-6f,
            )
        }
    }

    @Test
    fun `la correccion se atenua conforme se acerca`() {
        // Sin atenuación el recorte es un escalón: se pega al tope y se apaga de golpe, y el
        // invitado oscila alrededor del anfitrión corrigiendo en un sentido y luego en el otro.
        val lejos = abs(SyncCorrection.trimSpeed(200L) - 1f)
        val medio = abs(SyncCorrection.trimSpeed(120L) - 1f)
        val cerca = abs(SyncCorrection.trimSpeed(70L) - 1f)
        assertTrue("a 120 ms debe corregir menos que a 200 ms ($medio vs $lejos)", medio < lejos)
        assertTrue("a 70 ms debe corregir menos que a 120 ms ($cerca vs $medio)", cerca < medio)
    }

    @Test
    fun `un hueco demasiado grande se salta en vez de arrastrarse`() {
        // Cerrar 5 s al 2 % tardaría más de cuatro minutos: la canción entera desincronizada por no
        // dar un salto. Pasa al entrar a una sala a mitad de canción, que es cuando un salto no
        // molesta a nadie.
        val accion = SyncCorrection.correct(5_000L, 42_000L)
        assertEquals(SyncAction.Resync(42_000L), accion)
    }

    @Test
    fun `un salto nunca pide una posicion negativa`() {
        // Un anfitrión que acaba de empezar la canción y un invitado adelantado dan un objetivo
        // negativo; seekTo con un negativo es un argumento inválido.
        val accion = SyncCorrection.correct(5_000L, -3_000L)
        assertEquals(SyncAction.Resync(0L), accion)
    }

    @Test
    fun `el tempo que eligio el usuario se respeta`() {
        // Alguien que puso la canción a 1.25x sigue oyéndola a 1.25x mientras la sala lo alinea.
        // Sobrescribir le quitaría su ajuste sin avisar, y al salir de la sala se lo devolvería de
        // golpe con un cambio de velocidad audible.
        val trim = SyncCorrection.correct(300L, 10_000L)
        val conAjuste = SyncCorrection.playerSpeed(userTempo = 1.25f, action = trim)
        assertEquals(1.25f * (trim as SyncAction.Trim).speed, conAjuste, 1e-6f)
        assertTrue("el recorte tiene que seguir siendo pequeño sobre su tempo", conAjuste > 1.2f)
    }

    @Test
    fun `sincronizado devuelve exactamente el tempo del usuario`() {
        // Exactamente, no aproximadamente: `setOffloadEnabled` compara contra 1f para decidir si
        // pedir soporte de cambio de velocidad, y un 1.0000001 residual dejaría el offload
        // desactivado para siempre después de una sala — o sea, batería perdida en silencio.
        assertEquals(1f, SyncCorrection.playerSpeed(1f, SyncAction.Hold), 0f)
        assertEquals(1.25f, SyncCorrection.playerSpeed(1.25f, SyncAction.Hold), 0f)
        assertEquals(1f, SyncCorrection.playerSpeed(1f, SyncAction.Resync(0L)), 0f)
    }
}
