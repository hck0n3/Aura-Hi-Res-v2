package iad1tya.echo.music.utils

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Pins HALLAZGO-056: a stream resolution cancelled because its owner went away (the user left the
 * screen, skipped the track, closed the sheet) is NOT a playback failure. The BETA-012 log was full
 * of `RESOLVE_FAIL … Parent job is Cancelling` and `Playback failed — The coroutine scope left the
 * composition` entries persisted at WARNING/ERROR — benign teardown noise that buried the real
 * failures in the shared log. These predicates gate the logging: cancellations drop to debug
 * (never persisted by AppLogger), genuine errors keep their WARNING/ERROR lines.
 */
class ResolveCancellationTest {

    @Test
    fun `coroutine cancellations are benign`() {
        assertTrue(isBenignResolveCancellation(CancellationException("Parent job is Cancelling")))
        assertTrue(isBenignResolveCancellation(CancellationException("jq9 was cancelled")))
    }

    @Test
    fun `thread interrupts from cancelled blocking calls are benign`() {
        assertTrue(isBenignResolveCancellation(InterruptedException()))
    }

    @Test
    fun `real failures are never treated as cancellations`() {
        assertFalse(isBenignResolveCancellation(IOException("network down")))
        assertFalse(isBenignResolveCancellation(IllegalStateException("parse error")))
        assertFalse(isBenignResolveCancellation(null))
    }
}
