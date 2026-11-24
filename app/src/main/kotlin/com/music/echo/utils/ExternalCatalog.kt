package iad1tya.echo.music.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Registry row 846: which source a shared link is read from, and the exact title, artist and duration of a Deezer or Apple Music
 * item as each service's own public, keyless catalog gives them (api.deezer.com; the iTunes Search API `lookup`). Only the id in
 * the link is sent. Pure (no network, no Android) so the parsing is tested on the JVM; [ExternalMusicLinks] does the requests.
 */
internal object ExternalCatalog {

    /** Most tracks a shared album or playlist is read for: what `SpotifyImportRepository.matchExternalTracks` matches. */
    const val MAX_TRACKS = 100

    enum class Source { SPOTIFY, DEEZER, APPLE_MUSIC, TIDAL, PAGE }

    /** Tidal, SoundCloud, Amazon Music and other song.link pages have no keyless official catalog: [Source.PAGE] reads the page title. */
    fun sourceOf(scheme: String?, host: String): Source = when {
        scheme == "spotify" || host == "open.spotify.com" -> Source.SPOTIFY
        host.endsWith("deezer.com") -> Source.DEEZER
        host.endsWith("apple.com") -> Source.APPLE_MUSIC
        host.endsWith("tidal.com") -> Source.TIDAL
        else -> Source.PAGE
    }

    data class Track(val id: String, val title: String, val artist: String, val durationMs: Int, val album: String?)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Deezer ───────────────────────────────────────────────────────────────────────────────────

    fun deezerItemUrl(type: String, id: String) = "https://api.deezer.com/$type/$id"

    fun deezerTracksUrl(type: String, id: String, index: Int) =
        "https://api.deezer.com/$type/$id/tracks?index=$index&limit=$MAX_TRACKS"

    /** The answer's object, or null when Deezer answered with its `error` object (unknown id, quota…). */
    fun deezerObject(body: String): JsonObject? =
        (json.parseToJsonElement(body) as? JsonObject)?.takeIf { it["error"] == null }

    /** A Deezer track object (`title`, `artist.name`, `duration` in seconds, `album.title`); null without a title. */
    fun deezerTrack(obj: JsonObject, albumName: String?): Track? {
        val title = obj.str("title")?.takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = "dz_${obj.str("id").orEmpty()}",
            title = title,
            artist = (obj["artist"] as? JsonObject)?.str("name").orEmpty(),
            durationMs = ((obj["duration"]?.jsonPrimitive?.longOrNull ?: 0L) * 1_000).toInt(),
            album = albumName ?: (obj["album"] as? JsonObject)?.str("title"),
        )
    }

    /**
     * Every track of an album or playlist from its `/tracks` list, page after page (`index`), until the last one (`total`), an
     * empty or error page, or [MAX_TRACKS]. [fetchPage] returns the page object at that index ([deezerObject]).
     */
    fun deezerAllTracks(albumName: String?, fetchPage: (index: Int) -> JsonObject?): List<Track> {
        val tracks = mutableListOf<Track>()
        var index = 0
        while (index < MAX_TRACKS) {
            val page = fetchPage(index) ?: break
            val rows = page["data"] as? JsonArray ?: break
            if (rows.isEmpty()) break
            rows.mapNotNullTo(tracks) { (it as? JsonObject)?.let { row -> deezerTrack(row, albumName) } }
            index += rows.size
            val total = page["total"]?.jsonPrimitive?.intOrNull
            if (total != null && index >= total) break
        }
        return tracks.take(MAX_TRACKS)
    }

    // ── Apple Music (iTunes Search API) ──────────────────────────────────────────────────────────

    /** `lookup` of a song id, or of an album id with its songs (`entity=song`, up to 200). */
    fun itunesLookupUrl(id: String, country: String, withSongs: Boolean) =
        "https://itunes.apple.com/lookup?id=$id&country=$country" + if (withSongs) "&entity=song&limit=200" else ""

    fun itunesResults(body: String): List<JsonObject> =
        ((json.parseToJsonElement(body) as? JsonObject)?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun JsonObject.isTrack() = str("wrapperType") == "track"

    /** An iTunes track result (`trackName`, `artistName`, `trackTimeMillis`, `collectionName`); null without a name. */
    fun itunesTrack(obj: JsonObject): Track? {
        val title = obj.str("trackName")?.takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = "am_${obj.str("trackId").orEmpty()}",
            title = title,
            artist = obj.str("artistName").orEmpty(),
            durationMs = (obj["trackTimeMillis"]?.jsonPrimitive?.longOrNull ?: 0L).toInt(),
            album = obj.str("collectionName"),
        )
    }

    fun itunesSong(results: List<JsonObject>): Track? = results.firstOrNull { it.isTrack() }?.let(::itunesTrack)

    class Album(val title: String, val artist: String, val tracks: List<Track>)

    /** The album (`wrapperType` collection) and its songs in disc and track order; null when the album itself is missing. */
    fun itunesAlbum(results: List<JsonObject>): Album? {
        val collection = results.firstOrNull { it.str("wrapperType") == "collection" } ?: return null
        val tracks = results.filter { it.isTrack() }
            .sortedWith(compareBy({ it["discNumber"]?.jsonPrimitive?.intOrNull ?: 1 }, { it["trackNumber"]?.jsonPrimitive?.intOrNull ?: 0 }))
            .mapNotNull(::itunesTrack)
        return Album(collection.str("collectionName").orEmpty(), collection.str("artistName").orEmpty(), tracks)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
