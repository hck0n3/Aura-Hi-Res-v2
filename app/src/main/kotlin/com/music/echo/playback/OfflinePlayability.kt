package iad1tya.echo.music.playback

/**
 * ONE answer to "does this song play with zero network?" (registry 485 asked for one definition; there were four).
 *
 * It is the order and the conditions of the stream resolver (`createDataSourceFactory` in StreamResolution.kt), which
 * is what really decides: a local file; a COMPLETE download; an exported file that still exists; a COMPLETE listen-cache
 * copy — the last one only when the download cache holds no span of the song, because the resolver never mixes download
 * bytes with listen bytes (it skips that copy and refuses the song offline). Anything else needs the network.
 *
 * The inputs are lambdas so the cheap ones short-circuit the expensive ones (a listen-cache key scan, a SAF lookup).
 */
internal object OfflinePlayability {
    enum class Source { LOCAL, DOWNLOAD, EXPORTED, LISTEN_CACHE }

    fun source(
        isLocal: Boolean,
        downloadComplete: () -> Boolean,
        exportedFileExists: () -> Boolean,
        downloadHasAnySpan: () -> Boolean,
        listenCacheComplete: () -> Boolean,
    ): Source? = when {
        isLocal -> Source.LOCAL
        downloadComplete() -> Source.DOWNLOAD
        exportedFileExists() -> Source.EXPORTED
        !downloadHasAnySpan() && listenCacheComplete() -> Source.LISTEN_CACHE
        else -> null
    }

    /** "Every byte of the audio is on disk in the caches" (the scrubber's fully-loaded paint, the unavailable-song filter). */
    fun isCacheSource(source: Source?): Boolean = source == Source.DOWNLOAD || source == Source.LISTEN_CACHE
}
