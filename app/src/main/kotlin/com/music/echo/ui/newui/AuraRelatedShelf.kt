package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.playback.queues.YouTubeQueue
import java.util.Locale

/**
 * «También te puede gustar» at the foot of a playlist the user FOLLOWS (not one they created). Owner 2026-10-18: *«las
 * listas que sigo, que no he creado yo, al final de la lista no me sale nada de sugerencia de similar»*.
 *
 * The suggestions of a playlist you own are songs to ADD to it ([iad1tya.echo.music.ui.screens.playlist.SuggestedSongsSection]);
 * a followed playlist cannot take songs, so its footer is what YouTube Music shows under any playlist: related playlists,
 * albums, artists and songs to open or play. Same card and same routing as the online playlist page.
 */
@Composable
internal fun AuraRelatedShelf(
    items: List<YTItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val playerConnection = LocalPlayerConnection.current ?: return
    AuraSectionLabel(
        text = stringResource(R.string.album_you_might_also_like).uppercase(Locale.ROOT),
        modifier = modifier.padding(
            start = AuraSpacing.Gutter,
            end = AuraSpacing.Gutter,
            top = AuraSpacing.SectionTop,
            bottom = AuraSpacing.SectionGap,
        ),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        // POSITION as the key: the list comes from the server and entries can share an id (beta 0.6.148-beta1 crashed on it).
        itemsIndexed(items, key = { position, _ -> "aura_related_$position" }) { _, item ->
            AuraTypedYtCoverCard(
                item = item,
                cardScale = 1f,
                onClick = {
                    when (item) {
                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                        is AlbumItem -> navController.navigate("album/${item.browseId}")
                        is ArtistItem -> navController.navigate("artist/${item.id}")
                        is SongItem -> playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id)))
                        else -> Unit
                    }
                },
            )
        }
    }
}
