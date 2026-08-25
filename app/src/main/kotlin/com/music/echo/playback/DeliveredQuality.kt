package iad1tya.echo.music.playback

import iad1tya.echo.music.constants.AudioQuality

/**
 * Classifies a resolved stream's ACTUAL container (its MIME type) into the [AudioQuality] tier the
 * bytes belong to, extracted verbatim from the three inline copies in MusicService (HALLAZGO-021
 * split, phase A).
 *
 * This predicate is the single source of truth for the "delivered quality" stamp: the refresh-ahead
 * renewal, the upcoming-track preload and the post-resolve container guard (registry lessons #40 and
 * #57) must all agree on it — a drift between the stamp and the guard is what made the format guard
 * mis-fire on every open for a LOSSLESS/SAAVN user (#57). flac wins over mp4/m4a when a MIME type
 * mentions both; anything that is neither flac nor an mp4/m4a container classifies as OPUS.
 */
object DeliveredQuality {

    /** The delivered bytes are a FLAC container. */
    fun isLossless(mimeType: String): Boolean = mimeType.contains("flac", ignoreCase = true)

    /** The delivered bytes are an mp4/m4a container (the SAAVN tier). */
    fun isSaavn(mimeType: String): Boolean =
        mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true)

    /** The tier for [mimeType]: flac first, then mp4/m4a, everything else OPUS. */
    fun fromMimeType(mimeType: String): AudioQuality = when {
        isLossless(mimeType) -> AudioQuality.LOSSLESS
        isSaavn(mimeType) -> AudioQuality.SAAVN
        else -> AudioQuality.OPUS
    }
}
