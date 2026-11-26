package iad1tya.echo.music.ui.newui

/*
 * «Pantalla» of the TV player (plan, phase 6.2; registry row 896). Pure, so the cycle is tested on the JVM. The three
 * views are the player's own, never copies: the cover (wide shape), the lyrics (`showInlineLyrics` → InlineLyricsView)
 * and the video (`videoMode` → PlayerVideoSurface / AuraImmersiveVideo).
 */

/** What the TV player shows. */
enum class AuraScreenMode { COVER, LYRICS, VIDEO }

/** The view on screen now, from the player's two existing states. Video wins: opening it closes the lyrics. */
fun auraScreenModeOf(videoMode: Boolean, lyricsOpen: Boolean): AuraScreenMode = when {
    videoMode -> AuraScreenMode.VIDEO
    lyricsOpen -> AuraScreenMode.LYRICS
    else -> AuraScreenMode.COVER
}

/**
 * Does this song have a video to show? The same terms as the player's «Vídeo» chip: a music video not known to be
 * incompatible (`MediaMetadata.hasCompatibleVideo`) or a podcast episode with its own video.
 */
fun auraScreenOffersVideo(hasCompatibleVideo: Boolean, podcastVideoUrl: String?): Boolean =
    hasCompatibleVideo || !podcastVideoUrl.isNullOrEmpty()

/** portada → letra → vídeo → portada; vídeo is skipped for a song without video. */
fun nextAuraScreenMode(current: AuraScreenMode, videoOffered: Boolean): AuraScreenMode = when (current) {
    AuraScreenMode.COVER -> AuraScreenMode.LYRICS
    AuraScreenMode.LYRICS -> if (videoOffered) AuraScreenMode.VIDEO else AuraScreenMode.COVER
    AuraScreenMode.VIDEO -> AuraScreenMode.COVER
}

/**
 * Is «Pantalla» offered? Always, except in video when the song's audio is known to be refused (`SongModePlanning
 * .offerSongOption` false): leaving the video would only bring the error, exactly why the «Canción» chip hides then.
 */
fun auraScreenCycleOffered(current: AuraScreenMode, canLeaveVideo: Boolean): Boolean =
    current != AuraScreenMode.VIDEO || canLeaveVideo
