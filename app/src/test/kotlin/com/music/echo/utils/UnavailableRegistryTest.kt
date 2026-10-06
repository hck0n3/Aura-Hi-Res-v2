package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dueño (2026-10-06): "cualquier canción que ya no esté disponible no se la muestres al usuario, para no
 * causar errores de reproducción". Ocultar por error es perder canciones de vista, así que lo que se pinta
 * aquí son sobre todo las BARRERAS.
 */
class UnavailableRegistryTest {

    private var clock = 1_000_000L
    private val registry = UnavailableRegistry { clock }

    @Test
    fun greyedOutSongsAreUnavailableUntilSeenAvailableAgain() {
        registry.markGreyedOut(listOf("a", "b"))
        assertTrue(registry.isUnavailable("a"))
        registry.markSeenAvailable(listOf("a"))
        assertFalse(registry.isUnavailable("a"))
        assertTrue(registry.isUnavailable("b"))
    }

    @Test
    fun aFailureIsOnlyBelievedOnceAnotherSongPlays() {
        registry.recordFailure("x")
        assertFalse("one failure alone proves nothing", registry.isUnavailable("x"))
        registry.recordSuccess("y")
        assertTrue(registry.isUnavailable("x"))
    }

    @Test
    fun aStreakOfFailuresIsTheSessionNotTheContent() {
        // Filas #29/#30: la sesión o el cifrado tumban TODO a la vez; nada de eso es "no disponible".
        registry.recordFailure("a")
        registry.recordFailure("b")
        registry.recordFailure("c")
        registry.recordFailure("d") // tail of the outage, ignored until something plays again
        registry.recordSuccess("e")
        listOf("a", "b", "c", "d").forEach { assertFalse(it, registry.isUnavailable(it)) }
        // Back to normal: an isolated failure followed by a success is believed again.
        registry.recordFailure("f")
        registry.recordSuccess("g")
        assertTrue(registry.isUnavailable("f"))
    }

    @Test
    fun aSongThatPlaysIsNeverHidden() {
        registry.markGreyedOut(listOf("a"))
        registry.recordSuccess("a")
        assertFalse(registry.isUnavailable("a"))
        registry.recordFailure("b")
        registry.recordSuccess("b") // the same song recovered: not a content failure
        assertFalse(registry.isUnavailable("b"))
    }

    @Test
    fun appearingInAListDoesNotClearARealPlaybackFailure() {
        registry.recordFailure("x")
        registry.recordSuccess("y")
        registry.markSeenAvailable(listOf("x"))
        assertTrue(registry.isUnavailable("x"))
    }

    @Test
    fun marksExpire() {
        registry.markGreyedOut(listOf("g"))
        registry.recordFailure("f")
        registry.recordSuccess("ok")
        clock += UnavailableRegistry.FAILURE_TTL_MS + 1
        assertFalse(registry.isUnavailable("f"))
        assertTrue(registry.isUnavailable("g"))
        clock += UnavailableRegistry.GREYED_TTL_MS
        assertFalse(registry.isUnavailable("g"))
    }

    @Test
    fun aStalePendingFailureIsNeverConfirmed() {
        registry.recordFailure("x")
        clock += UnavailableRegistry.PENDING_TTL_MS + 1
        registry.recordSuccess("y")
        assertFalse(registry.isUnavailable("x"))
    }

    @Test
    fun serializeRoundTripsAndToleratesGarbage() {
        registry.markGreyedOut(listOf("a"))
        registry.recordFailure("b")
        registry.recordSuccess("c")
        val other = UnavailableRegistry { clock }
        other.restore(registry.serialize() + "\nnot,a,line\n,,\nz,12,q")
        assertEquals(setOf("a", "b"), other.snapshot())
    }
}
