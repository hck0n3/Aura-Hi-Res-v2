package iad1tya.echo.music.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import iad1tya.echo.music.utils.YTPlayerUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the error predicates that route onPlayerError into its recovery handlers,
 * extracted verbatim from MusicService into PlaybackErrorClassifier (HALLAZGO-021 split, phase A).
 *
 * What gets pinned here is not "reasonable behaviour" but the EXACT routing facts the registry depends
 * on: the 403/416/page-reload precedence over the network classification, the whole-chain walk that
 * finds a NO_STREAM dead-end 2+ levels deep, the top-level-only judgement of cache corruption (chain
 * walking there was tried and reverted, registry #74), and the innermost-reason recovery for the toast.
 */
class PlaybackErrorClassifierTest {

    // PlaybackException's public constructors stamp the current SystemClock.elapsedRealtime(), an
    // android.os method that throws on the plain JVM unit-test source set. The protected 5-arg
    // constructor takes the timestamp explicitly (it is the one fromBundle uses to restore), so it
    // builds cleanly here with a fixed 0L timestamp — the classifier never reads timestampMs.
    private fun playbackError(
        errorCode: Int,
        message: String? = null,
        cause: Throwable? = null,
    ): PlaybackException {
        val ctor = PlaybackException::class.java.getDeclaredConstructor(
            String::class.java,
            Throwable::class.java,
            Int::class.javaPrimitiveType,
            android.os.Bundle::class.java,
            Long::class.javaPrimitiveType,
        )
        ctor.isAccessible = true
        return ctor.newInstance(message, cause, errorCode, null, 0L)
    }

    // media3 1.10.1 signature: (int responseCode, String responseMessage, IOException cause,
    // Map headerFields, DataSpec dataSpec, byte[] responseBody). Built reflectively with a null
    // DataSpec: every public DataSpec constructor needs an android.net.Uri (a stub on the plain JVM
    // unit-test source set), and the classifier only ever reads responseCode — the constructor chain
    // just stores the fields, so a null DataSpec is safe here.
    private fun invalidResponseCode(code: Int): HttpDataSource.InvalidResponseCodeException {
        val ctor = HttpDataSource.InvalidResponseCodeException::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            String::class.java,
            java.io.IOException::class.java,
            Map::class.java,
            androidx.media3.datasource.DataSpec::class.java,
            ByteArray::class.java,
        )
        return ctor.newInstance(code, "http $code", null, emptyMap<String, List<String>>(), null, ByteArray(0))
    }

    // ------------------------------------------------------- httpResponseCode

    @Test
    fun httpResponseCodeFoundOnDirectCause() {
        val error = playbackError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, cause = invalidResponseCode(403))
        assertEquals(403, PlaybackErrorClassifier.httpResponseCode(error))
    }

    /** media3's Loader and ExoPlayer wrap the data-source exception, so it routinely sits 2 levels deep. */
    @Test
    fun httpResponseCodeFoundTwoLevelsDeep() {
        val wrapped = Exception("loader wrapper", invalidResponseCode(416))
        val error = playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, cause = wrapped)
        assertEquals(416, PlaybackErrorClassifier.httpResponseCode(error))
    }

    @Test
    fun httpResponseCodeNullWithoutResponseCodeException() {
        val error = playbackError(PlaybackException.ERROR_CODE_TIMEOUT, cause = java.net.ConnectException("refused"))
        assertNull(PlaybackErrorClassifier.httpResponseCode(error))
        assertNull(PlaybackErrorClassifier.httpResponseCode(playbackError(PlaybackException.ERROR_CODE_TIMEOUT)))
    }

    // -------------------------------------------------------- expired / range

    @Test
    fun expiredUrlIs403AnywhereInTheChain() {
        assertTrue(PlaybackErrorClassifier.isExpiredUrlError(playbackError(0, cause = invalidResponseCode(403))))
        assertFalse(PlaybackErrorClassifier.isExpiredUrlError(playbackError(0, cause = invalidResponseCode(416))))
        assertFalse(PlaybackErrorClassifier.isExpiredUrlError(playbackError(PlaybackException.ERROR_CODE_TIMEOUT)))
    }

    @Test
    fun rangeNotSatisfiableIs416AnywhereInTheChain() {
        assertTrue(
            PlaybackErrorClassifier.isRangeNotSatisfiableError(playbackError(0, cause = invalidResponseCode(416))),
        )
        assertFalse(
            PlaybackErrorClassifier.isRangeNotSatisfiableError(playbackError(0, cause = invalidResponseCode(403))),
        )
    }

    // ------------------------------------------------------------ page reload

    @Test
    fun pageReloadMatchesTopLevelMessage() {
        val error = playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, message = "Page needs to be reloaded")
        assertTrue(PlaybackErrorClassifier.isPageReloadError(error))
    }

    /** The owner's logs show the server localising the message (Italian, Spanish), so all three levels match. */
    @Test
    fun pageReloadMatchesLocalizedCauseMessages() {
        val italian = playbackError(0, cause = Exception("La pagina deve essere ricaricata"))
        assertTrue(PlaybackErrorClassifier.isPageReloadError(italian))

        val innerLevel = playbackError(0, cause = Exception("wrapper", Exception("please reload")))
        assertTrue(PlaybackErrorClassifier.isPageReloadError(innerLevel))
    }

    @Test
    fun pageReloadDoesNotMatchUnrelatedMessages() {
        assertFalse(
            PlaybackErrorClassifier.isPageReloadError(
                playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, message = "Source error", cause = Exception("3003")),
            ),
        )
        assertFalse(PlaybackErrorClassifier.isPageReloadError(playbackError(0)))
    }

    // --------------------------------------------------------- network-related

    @Test
    fun networkRelatedByTopLevelErrorCode() {
        assertTrue(
            PlaybackErrorClassifier.isNetworkRelatedError(
                playbackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
            ),
        )
        assertTrue(
            PlaybackErrorClassifier.isNetworkRelatedError(
                playbackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT),
            ),
        )
        assertTrue(PlaybackErrorClassifier.isNetworkRelatedError(playbackError(PlaybackException.ERROR_CODE_TIMEOUT)))
        assertTrue(
            PlaybackErrorClassifier.isNetworkRelatedError(
                playbackError(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE),
            ),
        )
    }

    @Test
    fun networkRelatedByCauseType() {
        assertTrue(
            PlaybackErrorClassifier.isNetworkRelatedError(playbackError(0, cause = java.net.ConnectException())),
        )
        assertTrue(
            PlaybackErrorClassifier.isNetworkRelatedError(playbackError(0, cause = java.net.UnknownHostException("x"))),
        )
        assertTrue(
            PlaybackErrorClassifier.isNetworkRelatedError(
                playbackError(0, cause = playbackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)),
            ),
        )
    }

    /**
     * The precedence the bounded-retry fix depends on: a URL-shaped error (403 / 416 / page-reload)
     * is NEVER network-related, even when a network code is also present — otherwise the dead URL
     * gets retried three times instead of re-resolved.
     */
    @Test
    fun urlShapedErrorsWinOverNetworkCodes() {
        val forbidden = playbackError(PlaybackException.ERROR_CODE_TIMEOUT, cause = invalidResponseCode(403))
        assertFalse(PlaybackErrorClassifier.isNetworkRelatedError(forbidden))

        val range = playbackError(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            cause = invalidResponseCode(416),
        )
        assertFalse(PlaybackErrorClassifier.isNetworkRelatedError(range))

        val reload = playbackError(
            PlaybackException.ERROR_CODE_TIMEOUT,
            message = "page needs to be reloaded",
        )
        assertFalse(PlaybackErrorClassifier.isNetworkRelatedError(reload))
    }

    @Test
    fun unrelatedErrorsAreNotNetworkRelated() {
        assertFalse(
            PlaybackErrorClassifier.isNetworkRelatedError(
                playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, cause = Exception("other")),
            ),
        )
    }

    // --------------------------------------------------------------- no stream

    @Test
    fun noStreamDetectedAtTopLevel() {
        val error = playbackError(PlaybackErrorClassifier.ERROR_CODE_NO_STREAM, message = "no format")
        assertTrue(PlaybackErrorClassifier.isNoStreamError(error))
    }

    /**
     * The real-world shape: thrown inside the ResolvingDataSource, wrapped by media3's Loader
     * (UnexpectedLoaderException, an IOException) and again by ExoPlayer — 2+ levels deep. A one-level
     * check misses this and falls through to handleGenericIOError (the "stuck / never loads" bug).
     */
    @Test
    fun noStreamDetectedDeepInTheCauseChain() {
        val resolution = playbackError(
            PlaybackErrorClassifier.ERROR_CODE_NO_STREAM,
            message = "resolve failed",
            cause = YTPlayerUtils.StreamResolutionException("region locked"),
        )
        val loaderWrap = java.io.IOException("Unexpected loader exception", resolution)
        val top = playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, cause = loaderWrap)
        assertTrue(PlaybackErrorClassifier.isNoStreamError(top))
    }

    @Test
    fun noStreamDetectedByExceptionTypeAlone() {
        // A StreamResolutionException that never got mapped to the NO_STREAM code still anchors the check.
        val top = playbackError(0, cause = Exception("wrap", YTPlayerUtils.StreamResolutionException("premium only")))
        assertTrue(PlaybackErrorClassifier.isNoStreamError(top))
    }

    @Test
    fun ordinaryErrorsAreNotNoStream() {
        assertFalse(
            PlaybackErrorClassifier.isNoStreamError(
                playbackError(PlaybackException.ERROR_CODE_TIMEOUT, cause = java.net.ConnectException()),
            ),
        )
    }

    @Test
    fun noStreamReasonComesFromTheMappedNoStreamException() {
        // The loader's exact shape: message IS the reason, cause is the StreamResolutionException.
        // The walk stops at the first PlaybackException(NO_STREAM) it meets and returns its message —
        // it never needs to reach the inner StreamResolutionException on this shape.
        val reason = "members only"
        val mapped = playbackError(
            PlaybackErrorClassifier.ERROR_CODE_NO_STREAM,
            message = reason,
            cause = YTPlayerUtils.StreamResolutionException(reason),
        )
        val top = playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, cause = mapped)
        assertEquals(reason, PlaybackErrorClassifier.noStreamReason(top))
    }

    @Test
    fun noStreamReasonPrefersABareResolutionExceptionMetFirstInTheChain() {
        // At every level the StreamResolutionException check runs BEFORE the NO_STREAM-code check, so a
        // bare SRE met on the way down wins with its reason.
        val top = playbackError(0, cause = YTPlayerUtils.StreamResolutionException("region locked"))
        assertEquals("region locked", PlaybackErrorClassifier.noStreamReason(top))
    }

    @Test
    fun noStreamReasonFallsBackToMappedCodeMessageThenTopMessage() {
        val mapped = playbackError(PlaybackErrorClassifier.ERROR_CODE_NO_STREAM, message = "timed out resolving")
        val top = playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, message = "top", cause = mapped)
        assertEquals("timed out resolving", PlaybackErrorClassifier.noStreamReason(top))

        val plain = playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, message = "top message")
        assertEquals("top message", PlaybackErrorClassifier.noStreamReason(plain))
    }

    // --------------------------------------------------------- audio renderer

    @Test
    fun audioRendererErrorsByTopLevelCode() {
        assertTrue(
            PlaybackErrorClassifier.isAudioRendererError(
                playbackError(PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED),
            ),
        )
        assertTrue(
            PlaybackErrorClassifier.isAudioRendererError(
                playbackError(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED),
            ),
        )
        assertTrue(
            PlaybackErrorClassifier.isAudioRendererError(playbackError(PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK)),
        )
    }

    @Test
    fun audioRendererErrorsByCauseCode() {
        assertTrue(
            PlaybackErrorClassifier.isAudioRendererError(
                playbackError(0, cause = playbackError(PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED)),
            ),
        )
        assertTrue(
            PlaybackErrorClassifier.isAudioRendererError(
                playbackError(0, cause = playbackError(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)),
            ),
        )
    }

    @Test
    fun sourceErrorsAreNotAudioRendererErrors() {
        assertFalse(
            PlaybackErrorClassifier.isAudioRendererError(playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)),
        )
    }

    // ------------------------------------------------------- cache corruption

    @Test
    fun cacheCorruptionByTopLevelCode() {
        assertTrue(
            PlaybackErrorClassifier.isCacheOrStreamCorruptionError(
                playbackError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED),
            ),
        )
        assertTrue(
            PlaybackErrorClassifier.isCacheOrStreamCorruptionError(
                playbackError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED),
            ),
        )
        assertTrue(
            PlaybackErrorClassifier.isCacheOrStreamCorruptionError(
                playbackError(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE),
            ),
        )
    }

    /**
     * Top-level ONLY, on purpose. Walking the chain here was tried and reverted (registry #74): the
     * format-guard's own CONTAINER_MALFORMED surfaces at top level as IO_UNSPECIFIED and would be
     * misrouted, moving a SimpleCache unlink onto the main looper for zero behaviour gained.
     */
    @Test
    fun cacheCorruptionDoesNotWalkTheCauseChain() {
        val nested = playbackError(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            cause = playbackError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED),
        )
        assertFalse(PlaybackErrorClassifier.isCacheOrStreamCorruptionError(nested))
    }

    // ---------------------------------------------------------- NoDeclaredBrand

    @Test
    fun noDeclaredBrandFoundOnAnyLevelCaseInsensitive() {
        assertTrue(
            PlaybackErrorClassifier.hasNoDeclaredBrand(
                playbackError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, message = "NoDeclaredBrand"),
            ),
        )
        assertTrue(
            PlaybackErrorClassifier.hasNoDeclaredBrand(
                playbackError(0, cause = Exception("extractor", Exception("nodeclaredbrand in container"))),
            ),
        )
        assertFalse(
            PlaybackErrorClassifier.hasNoDeclaredBrand(playbackError(0, message = "Source error", cause = Exception("3003"))),
        )
    }
}
