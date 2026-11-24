package iad1tya.echo.music.utils

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Registry row 846: the exact title, artist and duration a shared Deezer or Apple Music link is matched with. The fixtures follow
 * the fields both public APIs document (Deezer `track` / `/tracks` list; iTunes Search API `lookup`); the sandbox that wrote them
 * could not reach either API, so the live shape is [NO VERIFICADO] there.
 */
class ExternalCatalogTest {

    // ── Routing per host ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `each service is read from its own source`() {
        assertEquals(ExternalCatalog.Source.SPOTIFY, ExternalCatalog.sourceOf("https", "open.spotify.com"))
        assertEquals(ExternalCatalog.Source.SPOTIFY, ExternalCatalog.sourceOf("spotify", ""))
        assertEquals(ExternalCatalog.Source.DEEZER, ExternalCatalog.sourceOf("https", "www.deezer.com"))
        assertEquals(ExternalCatalog.Source.DEEZER, ExternalCatalog.sourceOf("https", "deezer.com"))
        assertEquals(ExternalCatalog.Source.APPLE_MUSIC, ExternalCatalog.sourceOf("https", "music.apple.com"))
        assertEquals(ExternalCatalog.Source.APPLE_MUSIC, ExternalCatalog.sourceOf("https", "geo.music.apple.com"))
        assertEquals(ExternalCatalog.Source.TIDAL, ExternalCatalog.sourceOf("https", "listen.tidal.com"))
        assertEquals(ExternalCatalog.Source.TIDAL, ExternalCatalog.sourceOf("https", "tidal.com"))
    }

    @Test
    fun `services without a keyless catalog read the page title`() {
        for (host in listOf("soundcloud.com", "m.soundcloud.com", "music.amazon.com", "song.link", "album.link", "odesli.co")) {
            assertEquals(host, ExternalCatalog.Source.PAGE, ExternalCatalog.sourceOf("https", host))
        }
    }

    // ── Deezer ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Deezer requests carry only the id from the link`() {
        assertEquals("https://api.deezer.com/track/3135556", ExternalCatalog.deezerItemUrl("track", "3135556"))
        assertEquals(
            "https://api.deezer.com/playlist/908622995/tracks?index=100&limit=100",
            ExternalCatalog.deezerTracksUrl("playlist", "908622995", 100),
        )
    }

    @Test
    fun `a Deezer track gives title, artist, duration and album`() {
        val root = ExternalCatalog.deezerObject(
            """{"id":3135556,"readable":true,"title":"Harder, Better, Faster, Stronger","title_short":"Harder, Better, Faster, Stronger",
               "isrc":"GBDUW0000059","duration":224,"artist":{"id":27,"name":"Daft Punk"},
               "album":{"id":302127,"title":"Discovery"},"type":"track"}""",
        )!!
        assertEquals(
            ExternalCatalog.Track("dz_3135556", "Harder, Better, Faster, Stronger", "Daft Punk", 224_000, "Discovery"),
            ExternalCatalog.deezerTrack(root, null),
        )
    }

    @Test
    fun `a Deezer error answer is not a track`() {
        assertNull(ExternalCatalog.deezerObject("""{"error":{"type":"DataException","message":"no data","code":800}}"""))
    }

    @Test
    fun `a Deezer track without a title is skipped`() {
        assertNull(ExternalCatalog.deezerTrack(obj("""{"id":1,"title":"","duration":10,"artist":{"name":"A"}}"""), null))
    }

    @Test
    fun `a Deezer playlist is read page after page to the last one`() {
        val pages = mapOf(
            0 to """{"data":[${row(1)},${row(2)}],"total":3,"next":"https://api.deezer.com/playlist/9/tracks?index=2"}""",
            2 to """{"data":[${row(3)}],"total":3}""",
        )
        val asked = mutableListOf<Int>()
        val tracks = ExternalCatalog.deezerAllTracks(null) { index ->
            asked += index
            pages[index]?.let(ExternalCatalog::deezerObject)
        }
        assertEquals(listOf(0, 2), asked)
        assertEquals(listOf("dz_1", "dz_2", "dz_3"), tracks.map { it.id })
        assertEquals("Album 1", tracks.first().album)
        assertEquals(181_000, tracks.first().durationMs)
    }

    @Test
    fun `an album's tracks carry the album name`() {
        val tracks = ExternalCatalog.deezerAllTracks("Discovery") { index ->
            if (index == 0) obj("""{"data":[${row(1)}],"total":1}""") else null
        }
        assertEquals("Discovery", tracks.single().album)
    }

    @Test
    fun `paging stops at an empty or error page and never runs past the match limit`() {
        assertEquals(1, ExternalCatalog.deezerAllTracks(null) { i -> if (i == 0) obj("""{"data":[${row(1)}]}""") else obj("""{"data":[]}""") }.size)
        assertEquals(1, ExternalCatalog.deezerAllTracks(null) { i -> if (i == 0) obj("""{"data":[${row(1)}]}""") else null }.size)
        // No `total`, and every page full: the reading stops once MAX_TRACKS rows were asked for.
        var calls = 0
        val tracks = ExternalCatalog.deezerAllTracks(null) { i ->
            calls++
            obj("""{"data":[${(i until i + 40).joinToString(",") { row(it) }}]}""")
        }
        assertEquals(ExternalCatalog.MAX_TRACKS, tracks.size)
        assertEquals(3, calls)
    }

    @Test
    fun `the list embedded in an album or playlist answer is read when its pages give nothing`() {
        val root = obj("""{"id":302127,"title":"Discovery","artist":{"name":"Daft Punk"},"tracks":{"data":[${row(1)},${row(2)}]}}""")
        assertEquals(listOf("dz_1", "dz_2"), ExternalCatalog.deezerEmbeddedTracks(root, "Discovery").map { it.id })
        assertEquals("Discovery", ExternalCatalog.deezerEmbeddedTracks(root, "Discovery").first().album)
        assertEquals(emptyList<ExternalCatalog.Track>(), ExternalCatalog.deezerEmbeddedTracks(obj("""{"id":1,"title":"T"}"""), null))
    }

    // ── Apple Music (iTunes lookup) ──────────────────────────────────────────────────────────────

    @Test
    fun `Apple Music lookups carry only the id and the store country`() {
        assertEquals("https://itunes.apple.com/lookup?id=1440857795&country=mx", ExternalCatalog.itunesLookupUrl("1440857795", "mx", withSongs = false))
        assertEquals(
            "https://itunes.apple.com/lookup?id=1440857781&country=us&entity=song&limit=200",
            ExternalCatalog.itunesLookupUrl("1440857781", "us", withSongs = true),
        )
    }

    @Test
    fun `an Apple Music song gives title, artist, duration and album`() {
        val results = ExternalCatalog.itunesResults(
            """{"resultCount":1,"results":[{"wrapperType":"track","kind":"song","trackId":1440857795,"artistName":"Daft Punk",
               "collectionName":"Discovery","trackName":"One More Time","trackTimeMillis":320357,"discNumber":1,"trackNumber":1}]}""",
        )
        assertEquals(
            ExternalCatalog.Track("am_1440857795", "One More Time", "Daft Punk", 320_357, "Discovery"),
            ExternalCatalog.itunesSong(results),
        )
    }

    @Test
    fun `an empty Apple Music lookup is no song and no album`() {
        val results = ExternalCatalog.itunesResults("""{"resultCount":0,"results":[]}""")
        assertNull(ExternalCatalog.itunesSong(results))
        assertNull(ExternalCatalog.itunesAlbum(results))
    }

    @Test
    fun `an Apple Music album lists its songs in disc and track order`() {
        val results = ExternalCatalog.itunesResults(
            """{"resultCount":4,"results":[
               {"wrapperType":"collection","collectionType":"Album","collectionId":10,"artistName":"Artist","collectionName":"Record"},
               {"wrapperType":"track","kind":"song","trackId":3,"artistName":"Artist","collectionName":"Record","trackName":"C","trackTimeMillis":3000,"discNumber":2,"trackNumber":1},
               {"wrapperType":"track","kind":"song","trackId":2,"artistName":"Artist","collectionName":"Record","trackName":"B","trackTimeMillis":2000,"discNumber":1,"trackNumber":2},
               {"wrapperType":"track","kind":"song","trackId":1,"artistName":"Artist","collectionName":"Record","trackName":"A","trackTimeMillis":1000,"discNumber":1,"trackNumber":1}]}""",
        )
        val album = ExternalCatalog.itunesAlbum(results)!!
        assertEquals("Record", album.title)
        assertEquals("Artist", album.artist)
        assertEquals(listOf("A", "B", "C"), album.tracks.map { it.title })
        assertEquals(listOf(1_000, 2_000, 3_000), album.tracks.map { it.durationMs })
    }

    private fun obj(body: String): JsonObject = ExternalCatalog.deezerObject(body)!!

    private fun row(n: Int) =
        """{"id":$n,"title":"Song $n","duration":181,"artist":{"id":7,"name":"Artist $n"},"album":{"id":5,"title":"Album $n"},"type":"track"}"""
}
