package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.applicationScope
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.dislike.DislikeStoreEntryPoint
import iad1tya.echo.music.dislike.HiddenItems
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Registry row 811 (owner H6a, 2026-11-24): «Ocultos» — everything hidden with «No me gusta», «No recomendar» and «Menos
 * de esto», with an undo per row. Names come from the local database when it has the item, the id otherwise; nothing
 * here is logged (AGENTS.md rule 4). The list follows the store's flow, so an undo — here, from the player or synced
 * from another device — removes its row by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenItemsScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val store = remember { DislikeStoreEntryPoint.get(context) }
    val disliked by store.disliked.collectAsState(initial = null)
    val rows = remember(disliked) { disliked?.let(HiddenItems::rows).orEmpty() }
    // Looked up once per id and kept across emissions: an undo must not re-query every other row.
    val names by produceState(emptyMap<HiddenItems.Row, String>(), rows) {
        val missing = rows.filter { it !in value }
        if (missing.isNotEmpty()) {
            val found = withContext(Dispatchers.IO) { missing.mapNotNull { row -> localName(database, row)?.let { row to it } } }
            value = value + found
        }
    }
    val notInLibrary = stringResource(R.string.hidden_items_not_in_library)
    val undoLabel = stringResource(R.string.hidden_items_undo)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (disliked != null && rows.isEmpty()) {
            Text(
                text = stringResource(R.string.hidden_items_empty),
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        GROUPS.forEach { (kind, titleRes) ->
            val ofKind = rows.filter { it.kind == kind }
            if (ofKind.isEmpty()) return@forEach
            Material3SettingsGroup(
                title = stringResource(titleRes),
                items = ofKind.map { row ->
                    val name = names[row]
                    val idOnlyNote: (@Composable () -> Unit)? = if (name == null) {
                        { Text(text = notInLibrary) }
                    } else {
                        null
                    }
                    Material3SettingsItem(
                        icon = painterResource(iconFor(kind)),
                        title = { Text(text = name ?: row.id) },
                        description = idOnlyNote,
                        trailingContent = {
                            TextButton(onClick = {
                                // Process scope: the edit must finish even if the user leaves the screen right away.
                                context.applicationScope.launch {
                                    HiddenItems.undo(store, row)
                                    if (HiddenItems.clearsAccountDislike(row)) {
                                        // Same as the player's own undo (MusicService.toggleDislikeCurrentSong).
                                        iad1tya.echo.music.utils.AccountPush.submit(
                                            iad1tya.echo.music.utils.AccountPushPolicy.Op.SONG_UNLIKE,
                                            row.id,
                                        )
                                    }
                                }
                            }) { Text(text = undoLabel) }
                        },
                    )
                },
            )
            Spacer(modifier = Modifier.height(27.dp))
        }
    }

    iad1tya.echo.music.ui.component.AuraTopAppBar(
        title = { Text(stringResource(R.string.hidden_items_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = null,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}

private val GROUPS = listOf(
    HiddenItems.Kind.SONG to R.string.hidden_group_songs,
    HiddenItems.Kind.ARTIST to R.string.hidden_group_artists,
    HiddenItems.Kind.ALBUM to R.string.hidden_group_albums,
    HiddenItems.Kind.PLAYLIST to R.string.hidden_group_playlists,
    HiddenItems.Kind.SOFT_SONG to R.string.hidden_group_soft_songs,
    HiddenItems.Kind.SOFT_ARTIST to R.string.hidden_group_soft_artists,
)

private fun iconFor(kind: HiddenItems.Kind): Int = when (kind) {
    HiddenItems.Kind.SONG, HiddenItems.Kind.SOFT_SONG -> R.drawable.music_note
    HiddenItems.Kind.ARTIST, HiddenItems.Kind.SOFT_ARTIST -> R.drawable.artist
    HiddenItems.Kind.ALBUM -> R.drawable.album
    HiddenItems.Kind.PLAYLIST -> R.drawable.queue_music
}

/** The name the local database has for [row], or null (then the screen shows the id). Playlists are stored by browse id. */
private suspend fun localName(database: MusicDatabase, row: HiddenItems.Row): String? = runCatching {
    when (row.kind) {
        HiddenItems.Kind.SONG, HiddenItems.Kind.SOFT_SONG -> database.song(row.id).firstOrNull()?.song?.title
        // A «Menos de esto» artist key is the artist id, or its lower-cased name when the song had no id (SoftDislikeRank).
        HiddenItems.Kind.ARTIST, HiddenItems.Kind.SOFT_ARTIST -> database.artist(row.id).firstOrNull()?.artist?.name
        HiddenItems.Kind.ALBUM -> database.album(row.id).firstOrNull()?.album?.title
        HiddenItems.Kind.PLAYLIST -> database.playlistByBrowseId(row.id).firstOrNull()?.playlist?.name
    }
}.getOrNull()?.takeIf { it.isNotBlank() }
