package iad1tya.echo.music.cloud

import android.media.MediaMetadataRetriever
import iad1tya.echo.music.localmedia.parseTagNumber

/**
 * Registry row 891 — reads one cloud file's tags WITHOUT downloading it: Android's MediaMetadataRetriever opens the
 * serve URL (with its Authorization header) and its HTTP source fetches only the byte ranges the container needs (the
 * header blocks of FLAC/MP3/OGG, the `moov` box of an M4A wherever it is).
 *
 * Chosen over the ffprobe path of rows 724-725: that one reads a local path or a SAF descriptor; for a URL it would need
 * FFmpegKit's http protocol (not checked in the build Aura ships) with the credentials in its argument list. The platform
 * retriever takes the header apart from the URL. What it does not read: ReplayGain (a cloud song is levelled by the
 * unknown-loudness default, like a phone song without the tag).
 *
 * Null when the file could not be read (the next index tries again); a [CloudTags] with every field null when it was read
 * and has no tags (the title is then the file name, [CloudTagFallback]). Blocking: call it off the main thread.
 */
internal object CloudTagReader {
    fun read(url: String, authorization: String): CloudTags? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, mapOf("Authorization" to authorization))
            fun text(key: Int): String? = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            CloudTags(
                title = text(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = text(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumArtist = text(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                year = parseTagNumber(text(MediaMetadataRetriever.METADATA_KEY_YEAR)),
                disc = parseTagNumber(text(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)),
                track = parseTagNumber(text(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                durationMs = text(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 },
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
