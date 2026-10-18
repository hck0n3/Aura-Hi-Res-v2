package iad1tya.echo.music.playlistimport

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.SongItem
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fila 435 — DE DÓNDE SALE EL AÑO DE UNA CANCIÓN.
 *
 * YouTube Music no da año por canción, pero sí por ÁLBUM (`AlbumItem.year`, la cabecera de la página del álbum). Una
 * canción de búsqueda trae su álbum (`SongItem.album`), así que su año es el de ese álbum — salvo que el álbum sea una
 * recopilación ([MusicRequestEra.trustworthyAlbumYear]), caso en el que no se sabe.
 *
 * Dos usos, los dos con presupuesto de tiempo y de llamadas (regla 7: batería y red):
 *  · [prefetch] — los años de los álbumes de unas canciones ya encontradas (para juzgarlas);
 *  · [songsOfAlbumsIn] — álbumes que YA están en el rango pedido, para que haya de dónde elegir (la búsqueda de
 *    canciones sueltas no filtra por año).
 *
 * Memoria de proceso: un álbum se pregunta una vez. Un fallo de red NO se guarda como "sin año".
 */
object MusicRequestEraYears {

    private const val NO_YEAR = 0
    private const val MAX_CACHE = 600

    private val albumYears = ConcurrentHashMap<String, Int>()
    private val songYears = ConcurrentHashMap<String, Int>()

    private fun trim() {
        if (albumYears.size > MAX_CACHE) albumYears.clear()
        if (songYears.size > MAX_CACHE) songYears.clear()
    }

    fun remember(albumId: String, year: Int?) {
        trim()
        albumYears[albumId] = year ?: NO_YEAR
    }

    /** The year of [song] if it is known and trustworthy; null otherwise (not cached yet, no year, or a compilation). */
    fun yearOf(song: SongItem): Int? {
        songYears[song.id]?.let { return it }
        val album = song.album ?: return null
        val year = albumYears[album.id]?.takeIf { it != NO_YEAR }
        return MusicRequestEra.trustworthyAlbumYear(album.name, year)
    }

    /** Same, for a queue item: its album's id and title. */
    fun yearOf(songId: String?, albumId: String?, albumTitle: String?): Int? {
        songId?.let { songYears[it] }?.let { return it }
        if (albumId == null) return null
        val year = albumYears[albumId]?.takeIf { it != NO_YEAR }
        return MusicRequestEra.trustworthyAlbumYear(albumTitle, year)
    }

    /**
     * Looks up the year of every album in [albumIds] that is not known yet, at most [maxAlbums], [PARALLEL] at a time,
     * and stops at [budgetMs]. Whatever arrives is kept; the rest stays unknown (the caller treats that as "no year").
     */
    suspend fun prefetch(albumIds: Collection<String>, budgetMs: Long, maxAlbums: Int = MAX_ALBUMS) {
        val todo = albumIds.distinct().filter { it !in albumYears }.take(maxAlbums)
        if (todo.isEmpty()) return
        val gate = Semaphore(PARALLEL)
        withTimeoutOrNull(budgetMs) {
            coroutineScope {
                todo.map { id ->
                    async {
                        gate.withPermit {
                            // withSongs = false: the header is enough, and a page of tracks per album is waste.
                            YouTube.album(id, withSongs = false).onSuccess { page -> remember(id, page.album.year) }
                        }
                    }
                }.forEach { it.await() }
            }
        }
    }

    /**
     * Songs of up to [maxAlbums] albums the search returns for [searchText] whose YEAR is inside [range] (the album search
     * lists the year, so this costs one search plus one page per album used). Their years are remembered, so the judge
     * knows them without asking again.
     */
    suspend fun songsOfAlbumsIn(
        searchText: String,
        range: MusicRequestEra.Range,
        maxAlbums: Int = 4,
        songsPerAlbum: Int = 8,
    ): List<SongItem> {
        val albums = YouTube.search(searchText, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
            ?.items?.filterIsInstance<AlbumItem>()
            ?.filter { album -> album.year?.let { it in range } == true }
            ?.take(maxAlbums)
            .orEmpty()
        val out = ArrayList<SongItem>()
        for (album in albums) {
            remember(album.browseId, album.year)
            val page = YouTube.album(album.browseId).getOrNull() ?: continue
            page.songs.take(songsPerAlbum).forEach { song ->
                album.year?.let { y -> songYears[song.id] = y }
                out += song
            }
        }
        return out
    }

    private const val PARALLEL = 5
    private const val MAX_ALBUMS = 24
}
