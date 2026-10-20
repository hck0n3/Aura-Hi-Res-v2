

package iad1tya.echo.music.ui.menu

import iad1tya.echo.music.ui.component.MenuLikeButton
import iad1tya.echo.music.ui.component.MenuHeaderCover
import iad1tya.echo.music.ui.component.MenuHeader
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.innertube.models.ArtistItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.applicationScope
import iad1tya.echo.music.utils.toggleArtistFollow
import iad1tya.echo.music.db.entities.SpeedDialItem
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.Material3MenuGroup
import iad1tya.echo.music.ui.component.Material3MenuItemData
import iad1tya.echo.music.ui.component.NewAction
import iad1tya.echo.music.ui.component.NewActionGrid
import iad1tya.echo.music.ui.component.YouTubeListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun YouTubeArtistMenu(
    artist: ArtistItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    // Same definition as the artist page (ArtistViewModel.isSubscribed): any spelling of the channel id counts.
    val isSubscribed by database.isAnyArtistFollowed(iad1tya.echo.music.utils.ArtistSyncPolicy.aliasIds(artist.id))
        .collectAsState(initial = false)
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val isPinned by database.speedDialDao.isPinned(artist.id).collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()

    MenuHeader(
        title = artist.title,
        thumbnail = { MenuHeaderCover(thumbnailUrl = artist.thumbnail, circle = true) },
    )

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = buildList {
                    if (!isGuest) {
                        artist.radioEndpoint?.let { watchEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.radio),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    },
                                    text = stringResource(R.string.start_radio),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(watchEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }

                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.share),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            text = stringResource(R.string.share),
                            onClick = {
                                val intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, iad1tya.echo.music.utils.ShareLinks.artistShareText(artist.id, artist.title))
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                                onDismiss()
                            }
                        )
                    )
                },
                modifier = Modifier.padding(bottom = 12.dp),
                columns = if (isGuest) 1 else 3
            )
        }

        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = {
                            Text(text = if (isSubscribed) stringResource(R.string.subscribed) else stringResource(R.string.subscribe))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(
                                    if (isSubscribed) {
                                        R.drawable.subscribed
                                    } else {
                                        R.drawable.subscribe
                                    }
                                ),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            // Application scope: the menu can close right after the tap and the tap must still land.
                            context.applicationScope.launch(Dispatchers.IO) {
                                database.toggleArtistFollow(
                                    artist.id,
                                    ArtistEntity(
                                        id = artist.id,
                                        name = artist.title,
                                        channelId = artist.channelId,
                                        thumbnailUrl = artist.thumbnail,
                                    ),
                                )
                            }
                        }
                    ),
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.do_not_recommend)) },
                        description = { Text(text = stringResource(R.string.do_not_recommend_artist_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.thumb_down),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            coroutineScope.launch {
                                iad1tya.echo.music.dislike.DislikeStoreEntryPoint.get(context).dislikeArtist(artist.id)
                            }
                            onDismiss()
                        }
                    ),
                    // Biblioteca ▸ Fijados (owner 2026-10-20).
                    artistPinMenuItem(
                        id = artist.id,
                        name = artist.title,
                        thumbnailUrl = artist.thumbnail,
                        onDismiss = onDismiss,
                    ),
                )
            )
        }
    }
}
