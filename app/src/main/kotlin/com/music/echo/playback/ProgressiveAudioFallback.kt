package iad1tya.echo.music.playback

/**
 * Which progressive (muxed) stream to play when a video has NO audio-only format.
 *
 * 🔴 OWNER REPORT (2026-09-16): *"necesito que todos los videos se puedan reproducir en modo audio,
 * porque algunos videos solo reproducían video"*.
 *
 * ## Why some videos were video-only
 * YouTube serves two kinds of stream. `adaptiveFormats` holds separate video-only and audio-only tracks;
 * `formats` holds the old **progressive** streams (itag 18 / 22), where picture and sound travel together
 * in one file. Aura's audio resolver looked at the first list only:
 *
 *     adaptiveFormats.filter { it.isAudio }      // isAudio == (width == null)
 *
 * while the video resolver deliberately searched BOTH lists ("Search BOTH adaptiveFormats and formats to
 * ensure we always find a video stream if one exists"). A progressive stream has a width, so `isAudio` is
 * false for it — it was invisible to the audio path even though it carries perfectly good audio.
 *
 * So for a video whose response has no adaptive audio (TVHTML5-style clients return exactly this — the
 * comment on `StreamingData.adaptiveFormats` in the innertube module even defends the defaults that keep
 * "a usable muxed `formats` list" alive), the audio resolver found nothing, moved to the next client, and
 * eventually gave up — while the video path played happily. From the outside: this song only works as
 * video.
 *
 * ## What this decides
 * The stream to fall back to, and nothing else. It never runs when real audio-only formats exist, so
 * normal tracks resolve byte-identically to before.
 *
 * Progressive means downloading a picture nobody is watching, so the choice is deliberately conservative:
 * on mobile data, with Data Saver on, or when the caller only needs a few seconds (the ringtone trimmer),
 * take the SMALLEST stream — itag 18, 360p, ~96 kbps AAC. Otherwise take the largest, which is itag 22
 * (720p, ~192 kbps AAC): unwatched pixels are a fair price for double the audio bitrate on WiFi, and this
 * is a Hi-Res player.
 */
object ProgressiveAudioFallback {
    /**
     * One progressive candidate, reduced to what the decision needs.
     *
     * [hasAudioTrack] matters because `formats` is not guaranteed to be muxed-only: a stream with no audio
     * would play as silence, which is worse than the failure it replaces.
     */
    data class Stream(
        val itag: Int,
        val bitrate: Int,
        val hasAudioTrack: Boolean,
        val hasUrl: Boolean,
    )

    /**
     * Index into [streams] of the stream to play, or null when none can carry audio.
     *
     * Returns an INDEX rather than the stream so the caller maps straight back to its own richer format
     * object without matching on itag — two entries could in principle share one.
     */
    fun pickIndex(
        streams: List<Stream>,
        preferSmallest: Boolean,
    ): Int? {
        val usable = streams.indices.filter { streams[it].hasAudioTrack && streams[it].hasUrl }
        if (usable.isEmpty()) return null
        return if (preferSmallest) {
            usable.minByOrNull { streams[it].bitrate }
        } else {
            usable.maxByOrNull { streams[it].bitrate }
        }
    }
}
