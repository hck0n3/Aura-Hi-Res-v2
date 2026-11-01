package iad1tya.echo.music.ui.screens.artist

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.YTItem
import java.text.Normalizer

/**
 * Row 612 (owner 2026-10-31: «las discografías de los artistas no deben tener álbumes repetidos»). YouTube lists the SAME release more than once
 * under different ids (clean and explicit editions, a re-issue under a regional catalogue) and the old `distinctBy { it.id }` only removes
 * the same id. Two albums are the same release when their title (accents, case, punctuation and spacing ignored), year and first artist
 * agree. A deluxe edition has another title, so it is a different release and stays. The first one listed is kept, in its place.
 */
object ArtistDiscography {
    private val MARKS = Regex("\\p{Mn}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    internal fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(MARKS, "").replace(NOT_WORD, " ").trim()

    internal fun keyOf(album: AlbumItem): String =
        "album|${fold(album.title)}|${album.year ?: 0}|${fold(album.artists?.firstOrNull()?.name.orEmpty())}"

    /**
     * Row 613 (owner 2026-10-31: «faltan muchas canciones del artista en su página, hay que buscarlas aparte»): the songs a search for the artist
     * brings that the page does not list. Only songs credited to THIS artist (a search for «Luis Miguel» also returns songs ABOUT him and covers
     * by others), and never one the page already has, by id or by title + first artist. Order of [found] is kept; at most [limit].
     */
    fun missingSongs(
        listed: List<com.music.innertube.models.SongItem>,
        found: List<com.music.innertube.models.SongItem>,
        artistName: String,
        limit: Int = 60,
    ): List<com.music.innertube.models.SongItem> {
        val wanted = fold(artistName)
        if (wanted.isEmpty()) return emptyList()
        fun titleKey(s: com.music.innertube.models.SongItem) = fold(s.title) + "|" + fold(s.artists.firstOrNull()?.name.orEmpty())
        val seenIds = listed.mapTo(HashSet()) { it.id }
        val seenTitles = listed.mapTo(HashSet()) { titleKey(it) }
        return found.filter { song ->
            song.artists.any { fold(it.name) == wanted } && seenIds.add(song.id) && seenTitles.add(titleKey(song))
        }.take(limit)
    }

    /**
     * [items] without the repeated releases; every other kind of item (songs, videos, playlists, artists) is only de-duplicated by id, as
     * before. Row 621: the first version added the SAME key twice for those kinds, so the second add always failed and every one of them
     * was dropped — the artist's videos and songs vanished from the artist pages in beta 12.
     */
    fun dedupe(items: List<YTItem>): List<YTItem> {
        val seenIds = HashSet<String>()
        val seenReleases = HashSet<String>()
        return items.filter { item ->
            if (!seenIds.add(item.id)) return@filter false
            if (item is AlbumItem) seenReleases.add(keyOf(item)) else true
        }
    }
}
