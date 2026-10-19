package iad1tya.echo.music.playback

/**
 * What the live queue holds right now, captured on the player thread before a smart-queue batch is ordered
 * (see MusicService.liveQueueSnapshot): the ids and [NoRepeatKey]s that may not come back, and the primary
 * artists (lowercase, oldest first) of the songs the batch will follow, so the seam between two batches is
 * spaced like the inside of one. Plain data: no Android types.
 */
internal class LiveQueueSnapshot(
    val ids: Set<String>,
    val keys: Set<String>,
    val tailArtists: List<String> = emptyList(),
)
