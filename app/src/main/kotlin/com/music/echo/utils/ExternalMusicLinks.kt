package iad1tya.echo.music.utils

import android.content.Context
import android.net.Uri
import iad1tya.echo.music.spotify.Spotify
import iad1tya.echo.music.spotify.models.SpotifySimpleAlbum
import iad1tya.echo.music.spotify.models.SpotifySimpleArtist
import iad1tya.echo.music.spotify.models.SpotifyTrack
import iad1tya.echo.music.spotifyimport.SpotifyImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Music links from other platforms (Spotify, Apple Music, Deezer, Tidal, SoundCloud, Amazon Music,
 * song.link) turned into something Aura can play from YouTube Music.
 *
 * Only free, key-less sources are used: the Spotify web client the app already has (anonymous token for
 * public albums/playlists), the public Deezer API, the iTunes lookup API, and the page's Open Graph tags.
 * The resolved tracks are then matched on YouTube Music by [SpotifyImportRepository.matchExternalTracks].
 */
object ExternalMusicLinks {

    enum class Kind { TRACK, ALBUM, PLAYLIST }

    sealed interface Resolved {
        val kind: Kind

        /** Exact tracks (title/artist/duration) to match on YouTube Music. */
        data class Tracks(
            override val kind: Kind,
            val title: String,
            val artist: String,
            val tracks: List<SpotifyTrack>,
        ) : Resolved

        /** Only a name is known (page title): search YouTube Music for it. */
        data class Query(override val kind: Kind, val text: String) : Resolved
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val SHORT_HOSTS = setOf("spotify.link", "spotify.app.link", "link.deezer.com", "deezer.page.link", "on.soundcloud.com")

    /** True for a link this resolver understands (checked before the YouTube routing). */
    fun isExternalMusicLink(uri: Uri): Boolean {
        if (uri.scheme == "spotify") return true
        val host = uri.host?.lowercase() ?: return false
        return host == "open.spotify.com" || host in SHORT_HOSTS ||
            host == "music.apple.com" || host == "geo.music.apple.com" ||
            host == "deezer.com" || host == "www.deezer.com" ||
            host == "tidal.com" || host == "listen.tidal.com" ||
            host == "soundcloud.com" || host == "m.soundcloud.com" ||
            host == "music.amazon.com" ||
            host == "song.link" || host == "album.link" || host == "odesli.co"
    }

    /** `song.link/y/<videoId>` — the link Aura itself shares. It is a YouTube id: play it directly. */
    fun songLinkVideoId(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        if (host != "song.link" && host != "odesli.co") return null
        val segments = uri.pathSegments
        if (segments.size < 2 || segments[0] != "y") return null
        return segments[1].takeIf { id -> id.isNotBlank() && id.all { it.isLetterOrDigit() || it == '-' || it == '_' } }
    }

