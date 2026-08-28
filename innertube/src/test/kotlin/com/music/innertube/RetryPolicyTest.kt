package com.music.innertube

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the HALLAZGO-028 retry gate: socket-level IO errors are transient (retried), while
 * cancellation and non-IO errors are final (rethrown at once, never swallowed). The HTTP
 * 5xx branch is the `is ServerResponseException` check inside [retryableInnerTubeError] —
 * ktor's exception demands a live request/response pair, so that branch is compile-checked
 * against the real class rather than instantiated here.
 */
class RetryPolicyTest {

    @Test
    fun `socket-level IO errors are transient and get retried`() {
        assertTrue(retryableInnerTubeError(IOException("connection reset")))
        assertTrue(retryableInnerTubeError(SocketTimeoutException("timeout")))
        assertTrue(retryableInnerTubeError(UnknownHostException("music.youtube.com")))
        assertTrue(retryableInnerTubeError(ConnectException("ECONNREFUSED")))
    }

    @Test
    fun `cancellation is final - the retry loop must not swallow it`() {
        assertFalse(retryableInnerTubeError(CancellationException("scope cancelled")))
    }

    @Test
    fun `non-IO failures are final and rethrown at once`() {
        assertFalse(retryableInnerTubeError(IllegalStateException("bad state")))
        assertFalse(retryableInnerTubeError(RuntimeException("parse")))
        assertFalse(retryableInnerTubeError(OutOfMemoryError()))
    }
}
