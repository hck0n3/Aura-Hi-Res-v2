package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.spotifyimport.ArtistNameMatching
import iad1tya.echo.music.utils.ArtistSyncPolicy
import iad1tya.echo.music.utils.toggleArtistFollow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * The player's «Suscribirme / Suscrito» (owner 2026-11-22). The tap itself is [toggleArtistFollow] — the SAME one the artist
 * page and the artist menus use, so the live account call, `followedByUserAt`/`unfollowedByUserAt` and
 * `confirmArtistUnsubscribed` are untouched (rows 106, 107, 154). What is new here is only how the player gets a channel id.
 */
internal object PlayerArtistFollow {
    /** A YouTube channel (`UC…`, or its library spelling `MPLAUC…`): the only ids a subscription can reach the account under. */
    fun isChannelId(id: String?): Boolean =
        id != null && id.removePrefix(ArtistSyncPolicy.LIBRARY_ARTIST_PREFIX).startsWith("UC")

    /** One entry per artist of the playing song, for the resolved-id memory and the tap guard. */
    fun key(artist: MediaMetadata.Artist): String = "${artist.id}_${artist.name}"

    /**
     * The tap toggles only while the account state is still the one the user SAW. A chip that still read «Suscribirme» for an
     * artist who turns out to be followed (its row just loaded, or the name resolved to a channel already followed) does nothing,
     * instead of unsubscribing.
     */
    fun shouldToggle(shownFollowed: Boolean, followedNow: Boolean): Boolean = shownFollowed == followedNow

    /**
     * A song whose artist has no channel id (a local file, a `LA…` row) is matched to a channel by EXACT name only. A partial
     * match would subscribe the account to a stranger — the phantom subscriptions of row 646.
     *
     * @param candidates `(id, title)` of the artist search results, in YouTube's order.
     */
    fun pickExactChannel(name: String, candidates: List<Pair<String, String>>): String? {
        val wanted = ArtistNameMatching.normalize(name)
        if (wanted.isEmpty()) return null
        return candidates.firstOrNull { (id, title) -> isChannelId(id) && ArtistNameMatching.normalize(title) == wanted }?.first
    }
}

/** Live follow state of [channelId] under every spelling of it, read off `followedByUserAt` only (row 154). */
@Composable
internal fun rememberPlayerArtistFollowed(database: MusicDatabase, channelId: String?): State<Boolean> =
    remember(channelId) {
        if (channelId == null) flowOf(false) else database.isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(channelId))
    }.collectAsState(initial = false)

/**
 * Resolves the channel of [artist] ([knownChannelId] from an earlier tap, else its own id, else an exact-name search), then
 * toggles it when [PlayerArtistFollow.shouldToggle] agrees. Returns the channel id, or null when there is none to subscribe to.
 */
internal suspend fun MusicDatabase.toggleFollowFromPlayer(
    artist: MediaMetadata.Artist,
    knownChannelId: String?,
    shownFollowed: Boolean,
): String? {
    val channelId = knownChannelId
        ?: artist.id?.takeIf { PlayerArtistFollow.isChannelId(it) }
        ?: withContext(Dispatchers.IO) {
            val result = YouTube.search(artist.name.trim(), YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
            PlayerArtistFollow.pickExactChannel(
                artist.name,
                result?.items.orEmpty().filterIsInstance<ArtistItem>().map { it.id to it.title },
            )
        }
        ?: return null
    val followedNow = isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(channelId)).first()
    if (PlayerArtistFollow.shouldToggle(shownFollowed, followedNow)) {
        toggleArtistFollow(channelId, ArtistEntity(id = channelId, name = artist.name))
    }
    return channelId
}
