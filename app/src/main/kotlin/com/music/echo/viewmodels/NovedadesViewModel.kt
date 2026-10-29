package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ChartsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.MyTopFilter
import iad1tya.echo.music.constants.SpotifyAccessTokenKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ReleaseRadarItem
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.UpcomingReleaseEntity
import iad1tya.echo.music.dislike.DislikeStore
import iad1tya.echo.music.extensions.filterExplicit
import iad1tya.echo.music.playlistimport.MusicRequestRanking
import iad1tya.echo.music.reco.AffinityEngine
import iad1tya.echo.music.reco.TasteProfile
import iad1tya.echo.music.releaseradar.ReleaseRadarWorker
import iad1tya.echo.music.spotify.Spotify
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.filterSongsToTasteArtists
import iad1tya.echo.music.utils.filterToSubscribedArtists
import iad1tya.echo.music.utils.filterToTasteArtists
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.SubscribedArtistKeys
import iad1tya.echo.music.utils.tasteArtistKeys
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NovedadesViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val dislikeStore: DislikeStore,
) : ViewModel() {

    // Owner (2026-10-13): Novedades showed artists he does not follow. The radar table is now seeded by
    // ReleaseRadarWorker from FOLLOWED artists only (and wiped once on this update), so the rows are read as is.
    val radarReleases: StateFlow<List<ReleaseRadarItem>> =
        database.releasesByDateDesc()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val upcoming: StateFlow<List<UpcomingReleaseEntity>> =
        database.upcomingReleases()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        refresh()
        ReleaseRadarWorker.refreshIfStale(context)
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                withContext(Dispatchers.IO) { loadFeeds() }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun togglePresave(item: UpcomingReleaseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val next = !item.presaved
            database.setUpcomingPresaved(item.id, next)
        }
    }

    // Owner 2026-10-29: «en Novedades solo quiero lo que tenga que ver con los estrenos de los artistas suscritos o
    // seguidos». The radar (followed artists, see above) and «Próximamente» (the followed artists' announced releases) are
    // the only sources left, so the YouTube new-albums, charts and library calls this screen used to make are gone with the
    // shelves they fed — fewer requests per visit, and nothing here that is not about a followed artist.
    private suspend fun loadFeeds() {
        scanUpcoming()
    }

    private suspend fun scanUpcoming() {
        val token = context.dataStore.data.first()[SpotifyAccessTokenKey].orEmpty()
        if (token.isBlank()) return
        val today = LocalDate.now()
        val existing = database.upcomingReleases().first().associateBy { it.id }
        val semaphore = Semaphore(4)
        val followed = runCatching {
            val page = Spotify.myArtists(limit = 20, offset = 0).getOrThrow()
            page.items
        }.getOrDefault(emptyList())
        if (followed.isEmpty()) return
        val rows = coroutineScope {
            followed.take(12).map { artist ->
                async {
                    semaphore.withPermit {
                        val disco = runCatching { Spotify.artistDiscography(artist.id).getOrThrow() }
                            .getOrDefault(emptyList())
                        disco.mapNotNull { album ->
                            val date = runCatching {
                                album.releaseDate?.take(10)?.let { LocalDate.parse(it) }
                            }.getOrNull() ?: return@mapNotNull null
                            if (!date.isAfter(today)) return@mapNotNull null
                            val id = album.id.ifBlank { "${artist.id}|${album.name}" }
                            val prev = existing[id]
                            UpcomingReleaseEntity(
                                id = id,
                                artistId = artist.id,
                                artistName = artist.name,
                                title = album.name,
                                releaseEpochMs = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                                artworkUri = album.images.firstOrNull()?.url ?: prev?.artworkUri,
                                youtubeBrowseId = prev?.youtubeBrowseId,
                                presaved = prev?.presaved == true,
                            )
                        }
                    }
                }
            }.awaitAll().flatten()
        }
        database.prunePastUpcoming(System.currentTimeMillis())
        if (rows.isNotEmpty()) database.upsertUpcomingReleases(rows)
    }
}
