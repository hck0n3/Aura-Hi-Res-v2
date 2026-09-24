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

        /**
         * Ronda 9 (dueño): "verifica si hay algo mejor". Odesli/song.link (la misma empresa detrás
         * de los links que Aura ya reconoce como `song.link`) mantiene su PROPIA base pública y
         * gratuita de equivalencias entre plataformas — es justo su función. Cuando tiene el
         * equivalente de YouTube Music para el link que se abrió, es un id EXACTO por catálogo, no
         * una búsqueda de texto adivinada — máxima confianza posible, se reproduce directo.
         */
        data class DirectVideo(val videoId: String) : Resolved {
            override val kind = Kind.TRACK
        }
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
            // Odesli PRIMERO — ver el KDoc de [Resolved.DirectVideo]. Solo produce un id cuando su
            // propio catálogo ya conecta este link con YouTube Music; si no lo tiene (lanzamiento
            // reciente que todavía no indexó, o esa plataforma en particular), sigue null y cae al
            // scraping+búsqueda de siempre — nunca peor que antes, y cuando SÍ lo tiene, mucho mejor.
            val direct = odesliYouTubeMusicId(uri)
            val resolved = if (direct != null) {
                Resolved.DirectVideo(direct)
            } else {
                when {
                    uri.scheme == "spotify" -> {
                        val parts = uri.schemeSpecificPart.split(":")
                        spotify(context, Uri.parse("https://open.spotify.com/${parts.getOrNull(0)}/${parts.getOrNull(1)}"))
                    }
                    expandedHost == "open.spotify.com" -> spotify(context, uri)
                    expandedHost.endsWith("deezer.com") -> deezer(uri)
                    expandedHost.endsWith("apple.com") -> apple(uri)
                    expandedHost.endsWith("tidal.com") -> tidal(uri)
                    else -> pageQuery(uri, kindFromPath(uri))
                }
            }
            val outcome = when (resolved) {
                null -> "null"
                is Resolved.DirectVideo -> "odesli_direct"
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

    /**
     * Consulta la API pública y sin clave de Odesli (api.song.link — la misma empresa detrás de
     * `song.link`/`odesli.co`) por la equivalencia REAL de este link en YouTube Music, si su propio
     * catálogo ya la conoce. Solo funciona cuando la URL de YouTube Music trae `?v=<id>` (una
     * canción de verdad, no un álbum/playlist — YouTube Music no tiene una URL de "reproducir álbum
     * directo" equivalente), así que esto se autolimita al caso donde de verdad hay máxima
     * confianza: si no la trae, se cae al scraping+búsqueda de siempre, tal cual antes.
     */
    private fun odesliYouTubeMusicId(uri: Uri): String? = runCatching {
        val apiUrl = "https://api.song.link/v1-alpha.1/links?url=" +
            java.net.URLEncoder.encode(uri.toString(), "UTF-8")
        // Timeout corto (regla de batería/latencia — AGENTS.md #7): esto es un intento EXTRA antes
        // del camino de siempre, así que si Odesli está lento o no responde, la espera no se duplica
        // — cae al scraping+búsqueda de siempre casi de inmediato.
        val body = fetch(apiUrl, timeoutMs = 5_000)
        val byPlatform = json.parseToJsonElement(body).jsonObject["linksByPlatform"] as? JsonObject
        val ytMusic = byPlatform?.get("youtubeMusic") as? JsonObject ?: byPlatform?.get("youtube") as? JsonObject
        val ytUrl = ytMusic?.str("url") ?: return@runCatching null
        Uri.parse(ytUrl).getQueryParameter("v")?.takeIf { it.isNotBlank() }
    }.getOrNull()

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
