package iad1tya.echo.music.playback

/**
 * Android Auto browse ids (registry row 883). Pure Kotlin, unit-tested on the JVM.
 *
 * A browse id is `<node>/<…>/<songId>`, read back with `split("/")`. A song on this phone has a MediaStore id
 * (`content://media/external/audio/media/12`) that is full of slashes, so the old split cut it into pieces: a tap
 * on a phone song in a search result, a playlist or Me gusta looked up `12` instead of the song and the queue
 * started on another one (or on nothing). [segments] keeps such an id whole as the last segment.
 */
object AutoBrowsePath {
    private val LocalSchemes = listOf("content://", "file://", "android.resource://")

    /**
     * Same segments as `mediaId.split("/")`, except that a local media id (from its scheme to the end) stays ONE
     * segment. An id with no local scheme is split exactly as before.
     */
    fun segments(mediaId: String): List<String> {
        val start = LocalSchemes
            .mapNotNull { scheme -> localIdStart(mediaId, scheme) }
            .minOrNull()
            ?: return mediaId.split("/")
        if (start == 0) return listOf(mediaId)
        return mediaId.substring(0, start - 1).split("/") + mediaId.substring(start)
    }

    /** First index where [scheme] starts a whole segment (at the start of the id or right after a `/`). */
    private fun localIdStart(mediaId: String, scheme: String): Int? {
        var from = 0
        while (true) {
            val index = mediaId.indexOf(scheme, from, ignoreCase = true)
            if (index < 0) return null
            if (index == 0 || mediaId[index - 1] == '/') return index
            from = index + 1
        }
    }
}
