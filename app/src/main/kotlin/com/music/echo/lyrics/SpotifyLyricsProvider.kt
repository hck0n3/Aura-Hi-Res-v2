package iad1tya.echo.music.lyrics

import android.content.Context
import iad1tya.echo.music.constants.EnableSpotifyLyricsKey
import iad1tya.echo.music.spotify.SpotifyMediaClient
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * SPOTIFY LYRICS (SimpMusic "Enable Spotify Lyrics" parity): the color-lyrics endpoint a logged-in
 * Spotify web player uses, applied to Aura tracks by resolving them through Spotify's searchTracks
 * persisted query. Opt-in AND login-gated — SimpMusic disables the toggle when the user is not
 * logged into Spotify, and color-lyrics needs a real sp_dc session token (anonymous tokens are
 * refused, so every request would be a guaranteed 401/403).
 *
 * Returns plain LRC text so it drops into the existing LyricsHelper pipeline (synced detection,
 * caching, "all providers" sheet) with zero special casing: a Spotify hit looks exactly like any
 * other synced provider to the rest of the app.
 */
object SpotifyLyricsProvider : LyricsProvider {
    override val name = "Spotify"

    override fun isEnabled(context: Context): Boolean {
        val toggle = context.dataStore[EnableSpotifyLyricsKey] ?: false
        if (!toggle) return false
        return true
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        val result = runCatching {
            val personalToken = SpotifyMediaClient.personalToken()
                ?: throw IllegalStateException("Spotify session required")
            val clientToken = SpotifyMediaClient.clientToken()
                ?: throw IllegalStateException("Spotify client token unavailable")

            val match = SpotifyMediaClient.searchTrack(
                query = buildQuery(title, artist),
                durationSeconds = duration.takeIf { it > 0 },
                personalToken = personalToken,
                clientToken = clientToken,
            ) ?: throw IllegalStateException("No Spotify track match")

            val lines = SpotifyMediaClient.getLyrics(
                trackId = match.trackId,
                personalToken = personalToken,
                clientToken = clientToken,
            ) ?: throw IllegalStateException("Lyrics unavailable")

            toLrc(lines)
        }
        result.onFailure { e ->
            if (e is CancellationException) throw e
            val msg = e.message?.take(120) ?: e.javaClass.simpleName
            Timber.tag(name).d("Lyrics fetch failed: $msg")
        }
        return result
    }

    /** Same noise cleanup SimpMusic runs before searching (feat/ft lists, punctuation). */
    private fun buildQuery(title: String, artist: String): String {
        val cleaned = "$title $artist"
            .replace(Regex("\\((feat\\.|ft\\.) [^)]*\\)"), " ")
            .replace(Regex("([()])"), "")
            .replace(".", " ")
            .replace("  ", " ")
            .trim()
        return cleaned.ifBlank { "$title $artist" }
    }

    private fun toLrc(lines: List<SpotifyMediaClient.SpotifyLyricsLine>): String =
        lines.joinToString("\n") { line ->
            val totalSeconds = line.startTimeMs / 1000.0
            val minutes = (totalSeconds / 60).toInt()
            val seconds = totalSeconds - minutes * 60
            "[%02d:%05.2f] %s".format(minutes, seconds, line.words)
        }
}
