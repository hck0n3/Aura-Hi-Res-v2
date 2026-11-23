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
     * Below this many comparable characters a name says too little to pick a channel by: [ArtistNameMatching.normalize] keeps
     * only `[a-z0-9 ]`, so «Би-2» is «2» and «宇多田ヒカル» is empty.
     */
    const val MIN_NAME_CHARS = 3

    fun canResolveByName(name: String): Boolean = ArtistNameMatching.normalize(name).length >= MIN_NAME_CHARS

    /** One artist search result: the channel the user is asked to confirm. */
    data class Candidate(val id: String, val title: String, val thumbnailUrl: String?)

    /**
     * A song whose artist has no channel id (a local file, a `LA…` row) is matched to a channel by EXACT name only. A partial
     * match would subscribe the account to a stranger — the phantom subscriptions of row 646.
     *
     * @param candidates the artist search results, in YouTube's order.
     */
    fun pickExactChannel(name: String, candidates: List<Candidate>): Candidate? {
        if (!canResolveByName(name)) return null
        val wanted = ArtistNameMatching.normalize(name)
        return candidates.firstOrNull { isChannelId(it.id) && ArtistNameMatching.normalize(it.title) == wanted }
    }

    sealed interface Resolution {
        /** The song's own channel id, or one the user already confirmed for this song: the tap acts on it. */
        data class Known(val channelId: String) : Resolution

        /** Found by name: the user confirms that channel before anything is subscribed (row 646). */
        data class ByName(val channel: Candidate) : Resolution

        /** No channel carries this exact name, or the name is too short to search by. */
        data object NotFound : Resolution

        /** The search itself failed (offline, a request error): not the artist's fault. */
        data object SearchFailed : Resolution
    }

    /** What can be decided without the network; null means «search by name». */
    fun resolveLocally(artist: MediaMetadata.Artist, knownChannelId: String?): Resolution? = when {
        knownChannelId != null -> Resolution.Known(knownChannelId)
        artist.id != null && isChannelId(artist.id) -> Resolution.Known(artist.id)
        !canResolveByName(artist.name) -> Resolution.NotFound
        else -> null
    }

    fun fromSearch(name: String, results: Result<List<Candidate>>): Resolution = results.fold(
        onSuccess = { found -> pickExactChannel(name, found)?.let { Resolution.ByName(it) } ?: Resolution.NotFound },
        onFailure = { Resolution.SearchFailed },
    )

    enum class Several { NONE, SOME, ALL }

    /** The several-artists chip: «Suscrito» when all are followed, «Suscrito a N de M» when some, «Suscribirme» when none. */
    fun several(followed: List<Boolean>): Several = when {
        followed.isNotEmpty() && followed.all { it } -> Several.ALL
        followed.any { it } -> Several.SOME
        else -> Several.NONE
    }
}

/** A channel found by name, waiting for the user's «¿Suscribirte a …?»; only for the song ([songId]) it was found for. */
internal data class PendingPlayerFollow(val songId: String, val key: String, val channel: PlayerArtistFollow.Candidate)

/** Live follow state of [channelId] under every spelling of it, read off `followedByUserAt` only (row 154). */
@Composable
internal fun rememberPlayerArtistFollowed(database: MusicDatabase, channelId: String?): State<Boolean> =
    remember(channelId) {
        if (channelId == null) flowOf(false) else database.isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(channelId))
    }.collectAsState(initial = false)

/** [PlayerArtistFollow.resolveLocally], else an artist search matched by exact name. */
internal suspend fun resolvePlayerArtistChannel(
    artist: MediaMetadata.Artist,
    knownChannelId: String?,
): PlayerArtistFollow.Resolution =
    PlayerArtistFollow.resolveLocally(artist, knownChannelId) ?: withContext(Dispatchers.IO) {
        PlayerArtistFollow.fromSearch(
            artist.name,
            YouTube.search(artist.name.trim(), YouTube.SearchFilter.FILTER_ARTIST).map { result ->
                result.items.filterIsInstance<ArtistItem>().map { PlayerArtistFollow.Candidate(it.id, it.title, it.thumbnail) }
            },
        )
    }

internal suspend fun MusicDatabase.isPlayerArtistFollowed(channelId: String): Boolean =
    isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(channelId)).first()

/** Toggles [channelId] only when [PlayerArtistFollow.shouldToggle] agrees with the state the user saw. */
internal suspend fun MusicDatabase.toggleFollowIfStillShown(channelId: String, name: String, shownFollowed: Boolean) {
    if (PlayerArtistFollow.shouldToggle(shownFollowed, isPlayerArtistFollowed(channelId))) {
        // Row 716: compared again inside the toggle's transaction, so a write landing between the two reads is seen.
        toggleArtistFollow(channelId, ArtistEntity(id = channelId, name = name), shownFollowed = shownFollowed)
    }
}
