package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.db.OfflineCollection
import iad1tya.echo.music.db.entities.Song
import java.text.Normalizer

/**
 * The search of the offline mode (owner 2026-10-20: *«el modo sin conexión… me gustaría que tenga su propio buscador, pero
 * que solo busque de las que están disponibles en el modo sin conexión, que no busque de otro lado»*).
 *
 * This file is PURE: it takes lists that are already in memory and returns lists. It owns no database, no network, no
 * view model — that is the guarantee that this search cannot reach YouTube. WHAT is searchable is decided by the caller
 * (`rememberOfflineLibrary`), which hands in exactly the songs the offline body lists (downloads, complete listen-cache
 * copies, exported MP3s and videos) and the downloaded albums / playlists; nothing else can ever show up here.
 */

/** Lower-case, accent-free, single-spaced form of [text] used on BOTH sides of a comparison ("Niño" == "nino"). */
internal fun offlineSearchFold(text: String): String {
    if (text.isEmpty()) return ""
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val out = StringBuilder(decomposed.length)
    for (ch in decomposed) {
        when {
            // The combining marks NFD split off (acute, tilde, diaeresis, cedilla…).
            ch.category == CharCategory.NON_SPACING_MARK -> Unit
            // Letters that do not decompose but that people type without their stroke.
            ch == 'ł' || ch == 'Ł' -> out.append('l')
            ch == 'ø' || ch == 'Ø' -> out.append('o')
            ch == 'đ' || ch == 'Đ' -> out.append('d')
            ch == 'ı' -> out.append('i')
            ch == 'ß' -> out.append("ss")
            ch == 'æ' || ch == 'Æ' -> out.append("ae")
            ch == 'œ' || ch == 'Œ' -> out.append("oe")
            ch.isWhitespace() -> out.append(' ')
            else -> out.append(ch.lowercaseChar())
        }
    }
    return out.toString().trim().replace(MULTI_SPACE, " ")
}

private val MULTI_SPACE = Regex(" {2,}")

/** An artist that has at least one song playable offline. */
internal data class OfflineArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val songCount: Int,
)

internal data class OfflineSearchResult(
    val songs: List<Song>,
    val albums: List<OfflineCollection>,
    val playlists: List<OfflineCollection>,
    val artists: List<OfflineArtist>,
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && playlists.isEmpty() && artists.isEmpty()

    companion object {
        val Empty = OfflineSearchResult(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/**
 * Searchable view of what plays with no network. Folded text is computed once, lazily (the first keystroke pays for it,
 * on whichever thread calls [search] — the UI calls it off the main thread), not on every recomposition.
 *
 * A query is split on spaces and EVERY word must appear somewhere in title / artists / album ("cumbia gil" finds a
 * «Cumbia» by an artist called Gil). Results are ordered by how well the title matches: starts with the query, contains
 * it, has all the words in the title, and only then matches through artist or album.
 */
internal class OfflineSearchIndex(
    songs: List<Song>,
    private val collectionsAlbums: List<OfflineCollection>,
    private val collectionsPlaylists: List<OfflineCollection>,
) {
    private class SongEntry(val song: Song, val title: String, val artists: String, val album: String)

    private val entries: List<SongEntry> by lazy {
        songs.distinctBy { it.id }.map { song ->
            SongEntry(
                song = song,
                title = offlineSearchFold(song.song.title),
                artists = offlineSearchFold(song.artists.joinToString(" ") { it.name }),
                album = offlineSearchFold(song.album?.title.orEmpty()),
            )
        }
    }

    /** Every artist with an offline song, most songs first. */
    val artists: List<OfflineArtist> by lazy {
        val counts = LinkedHashMap<String, OfflineArtist>()
        entries.forEach { entry ->
            entry.song.artists.forEach { artist ->
                if (artist.name.isBlank()) return@forEach
                val seen = counts[artist.id]
                counts[artist.id] = if (seen == null) {
                    OfflineArtist(artist.id, artist.name, artist.thumbnailUrl, 1)
                } else {
                    seen.copy(songCount = seen.songCount + 1)
                }
            }
        }
        counts.values.sortedWith(compareByDescending<OfflineArtist> { it.songCount }.thenBy { it.name })
    }

    private val artistFolded: List<String> by lazy { artists.map { offlineSearchFold(it.name) } }

    /** album id -> folded names of the artists of its offline songs, so «Mi álbum» is found by its artist too. */
    private val albumArtists: Map<String, String> by lazy {
        val byAlbum = HashMap<String, MutableSet<String>>()
        entries.forEach { entry ->
            val albumId = entry.song.album?.id ?: return@forEach
            byAlbum.getOrPut(albumId) { LinkedHashSet() }.add(entry.artists)
        }
        byAlbum.mapValues { it.value.joinToString(" ") }
    }

    /** The offline songs of one artist, in the order the offline body lists them. */
    fun songsOf(artistId: String): List<Song> =
        entries.filter { entry -> entry.song.artists.any { it.id == artistId } }.map { it.song }

    /** The offline songs of one album (downloaded, cached or exported), in the order the offline body lists them. */
    fun songsOfAlbum(albumId: String): List<Song> =
        entries.filter { it.song.album?.id == albumId }.map { it.song }

    fun search(query: String): OfflineSearchResult {
        val folded = offlineSearchFold(query)
        if (folded.isEmpty()) return OfflineSearchResult.Empty
        val words = folded.split(' ')

        val songs = entries
            .mapNotNull { entry ->
                val haystack = "${entry.title} ${entry.artists} ${entry.album}"
                if (!words.all { it in haystack }) null else rank(folded, words, entry.title) to entry.song
            }
            .sortedBy { it.first }
            .map { it.second }

        fun collections(list: List<OfflineCollection>, extra: (OfflineCollection) -> String) = list
            .mapNotNull { collection ->
                val title = offlineSearchFold(collection.title)
                val haystack = "$title ${extra(collection)}"
                if (!words.all { it in haystack }) null else rank(folded, words, title) to collection
            }
            .sortedBy { it.first }
            .map { it.second }

        val albums = collections(collectionsAlbums) { albumArtists[it.id].orEmpty() }
        val playlists = collections(collectionsPlaylists) { "" }

        val artistHits = artists.indices
            .filter { index -> words.all { it in artistFolded[index] } }
            .map { index -> rank(folded, words, artistFolded[index]) to artists[index] }
            .sortedBy { it.first }
            .map { it.second }

        return OfflineSearchResult(songs, albums, playlists, artistHits)
    }

    private fun rank(query: String, words: List<String>, title: String): Int = when {
        title.startsWith(query) -> 0
        query in title -> 1
        words.all { it in title } -> 2
        else -> 3
    }
}