    suspend fun resolve(context: Context, input: Uri): Resolved? = withContext(Dispatchers.IO) {
        try {
            val uri = expandShortLink(input)
            val host = uri.host?.lowercase().orEmpty()
            when {
                uri.scheme == "spotify" -> {
                    val parts = uri.schemeSpecificPart.split(":")
                    spotify(context, Uri.parse("https://open.spotify.com/${parts.getOrNull(0)}/${parts.getOrNull(1)}"))
                }
                host == "open.spotify.com" -> spotify(context, uri)
                host.endsWith("deezer.com") -> deezer(uri)
                host.endsWith("apple.com") -> apple(uri)
                host.endsWith("tidal.com") -> tidal(uri)
                else -> pageQuery(uri, kindFromPath(uri))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            null
        }
    }

    // ── Platforms ────────────────────────────────────────────────────────────────────────────────

    private suspend fun spotify(context: Context, uri: Uri): Resolved? {
        val segments = uri.pathSegments.filterNot { it.startsWith("intl-") }
        val type = segments.getOrNull(0)
        val id = segments.getOrNull(1) ?: return null
        val repository = SpotifyImportRepository.get(context)
        return when (type) {
            "track" -> {
                val html = fetch(uri.toString())
                val title = meta(html, "og:title") ?: return null
                // og:description: "Artist · Album · Song · 1987"
                val artist = meta(html, "og:description")?.substringBefore(" · ").orEmpty()
                Resolved.Tracks(Kind.TRACK, title, artist, listOf(track(id, title, artist)))
            }
            "album" -> {
                repository.ensureSpotifyReadToken()
                val album = Spotify.album(id).getOrNull()
                if (album == null || album.tracks?.items.isNullOrEmpty()) {
                    pageQuery(uri, Kind.ALBUM)
                } else {
                    Resolved.Tracks(
                        Kind.ALBUM,
                        album.name,
                        album.artists.joinToString(" ") { it.name },
                        album.tracks!!.items.map { if (it.album == null) it.copy(album = SpotifySimpleAlbum(name = album.name)) else it },
                    )
                }
            }
            "playlist" -> {
                repository.ensureSpotifyReadToken()
                val name = Spotify.playlist(id).getOrNull()?.name
                val tracks = Spotify.playlistTracks(id, limit = 100).getOrNull()?.items.orEmpty().mapNotNull { it.track }
                if (tracks.isEmpty()) pageQuery(uri, Kind.PLAYLIST) else Resolved.Tracks(Kind.PLAYLIST, name.orEmpty(), "", tracks)
            }
            else -> pageQuery(uri, kindFromPath(uri))
        }
    }

    private fun deezer(uri: Uri): Resolved? {
        val segments = uri.pathSegments.filterNot { it.length == 2 } // language prefix: /es/track/…
        val type = segments.getOrNull(0)
        val id = segments.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: return pageQuery(uri, kindFromPath(uri))
        if (type !in setOf("track", "album", "playlist")) return pageQuery(uri, kindFromPath(uri))
        val root = json.parseToJsonElement(fetch("https://api.deezer.com/$type/$id")).jsonObject
        if (root["error"] != null) return pageQuery(uri, kindFromPath(uri))
        fun JsonObject.toTrack(albumName: String?) = track(
            id = "dz_${str("id")}",
            title = str("title").orEmpty(),
            artist = (this["artist"] as? JsonObject)?.str("name").orEmpty(),
            durationMs = ((this["duration"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000).toInt(),
            album = albumName ?: (this["album"] as? JsonObject)?.str("title"),
        )
        return when (type) {
            "track" -> {
                val t = root.toTrack(null)
                Resolved.Tracks(Kind.TRACK, t.name, t.artists.firstOrNull()?.name.orEmpty(), listOf(t))
            }
            else -> {
                val title = root.str("title").orEmpty()
                val data = (root["tracks"] as? JsonObject)?.get("data") as? JsonArray
                val tracks = data.orEmpty().mapNotNull { (it as? JsonObject)?.toTrack(if (type == "album") title else null) }
                val artist = (root["artist"] as? JsonObject)?.str("name").orEmpty()
                Resolved.Tracks(if (type == "album") Kind.ALBUM else Kind.PLAYLIST, title, artist, tracks)
            }
        }
    }

    private fun apple(uri: Uri): Resolved? {
        val segments = uri.pathSegments
        val country = segments.firstOrNull()?.takeIf { it.length == 2 } ?: "us"
        val type = segments.firstOrNull { it in setOf("album", "song", "playlist", "music-video") }
        val trackId = uri.getQueryParameter("i")
        val lastId = segments.lastOrNull()
        fun lookup(id: String, entity: String?): List<JsonObject> {
            val url = "https://itunes.apple.com/lookup?id=$id&country=$country" + (entity?.let { "&entity=$it" } ?: "")
            val results = json.parseToJsonElement(fetch(url)).jsonObject["results"]?.jsonArray.orEmpty()
            return results.mapNotNull { it as? JsonObject }
        }
        fun JsonObject.toTrack() = track(
            id = "am_${str("trackId")}",
            title = str("trackName").orEmpty(),
            artist = str("artistName").orEmpty(),
            durationMs = (this["trackTimeMillis"]?.jsonPrimitive?.longOrNull ?: 0L).toInt(),
            album = str("collectionName"),
        )
        return when {
            trackId != null || type == "song" -> {
                val song = lookup(trackId ?: lastId ?: return null, null).firstOrNull { it.str("wrapperType") == "track" }
                    ?: return pageQuery(uri, Kind.TRACK)
                val t = song.toTrack()
                Resolved.Tracks(Kind.TRACK, t.name, t.artists.firstOrNull()?.name.orEmpty(), listOf(t))
            }
            type == "album" && lastId != null -> {
                val results = lookup(lastId, "song")
                val collection = results.firstOrNull { it.str("wrapperType") == "collection" }
                val tracks = results.filter { it.str("wrapperType") == "track" }
                    .sortedWith(compareBy({ it["discNumber"]?.jsonPrimitive?.intOrNull ?: 1 }, { it["trackNumber"]?.jsonPrimitive?.intOrNull ?: 0 }))
                    .map { it.toTrack() }
                if (collection == null) {
                    pageQuery(uri, Kind.ALBUM)
                } else {
                    Resolved.Tracks(Kind.ALBUM, collection.str("collectionName").orEmpty(), collection.str("artistName").orEmpty(), tracks)
                }
            }
            else -> pageQuery(uri, kindFromPath(uri))
        }
    }

    private fun tidal(uri: Uri): Resolved? {
        val kind = kindFromPath(uri)
        val title = meta(fetch(uri.toString()), "og:title") ?: return null
        if (kind != Kind.TRACK) return Resolved.Query(kind, title.replace(" - ", " "))
        // og:title: "Artist - Title"
        val artist = title.substringBefore(" - ", "")
        val name = title.substringAfter(" - ")
        return Resolved.Tracks(Kind.TRACK, name, artist, listOf(track("td_${uri.lastPathSegment}", name, artist)))
    }

    /** Any other page: search YouTube Music for its Open Graph title. */
    private fun pageQuery(uri: Uri, kind: Kind): Resolved? {
        val html = fetch(uri.toString())
        val raw = meta(html, "og:title") ?: Regex("<title>([^<]+)</title>").find(html)?.groupValues?.get(1) ?: return null
        val text = raw
            .replace(Regex("\\s*[|·–-]\\s*(SoundCloud|Amazon Music|Apple Music|Spotify|TIDAL|Deezer|Songlink|Odesli).*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(Stream|Listen to|Escucha)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(by|de)\\s+", RegexOption.IGNORE_CASE), " ")
            .trim()
        return text.takeIf { it.isNotBlank() }?.let { Resolved.Query(kind, it) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    private fun kindFromPath(uri: Uri): Kind {
        val path = uri.path.orEmpty().lowercase()
        return when {
            "/album" in path -> Kind.ALBUM
            "/playlist" in path || "/sets/" in path -> Kind.PLAYLIST
            else -> Kind.TRACK
        }
    }

    private fun track(id: String, title: String, artist: String, durationMs: Int = 0, album: String? = null) = SpotifyTrack(
        id = id,
        name = title,
        artists = if (artist.isBlank()) emptyList() else listOf(SpotifySimpleArtist(name = artist)),
        album = album?.takeIf { it.isNotBlank() }?.let { SpotifySimpleAlbum(name = it) },
        durationMs = durationMs,
    )

    /** Follows a short link (spotify.link, deezer.page.link…) to the real platform URL. */
    private fun expandShortLink(uri: Uri): Uri {
        val host = uri.host?.lowercase() ?: return uri
        if (host !in SHORT_HOSTS) return uri
        val connection = open(uri.toString())
        return try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val finalUrl = Uri.parse(connection.url.toString())
            if (finalUrl.host?.lowercase() !in SHORT_HOSTS) {
                finalUrl
            } else {
                // Some short links land on an HTML page that carries the target URL instead of redirecting.
                Regex("https://(open\\.spotify\\.com|www\\.deezer\\.com|soundcloud\\.com)/[^\"'<>\\s]+").find(body)
                    ?.value?.let(Uri::parse) ?: uri
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetch(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 12_000
        readTimeout = 12_000
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36")
        setRequestProperty("Accept-Language", "es,en;q=0.8")
    }

    private fun meta(html: String, property: String): String? =
        Regex("<meta[^>]+(?:property|name)=\"${Regex.escape(property)}\"[^>]+content=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#x27;", "'")?.replace("&#39;", "'")
            ?.trim()?.takeIf { it.isNotBlank() }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
