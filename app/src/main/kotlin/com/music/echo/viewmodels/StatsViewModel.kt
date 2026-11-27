

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.statToPeriod
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.stats.StatsDedupe
import iad1tya.echo.music.ui.screens.OptionStats
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val selectedOption = MutableStateFlow(OptionStats.CONTINUOUS)
    val indexChips = MutableStateFlow(0)

    val totalPlayTime =
        combine(
            selectedOption,
            indexChips,
        ) { selection, t -> Pair(selection, t) }
            .flatMapLatest { (selection, t) ->
                val fromTimeStamp = statToPeriod(selection, t)
                val toTimeStamp = if (selection == OptionStats.CONTINUOUS || t == 0) {
                    LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
                } else {
                    statToPeriod(selection, t - 1)
                }
                database.getTotalPlayTimeInRange(fromTimeStamp, toTimeStamp)
            }.map { it ?: 0L }
            .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val allTimePlayTime = database.getTotalPlayTimeInRange(0, Long.MAX_VALUE)
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val uniqueSongsCount =
        combine(
            selectedOption,
            indexChips,
        ) { selection, t -> Pair(selection, t) }
            .flatMapLatest { (selection, t) ->
                val fromTimeStamp = statToPeriod(selection, t)
                val toTimeStamp = if (selection == OptionStats.CONTINUOUS || t == 0) {
                    LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
                } else {
                    statToPeriod(selection, t - 1)
                }
                database.getUniqueSongCountInRange(fromTimeStamp, toTimeStamp)
            }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val uniqueArtistsCount =
        combine(
            selectedOption,
            indexChips,
        ) { selection, t -> Pair(selection, t) }
            .flatMapLatest { (selection, t) ->
                val fromTimeStamp = statToPeriod(selection, t)
                val toTimeStamp = if (selection == OptionStats.CONTINUOUS || t == 0) {
                    LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
                } else {
                    statToPeriod(selection, t - 1)
                }
                database.getUniqueArtistCountInRange(fromTimeStamp, toTimeStamp)
            }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val uniqueAlbumsCount =
        combine(
            selectedOption,
            indexChips,
        ) { selection, t -> Pair(selection, t) }
            .flatMapLatest { (selection, t) ->
                val fromTimeStamp = statToPeriod(selection, t)
                val toTimeStamp = if (selection == OptionStats.CONTINUOUS || t == 0) {
                    LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
                } else {
                    statToPeriod(selection, t - 1)
                }
                database.getUniqueAlbumCountInRange(fromTimeStamp, toTimeStamp)
            }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val rawMostPlayedSongsStats =
        combine(
            selectedOption,
            indexChips,
            context.dataStore.data.map { it[HideVideoSongsKey] ?: false }.distinctUntilChanged()
        ) { first, second, third -> Triple(first, second, third) }
            .flatMapLatest { (selection, t, hideVideoSongs) ->
                database
                    .mostPlayedSongsStats(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            LocalDateTime
                                .now()
                                .toInstant(
                                    ZoneOffset.UTC,
                                ).toEpochMilli()
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                    ).map { songs ->
                        if (hideVideoSongs) songs.filter { !it.isVideo } else songs
                    }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val rawMostPlayedSongs =
        combine(
            selectedOption,
            indexChips,
            context.dataStore.data.map { it[HideVideoSongsKey] ?: false }.distinctUntilChanged()
        ) { first, second, third -> Triple(first, second, third) }
            .flatMapLatest { (selection, t, hideVideoSongs) ->
                database
                    .mostPlayedSongs(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            LocalDateTime
                                .now()
                                .toInstant(
                                    ZoneOffset.UTC,
                                ).toEpochMilli()
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                    ).map { songs ->
                        if (hideVideoSongs) songs.filter { !it.song.isVideo } else songs
                    }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val rawMostPlayedArtists =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedArtists(
                        statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            LocalDateTime
                                .now()
                                .toInstant(
                                    ZoneOffset.UTC,
                                ).toEpochMilli()
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                    )
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val rawMostPlayedAlbums =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database.mostPlayedAlbums(
                    statToPeriod(selection, t),
                    limit = -1,
                    toTimeStamp =
                    if (selection == OptionStats.CONTINUOUS || t == 0) {
                        LocalDateTime
                            .now()
                            .toInstant(
                                ZoneOffset.UTC,
                            ).toEpochMilli()
                    } else {
                        statToPeriod(selection, t - 1)
                    },
                )
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Registry row 909: a song heard from the phone, the cloud and online is ONE song here (the copies' plays and time
    // add up), and so is an artist or an album with one row per source. The shelves used to list local artists
    // nowhere (only `UC…` ids passed) and the same song twice. The two song lists stay in the same order, one copy
    // per song, so the screen pairs them by id.
    private val mergedSongs =
        combine(rawMostPlayedSongsStats, rawMostPlayedSongs) { stats, songs ->
            val groupOf = StatsDedupe.songGroups(
                songs.map { StatsDedupe.SongKey(it.id, it.song.title, it.artists.map { a -> a.name }, it.song.duration) },
            )
            val merged = StatsDedupe.mergeGroups(
                items = stats,
                keyOf = { groupOf[it.id] ?: it.id },
                weight = { it.timeListened ?: 0L },
                merge = { into, other ->
                    into.copy(
                        songCountListened = into.songCountListened + other.songCountListened,
                        timeListened = (into.timeListened ?: 0L) + (other.timeListened ?: 0L),
                    )
                },
            )
            val byId = songs.associateBy { it.id }
            merged to merged.mapNotNull { byId[it.id] }
        }

    val mostPlayedSongsStats = mergedSongs.map { it.first }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedSongs = mergedSongs.map { it.second }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedArtists = rawMostPlayedArtists.map { artists ->
        StatsDedupe.mergeGroups(
            items = artists,
            keyOf = { StatsDedupe.artistKey(it.id, it.artist.name) },
            weight = { (it.timeListened ?: 0).toLong() },
            // The YouTube row opens the artist page with its catalogue and photo.
            preferred = { it.artist.isYouTubeArtist },
            merge = { into, other ->
                into.copy(
                    songCount = into.songCount + other.songCount,
                    timeListened = (into.timeListened ?: 0) + (other.timeListened ?: 0),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedAlbums = rawMostPlayedAlbums.map { albums ->
        StatsDedupe.mergeGroups(
            items = albums,
            keyOf = { StatsDedupe.albumKey(it.id, it.album.title, it.artists.firstOrNull()?.name) },
            weight = { it.timeListened ?: 0L },
            merge = { into, other ->
                into.copy(
                    songCountListened = (into.songCountListened ?: 0) + (other.songCountListened ?: 0),
                    timeListened = (into.timeListened ?: 0L) + (other.timeListened ?: 0L),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val firstEvent =
        database
            .firstEvent()
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    init {
        viewModelScope.launch {
            mostPlayedArtists.collect { artists ->
                artists
                    .map { it.artist }
                    // A phone or cloud artist has no YouTube page to read.
                    .filter { it.isYouTubeArtist }
                    .filter {
                        it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
        viewModelScope.launch {
            mostPlayedAlbums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
    }
}
