package iad1tya.echo.music.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import iad1tya.echo.music.utils.YTPlayerUtils

/**
 * Pure classification of player errors: "what KIND of failure is this?" with no service state, no side
 * effects and no recovery policy. Extracted verbatim from MusicService.onPlayerError's helpers so the
 * routing decisions that used to be reachable only through a live service can be pinned by tests
 * (HALLAZGO-021 split, phase A). MusicService keeps the ROUTING (which handler runs, retry counts,
 * cache purges); this object only answers the yes/no questions the `when` branches ask.
 *
 * Invariants these predicates protect (docs/REGRESSION_REGISTRY.md):
 *  - 403 / 416 / page-reload are three DIFFERENT recoveries and must never be classified as "network"
 *    (isNetworkRelatedError rejects them first) — a bounded network retry on a dead deciphered URL is
 *    not the same thing as waiting for the link to come back.
 *  - NO_STREAM is a dead-end, not a network error: it must fail fast with a reason + skip, and the
 *    real reason lives on the INNERMOST StreamResolutionException, 2+ cause levels deep.
 *  - Cache/stream corruption is judged on the TOP-LEVEL code only; walking the cause chain there was
 *    tried and reverted (registry #74: it moved a SimpleCache unlink onto the main looper for zero
 *    behaviour gained).
 */
object PlaybackErrorClassifier {

    /**
     * Unresolvable-song dead-end code (see YTPlayerUtils.StreamResolutionException, mapped to this code
     * in the loader). Deliberately NOT a media3 network code so classification can never confuse a
     * genuinely unplayable song with a transient link failure.
     */
    const val ERROR_CODE_NO_STREAM = 1000001

    /**
     * The HTTP status behind the failure, if any. Walks the WHOLE cause chain: the
     * InvalidResponseCodeException is thrown inside data sources and gets wrapped by media3's Loader
     * and by ExoPlayer, so it routinely sits 1-2 levels below the top-level PlaybackException.
     */
    fun httpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    /** Expired / forbidden stream URL (403). Recovery: drop songUrlCache + decryption cache, KEEP the bytes. */
    fun isExpiredUrlError(error: PlaybackException): Boolean = httpResponseCode(error) == 403

    /** Range Not Satisfiable (416). Recovery: strict re-prepare with a purged cache. */
    fun isRangeNotSatisfiableError(error: PlaybackException): Boolean = httpResponseCode(error) == 416

    /**
     * YouTube's "page needs to be reloaded" family. Matched on localised keyword triples because the
     * message text is server-localised (owner's logs show Italian and Spanish variants), checked on the
     * top message plus two cause levels.
     */
    fun isPageReloadError(error: PlaybackException): Boolean {
        val errorMessage = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val innerCauseMessage = error.cause?.cause?.message?.lowercase() ?: ""

        val reloadKeywords = listOf(
            "page needs to be reloaded",
            "pagina deve essere ricaricata",
            "la pagina deve essere ricaricata",
            "page must be reloaded",
            "reload",
            "ricaricata",
        )

        return reloadKeywords.any { keyword ->
            errorMessage.contains(keyword) ||
                causeMessage.contains(keyword) ||
                innerCauseMessage.contains(keyword)
        }
    }

    /**
     * "Looks like the network is the problem" — but ONLY when it is not one of the three URL-shaped
     * errors above. Those have their own recoveries; routing them through the bounded network retry
     * would re-fetch the identical dead URL three times and then skip a song a fresh resolve would
     * have played.
     */
    fun isNetworkRelatedError(error: PlaybackException): Boolean {
        if (isExpiredUrlError(error) || isRangeNotSatisfiableError(error) || isPageReloadError(error)) {
            return false
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
            error.cause is java.net.ConnectException ||
            error.cause is java.net.UnknownHostException ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    }

    /**
     * Unresolvable-song dead-end. Walk the WHOLE cause chain: our PlaybackException(NO_STREAM) is
     * thrown from inside the ResolvingDataSource, so media3's Loader wraps it in
     * UnexpectedLoaderException (an IOException) and ExoPlayer wraps THAT again — the NO_STREAM code /
     * StreamResolutionException ends up 2+ levels deep, so a one-level check would miss it and fall
     * through to handleGenericIOError (the exact "stuck / never loads" behavior this fix kills).
     * Anchoring on StreamResolutionException is most robust.
     */
    fun isNoStreamError(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is YTPlayerUtils.StreamResolutionException) return true
            if (cause is PlaybackException && cause.errorCode == ERROR_CODE_NO_STREAM) return true
            cause = cause.cause
        }
        return false
    }

    /**
     * The real, user-facing reason for an unresolvable song lives on the innermost
     * StreamResolutionException (region-locked / premium / members-only / timed-out …), NOT on the
     * top-level ExoPlaybackException whose message is a generic loader string. Walk the chain to
     * recover it so the toast surfaces WHY.
     */
    fun noStreamReason(error: PlaybackException): String? {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is YTPlayerUtils.StreamResolutionException) return cause.reason
            if (cause is PlaybackException && cause.errorCode == ERROR_CODE_NO_STREAM) return cause.message
            cause = cause.cause
        }
        return error.message
    }

    /** The SINK failed (AudioTrack), not the source. Recovery is a safe player re-create, never a cache purge. */
    fun isAudioRendererError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
    }

    /**
     * The cached BYTES are bad. Top-level code only. Walking the cause chain here was tried and
     * reverted: the format-guard's own CONTAINER_MALFORMED surfaces at top level as IO_UNSPECIFIED and
     * would route to handleExpiredUrlError instead of handleGenericIOError — but both do the same
     * purge + re-prepare, so the only real effect was moving a SimpleCache file-unlink onto the main
     * looper inside onPlayerError (the exact cost registry #74 flagged). No behaviour gained, a
     * main-thread cost added.
     *
     * CONTAINER_UNSUPPORTED (3003) + NoDeclaredBrand: googlevideo often returns HTML/empty when the
     * URL is dead or n-transform failed — not a playable container. Treat like a bad stream so we
     * drop the cached URL and re-resolve (owner log 0.6.162: Source error / extractors could not read).
     */
    fun isCacheOrStreamCorruptionError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
    }

    /**
     * googlevideo's "not a media container" marker (HTML/empty reply). Checked on the message of every
     * cause level because the extractor may attach it below the top-level PlaybackException.
     */
    fun hasNoDeclaredBrand(error: PlaybackException): Boolean =
        error.message?.contains("NoDeclaredBrand", ignoreCase = true) == true ||
            generateSequence(error as Throwable?) { it.cause }
                .any { it.message?.contains("NoDeclaredBrand", ignoreCase = true) == true }
}
