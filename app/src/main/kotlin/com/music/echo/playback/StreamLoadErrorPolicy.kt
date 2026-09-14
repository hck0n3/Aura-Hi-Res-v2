package iad1tya.echo.music.playback

import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Stops media3 from retrying a DEAD stream URL. The default policy retries every load error with a
 * growing delay capped at 5 s, and a progressive source only surfaces the error once its buffer runs
 * dry — so a googlevideo URL that answers 403 was re-requested ~55 times over 4+ minutes (app.log
 * 2026-09-13 17:49–17:53) before MusicService's expired-URL recovery could re-resolve it. A 403/410
 * never heals by retrying the same URL, so it is made fatal at once; onPlayerError then refreshes the
 * URL (bounded by MAX_RETRY_PER_SONG). Every other error keeps the default behaviour.
 */
internal class StreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val code = deadResponseCodeIn(loadErrorInfo.exception) {
            (it as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        }
        return if (code != null) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
    }

    internal companion object {
        private val DEAD_URL_CODES = setOf(403, 410)
        private const val MAX_CAUSE_DEPTH = 8

        /** The first dead-URL HTTP status found in [error]'s cause chain, or null. */
        fun deadResponseCodeIn(error: Throwable, responseCodeOf: (Throwable) -> Int?): Int? {
            var current: Throwable? = error
            repeat(MAX_CAUSE_DEPTH) {
                val throwable = current ?: return null
                responseCodeOf(throwable)?.takeIf { it in DEAD_URL_CODES }?.let { return it }
                current = throwable.cause?.takeIf { it !== throwable }
            }
            return null
        }
    }
}
