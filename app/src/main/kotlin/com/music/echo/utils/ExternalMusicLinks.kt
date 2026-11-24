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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import timber.log.Timber

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

    private val SONG_LINK_WRAP_PREFIX = Regex("^https?://(?:www\\.)?(?:song\\.link|album\\.link|odesli\\.co)/(.+)$", RegexOption.IGNORE_CASE)
    private val YOUTUBE_MUSIC_URL = Regex("^https?://(?:music|www|m)\\.youtube\\.com/.+$", RegexOption.IGNORE_CASE)

    /**
     * `song.link/<https://music.youtube.com/…>` — the wrapper [ShareLinks.viaSongLink] puts around an
     * artist, playlist or album. The inner YouTube Music URL is what Aura already routes natively (artist,
     * album, playlist screens), so a share from another Aura opens exactly that item with no network
     * round-trip, instead of going through the scraping resolver. Null for anything else (`/y/<id>`, Odesli
     * page ids, wrapped Spotify/Apple URLs…), which keep their existing routes.
     */
    fun songLinkWrappedYouTubeUrl(url: String): String? {
        val rest = SONG_LINK_WRAP_PREFIX.find(url.trim())?.groupValues?.get(1) ?: return null
        // Some apps percent-encode the whole inner URL when they re-share it.
        val inner = if (rest.startsWith("http%3A", ignoreCase = true) || rest.startsWith("https%3A", ignoreCase = true)) {
            runCatching { java.net.URLDecoder.decode(rest, "UTF-8") }.getOrNull() ?: return null
        } else {
            rest
        }
        return inner.takeIf { YOUTUBE_MUSIC_URL.matches(it) }
    }

    /**
     * Ronda 9 (dueño, reporte repetido de "no encontrado" sin ningún dato para rastrear la causa):
     * traza cada intento de resolver un link externo — plataforma, qué rama corrió y en qué se
     * convirtió (o por qué no). Nunca el título/artista real ni la URL completa (regla #4 de
     * AGENTS.md) — solo el host y counters/booleanos, para que el próximo "no encontrado" real
     * llegue con una línea de app.log que sí dice algo, en vez de investigar a ciegas otra vez.
     */
    private const val TAG = "ExternalMusicLinks"

    suspend fun resolve(context: Context, input: Uri): Resolved? = withContext(Dispatchers.IO) {
        val host = input.host?.lowercase().orEmpty()
        try {
            val uri = expandShortLink(input)
            val expandedHost = uri.host?.lowercase().orEmpty()
            if (expandedHost != host) {
                Timber.tag(TAG).i("EXTERNAL_LINK short-link host=%s expanded_to=%s", host, expandedHost)
            }
            // Registry row 846: Odesli's keyless public API answers 401 PUBLIC_API_ACCESS_DEPRECATED (checked 2026-11-24), so it
            // is no longer asked; each service's own public catalog gives the exact title, artist and duration instead.
            val resolved = when (ExternalCatalog.sourceOf(uri.scheme, expandedHost)) {
                ExternalCatalog.Source.SPOTIFY -> if (uri.scheme == "spotify") {
                    val parts = uri.schemeSpecificPart.split(":")
                    spotify(context, Uri.parse("https://open.spotify.com/${parts.getOrNull(0)}/${parts.getOrNull(1)}"))
                } else {
                    spotify(context, uri)
                }
                ExternalCatalog.Source.DEEZER -> deezer(uri)
                ExternalCatalog.Source.APPLE_MUSIC -> apple(uri)
                ExternalCatalog.Source.TIDAL -> tidal(uri)
                ExternalCatalog.Source.PAGE -> pageQuery(uri, kindFromPath(uri))
            }
            val outcome = when (resolved) {
                null -> "null"
                is Resolved.Tracks -> "tracks(kind=${resolved.kind}, count=${resolved.tracks.size})"
                is Resolved.Query -> "query(kind=${resolved.kind})"
            }
            Timber.tag(TAG).i("EXTERNAL_LINK host=%s -> %s", expandedHost, outcome)
            resolved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "EXTERNAL_LINK host=%s threw %s", host, e.javaClass.simpleName)
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
                // Ronda 9 (dueño): "con Amazon Music sí funciona, con Spotify no". Amazon/SoundCloud got
                // the link-preview UA fix (see pageQuery) because those pages are JS-rendered SPAs that
                // often skip server-side og:* tags for a plain mobile UA. This branch bypasses pageQuery
                // entirely (it parses og:description too, for the artist), so it never got that same fix —
                // and open.spotify.com's track pages are the same kind of JS-heavy SPA, so a plain fetch
                // can just as easily come back with no usable og:title.
                val html = fetchPage(uri)
                val title = meta(html, "og:title")
                if (title == null) {
                    Timber.tag(TAG).i("EXTERNAL_LINK spotify track no_title_found html_len=%d", html.length)
                    return null
                }
                // og:description: "Artist · Album · Song · 1987" — but Spotify does not guarantee
                // that exact shape, and `substringBefore` silently returns the WHOLE string
                // unchanged when the separator isn't there, which used to hand the entire
                // description (garbage) to the matcher as an "artist" and sink its score below
                // MIN_MATCH_SCORE — reading as "no encontrado" for a song that really is on YTM.
                // Empty is the safe fallback: the search still runs on the title alone.
                val artist = meta(html, "og:description")
                    ?.let { desc ->
                        when {
                            " · " in desc -> desc.substringBefore(" · ")
                            " - " in desc -> desc.substringBefore(" - ")
                            else -> null
                        }
                    }
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && it.length <= 100 }
                    .orEmpty()
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
        val type = segments.getOrNull(0)?.takeIf { it in setOf("track", "album", "playlist") }
            ?: return pageQuery(uri, kindFromPath(uri))
        val id = segments.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: return pageQuery(uri, kindFromPath(uri))
        val root = ExternalCatalog.deezerObject(fetch(ExternalCatalog.deezerItemUrl(type, id)))
            ?: return pageQuery(uri, kindFromPath(uri))
        return when (type) {
            "track" -> {
                val t = ExternalCatalog.deezerTrack(root, null) ?: return pageQuery(uri, Kind.TRACK)
                Resolved.Tracks(Kind.TRACK, t.title, t.artist, listOf(t.toSpotifyTrack()))
            }
            else -> {
                val title = root.str("title").orEmpty()
                val albumName = if (type == "album") title else null
                // A page that fails ends the list there; with nothing read, the list embedded in the answer above is used.
                val tracks = ExternalCatalog.deezerAllTracks(albumName) { index ->
                    runCatching { ExternalCatalog.deezerObject(fetch(ExternalCatalog.deezerTracksUrl(type, id, index))) }.getOrNull()
                }.ifEmpty { ExternalCatalog.deezerEmbeddedTracks(root, albumName) }
                val artist = (root["artist"] as? JsonObject)?.str("name").orEmpty()
                Resolved.Tracks(if (type == "album") Kind.ALBUM else Kind.PLAYLIST, title, artist, tracks.map { it.toSpotifyTrack() })
            }
        }
    }

    private fun apple(uri: Uri): Resolved? {
        val segments = uri.pathSegments
        val country = segments.firstOrNull()?.takeIf { it.length == 2 } ?: "us"
        val type = segments.firstOrNull { it in setOf("album", "song", "playlist", "music-video") }
        val trackId = uri.getQueryParameter("i")
        val lastId = segments.lastOrNull()
        fun lookup(id: String, withSongs: Boolean) =
            ExternalCatalog.itunesResults(fetch(ExternalCatalog.itunesLookupUrl(id, country, withSongs)))
        return when {
            trackId != null || type == "song" -> {
                val t = ExternalCatalog.itunesSong(lookup(trackId ?: lastId ?: return null, withSongs = false))
                    ?: return pageQuery(uri, Kind.TRACK)
                Resolved.Tracks(Kind.TRACK, t.title, t.artist, listOf(t.toSpotifyTrack()))
            }
            type == "album" && lastId != null -> {
                val album = ExternalCatalog.itunesAlbum(lookup(lastId, withSongs = true)) ?: return pageQuery(uri, Kind.ALBUM)
                Resolved.Tracks(Kind.ALBUM, album.title, album.artist, album.tracks.map { it.toSpotifyTrack() })
            }
            else -> pageQuery(uri, kindFromPath(uri))
        }
    }

    private fun tidal(uri: Uri): Resolved? {
        val kind = kindFromPath(uri)
        val html = fetchPage(uri)
        val title = meta(html, "og:title")
        if (title == null) {
            Timber.tag(TAG).i("EXTERNAL_LINK tidal no_title_found html_len=%d", html.length)
            return null
        }
        if (kind != Kind.TRACK) return Resolved.Query(kind, title.replace(" - ", " "))
        // og:title: "Artist - Title"
        val artist = title.substringBefore(" - ", "")
        val name = title.substringAfter(" - ")
        return Resolved.Tracks(Kind.TRACK, name, artist, listOf(track("td_${uri.lastPathSegment}", name, artist)))
    }

    /**
     * Fetch [uri]'s HTML trying the link-preview bot User-Agent FIRST (more likely to get a JS-heavy SPA's
     * server-rendered og:* tags — see the note above MOBILE_UA), falling back to the normal mobile UA if
     * that request fails outright (some sites do block known bot UAs) — never worse than a plain fetch.
     * Shared by every direct-scrape path (pageQuery, spotify's track branch, tidal) so a fix to this
     * technique benefits all of them at once instead of only whichever branch happened to get it first.
     */
    private fun fetchPage(uri: Uri): String =
        runCatching { fetch(uri.toString(), LINK_PREVIEW_UA) }.getOrElse { fetch(uri.toString()) }

    /** Any other page: search YouTube Music for its Open Graph title. */
    private fun pageQuery(uri: Uri, kind: Kind): Resolved? {
        val html = fetchPage(uri)
        val ogTitle = meta(html, "og:title")
        val raw = ogTitle ?: Regex("<title>([^<]+)</title>").find(html)?.groupValues?.get(1)
        if (raw == null) {
            Timber.tag(TAG).i("EXTERNAL_LINK pageQuery host=%s no_title_found html_len=%d", uri.host, html.length)
            return null
        }
        val text = raw
            .replace(Regex("\\s*[|·–-]\\s*(SoundCloud|Amazon Music|Apple Music|Spotify|TIDAL|Deezer|Songlink|Odesli).*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(Stream|Listen to|Escucha)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(by|de)\\s+", RegexOption.IGNORE_CASE), " ")
            .trim()
        return text.takeIf { it.isNotBlank() }?.let { Resolved.Query(kind, it) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    private fun kindFromPath(uri: Uri): Kind = kindFromPathAndQuery(uri.path, uri.getQueryParameter("trackAsin"))

    /**
     * Ronda 9 (dueño): un link de Amazon Music para UNA canción se comparte casi siempre como
     * `/albums/{albumAsin}?trackAsin={trackAsin}` — la ruta sola dice "álbum" ("/albums/" contiene
     * "/album"), y `trackAsin` nunca se miraba. Eso hacía que una canción se tratara como si hubiera
     * pedido el ÁLBUM completo: [pageQuery] scrapea el título de la página del ÁLBUM (no de la
     * canción), y luego se busca ese título como álbum en YouTube Music — una coincidencia mucho más
     * estricta que buscar una canción, así que una variación de título (edición, mayúsculas) bastaba
     * para decir "no encontrado" aunque la canción sí estuviera. `trackAsin` es un parámetro propio de
     * Amazon: si está presente, la intención es inequívocamente UNA canción, sin importar la ruta.
     */
    internal fun kindFromPathAndQuery(path: String?, trackAsin: String?): Kind {
        if (!trackAsin.isNullOrBlank()) return Kind.TRACK
        val p = path.orEmpty().lowercase()
        return when {
            "/album" in p -> Kind.ALBUM
            "/playlist" in p || "/sets/" in p -> Kind.PLAYLIST
            else -> Kind.TRACK
        }
    }

    private fun ExternalCatalog.Track.toSpotifyTrack() = track(id, title, artist, durationMs, album)

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

    private fun fetch(url: String, userAgent: String = MOBILE_UA, timeoutMs: Int = 12_000): String {
        val connection = open(url, userAgent, timeoutMs)
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, userAgent: String = MOBILE_UA, timeoutMs: Int = 12_000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept-Language", "es,en;q=0.8")
        }

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"

    // Ronda 9 (dueño, reporte repetido de "no encontrado" sin causa clara): [pageQuery] cubre páginas
    // de una sola página (SPA) — Amazon Music, SoundCloud — que muchas veces NO renderizan su propio
    // <meta property="og:*"> del lado del servidor para un navegador normal (solo lo arma con
    // JavaScript, que esta petición no ejecuta), pero SÍ lo hacen para los bots de vista previa de
    // enlaces (Facebook/Twitter/Discord) — es justo para eso que existen esas etiquetas. Usar ese
    // mismo user-agent es la misma técnica que esos bots ya usan, no un workaround de nada que el
    // sitio no quiera compartir: el contenido es público y las etiquetas están para leerse así.
    private const val LINK_PREVIEW_UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uat.php)"

    private fun meta(html: String, property: String): String? =
        Regex("<meta[^>]+(?:property|name)=\"${Regex.escape(property)}\"[^>]+content=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#x27;", "'")?.replace("&#39;", "'")
            ?.trim()?.takeIf { it.isNotBlank() }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
