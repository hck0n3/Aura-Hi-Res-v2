package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.utils.ArtistSyncPolicy
import iad1tya.echo.music.utils.needsOnlineBrowseResolution
import iad1tya.echo.music.utils.resolveOnlineArtistBrowseId
import iad1tya.echo.music.utils.toggleArtistFollow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * The player's «Suscribirse» chip (owner 2026-11-02: «cuando uno hace clic en el nombre del artista sale ir al álbum, ir
 * al artista, ir a la playlist; quiero que salga suscribirse al cantante, y si hay varios que pregunte a cuál; que
 * muestre si ya estoy suscrito»).
 *
 * The state read and the tap are the ones the artist page and the artist menus already use
 * (`isAnyArtistFollowed` over [ArtistSyncPolicy.aliasIds] and [toggleArtistFollow]), so the chip, the page and the menus
 * cannot disagree about who is followed.
 */
internal object PlayerArtistFollow {
    /** The artists the chip can act on: the ones with a name, once each. */
    fun candidates(artists: List<MediaMetadata.Artist>): List<MediaMetadata.Artist> =
        artists.filter { it.name.isNotBlank() }.distinctBy { it.id to it.name }

    /** The chip reads «Suscrito» only when there is at least one artist and EVERY one of them is followed. */
    fun allFollowed(followed: List<Boolean>): Boolean = followed.isNotEmpty() && followed.all { it }

    /** One artist: the tap toggles it. Several: the tap asks which one. */
    fun asksWhich(candidateCount: Int): Boolean = candidateCount > 1

    /**
     * The channel id the follow is stored under: the song's own id when it is a real channel, else the one resolved
     * from the name earlier in this session ([resolved], keyed by name), else null (not known yet).
     */
    fun knownId(artist: MediaMetadata.Artist, resolved: Map<String, String>): String? =
        resolved[artist.name] ?: artist.id?.takeIf { !needsOnlineBrowseResolution(it) }
}

/** Followed state per artist NAME for the artists of the playing song. A name with no known channel id reads false. */
@Composable
internal fun rememberPlayerArtistFollowed(
    database: MusicDatabase,
    candidates: List<MediaMetadata.Artist>,
    resolved: Map<String, String>,
): Map<String, Boolean> {
    val flow: Flow<Map<String, Boolean>> = remember(candidates, resolved) {
        if (candidates.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                candidates.map { artist ->
                    val id = PlayerArtistFollow.knownId(artist, resolved)
                    if (id == null) flowOf(false) else database.isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(id))
                },
            ) { states -> candidates.indices.associate { candidates[it].name to states[it] } }
        }
    }
    val map by flow.collectAsState(initial = emptyMap())
    return map
}

/**
 * Follows or unfollows [artist]. An artist the song only knows by name (a local row) is first looked up on YouTube
 * Music, as «Ver artista» already does; [onResolved] receives the id so the chip can read its state afterwards.
 * Returns false when no channel could be found (nothing was changed).
 */
internal suspend fun toggleFollowFromPlayer(
    database: MusicDatabase,
    artist: MediaMetadata.Artist,
    resolved: Map<String, String>,
    onResolved: (String) -> Unit,
): Boolean {
    val id = PlayerArtistFollow.knownId(artist, resolved)
        ?: withContext(Dispatchers.IO) { resolveOnlineArtistBrowseId(artist.name) }?.also(onResolved)
        ?: return false
    withContext(Dispatchers.IO) {
        database.toggleArtistFollow(id, ArtistEntity(id = id, name = artist.name))
    }
    return true
}
