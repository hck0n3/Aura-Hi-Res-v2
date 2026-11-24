package iad1tya.echo.music.spotifyimport

import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.security.MessageDigest

/**
 * Registry row 832 — a scheduled Spotify pass re-marked as liked every song of Spotify's «Liked Songs», including one the user
 * had un-liked in Aura since. Only a song NEW in Spotify since the last pass is marked now.
 *
 * Per Spotify account (one DataStore set per account, [keyOf]), the Spotify track ids already imported as likes. Never forgets an
 * id: a pass whose Spotify read came back short must not make the next pass re-like what it missed. Before an account has a set
 * (first pass after this change, or the account could not be read), the songs already in the local «Liked Songs» mirror count as
 * seen: the mirror holds exactly what the previous pass matched. Pure apart from the key, for JVM tests.
 */
object SpotifyLikedImportLedger {
    /** Separate from the artist import's sets (K1a). */
    fun keyOf(tag: String) = stringSetPreferencesKey("spotify_liked_imported_ids@$tag")

    /** A short hash of the Spotify user id: only to tell one account from another; the id itself is never stored nor logged. */
    fun tagOf(spotifyUserId: String?): String? {
        val id = spotifyUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
    }

    /** The Spotify track id, or the matched video id for a track Spotify gave no id. */
    fun trackKey(spotifyTrackId: String, videoId: String): String = spotifyTrackId.ifBlank { "yt:$videoId" }

    /**
     * The video ids to mark as liked. [matched] = (track key, video id) of this pass; [previous] = the account's set (null = none
     * yet); [mirrorSongIds] = the video ids already in the local «Liked Songs» mirror.
     */
    fun toMark(matched: List<Pair<String, String>>, previous: Set<String>?, mirrorSongIds: Set<String>): Set<String> =
        matched.filter { (key, videoId) -> if (previous != null) key !in previous else videoId !in mirrorSongIds }
            .mapTo(LinkedHashSet()) { it.second }

    /** The set to store after this pass. */
    fun next(previous: Set<String>?, matched: List<Pair<String, String>>): Set<String> =
        previous.orEmpty() + matched.map { it.first }
}
