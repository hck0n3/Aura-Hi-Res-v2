package iad1tya.echo.music.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.db.entities.LyricsEntity
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.ui.component.LocalBottomSheetPageState
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.Lyrics
import iad1tya.echo.music.ui.menu.LyricsMenu
import iad1tya.echo.music.ui.utils.ShowOffsetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineLyricsView(
    mediaMetadata: MediaMetadata?,
    showLyrics: Boolean,
    // Nullable on purpose: when it returns null, the Lyrics view falls through to the LIVE player position
    // (its own 8 ms ticker) so the word-by-word highlight sweeps smoothly. The outer player position state is
    // throttled to 500 ms (to avoid a recomposition storm); feeding that to the lyrics made them jump in
    // 500 ms steps. We only provide a value while scrubbing (preview) or casting (the active stream).
    positionProvider: () -> Long?
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val rawCurrentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    // Audible song during crossfade (outgoing pin), not the already-published incoming metadata.
    val lyricsMeta by playerConnection.lyricsMediaMetadata.collectAsState()
    // Wrong-song guard: ignore a lyrics row that belongs to a different song (flatMapLatest lag).
    val currentLyrics = rawCurrentLyrics?.takeIf { it.id == lyricsMeta?.id }
    val lyrics = remember(currentLyrics) { currentLyrics?.lyrics?.trim() }
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    LaunchedEffect(lyricsMeta?.id, currentLyrics) {
        val meta = lyricsMeta
        if (meta != null && currentLyrics == null) {
            delay(500)
            withContext(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        iad1tya.echo.music.di.LyricsHelperEntryPoint::class.java
                    )
                    val lyricsHelper = entryPoint.lyricsHelper()
                    val fetchedLyricsWithProvider = lyricsHelper.getLyrics(meta)
                    database.query {
                        upsert(LyricsEntity(meta.id, fetchedLyricsWithProvider.lyrics, fetchedLyricsWithProvider.provider))
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Lyrics fetch/save failed")
                }
            }
        }
    }

    Box (
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            lyrics == null -> {
                ContainedLoadingIndicator()
            }
            lyrics == LyricsEntity.LYRICS_NOT_FOUND -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    // P22A: the empty state offers its next step — the lyrics menu's own «Buscar letra» dialog,
                    // opened directly. The song is read only while that menu is open (no extra observer here).
                    val searchMeta = lyricsMeta
                    if (searchMeta != null) {
                        TextButton(
                            onClick = {
                                ActionLog.tap(ActionId.LYRINLINE_SEARCH)
                                menuState.show {
                                    val song by playerConnection.currentSong.collectAsState(initial = null)
                                    LyricsMenu(
                                        lyricsProvider = { currentLyrics },
                                        songProvider = { song?.song },
                                        mediaMetadataProvider = { searchMeta },
                                        onDismiss = menuState::dismiss,
                                        onShowOffsetDialog = {
                                            bottomSheetPageState.show {
                                                ShowOffsetDialog(songProvider = { song?.song })
                                            }
                                        },
                                        startWithSearch = true,
                                    )
                                }
                            },
                        ) {
                            Text(text = stringResource(R.string.search_lyrics))
                        }
                    }
                }
            }
            else -> {
                val lyricsContent: @Composable () -> Unit = {
                    Lyrics(
                        sliderPositionProvider = positionProvider,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        showLyrics = showLyrics
                    )
                }
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                ) {
                    lyricsContent()
                }
            }
        }
    }
}

