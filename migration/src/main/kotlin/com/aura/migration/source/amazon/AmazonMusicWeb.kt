/*
 * Adapted from ahnafnafee/songmirror@e5bad7a (MIT): songmirror/amazon_music_web.py and the READ side of
 * songmirror/engine/targets/amazon_music.py (playlist listing, playlist tracks, liked tracks). Nothing
 * that creates or changes anything on Amazon was ported.
 *
 * Copyright (c) 2026 Ahnaf An Nafee
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.aura.migration.source.amazon

import com.aura.migration.model.CollectionKind
import com.aura.migration.model.SourcePlaylist
import com.aura.migration.model.SourceTrack
import com.aura.migration.model.SourceType
import com.aura.migration.net.arr
import com.aura.migration.net.obj
import com.aura.migration.net.strOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.util.Base64
import java.util.Locale

/**
 * Amazon Music has no public read API: its documented Web API is a closed beta. The consumer web player
 * (music.amazon.<tld>) talks to a private GraphQL endpoint with short-lived headers it mints through
 * its own cookie-authenticated `/config.json` and `/pandaToken` routes. Everything here is that
 * player's behaviour as SongMirror observed it — **[NO VERIFICADO] in Aura**: it can change without
 * notice, so every parser fails with a clear [AmazonMusicException] on a shape it does not recognise.
 *
 * Pure functions only (no I/O): the cookie allowlist, the header minting and the GraphQL page parsers,
 * so they are unit-tested on the JVM with recorded-shape fixtures.
 */
object AmazonMusicWeb {

    const val GRAPHQL_ENDPOINT = "https://gql.music.amazon.dev"
    const val DEFAULT_MUSIC_HOST = "music.amazon.com"

    /**
     * Public identifier embedded in Amazon Music's first-party web bundle (SongMirror: "It identifies the
     * web client; it is not a customer secret"). The per-user token comes from config.json/pandaToken.
     */
    const val FIREFLY_WEB_API_KEY = "amzn1.application.e1dc16675f9f4c78b31927d5bfd5c229"

    /** SongMirror's browser identity for the renewal routes; the login WebView uses the same one. */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    /** The only hosts that may ever receive the replayed account cookies (SongMirror `_MUSIC_HOSTS`). */
    val MUSIC_HOSTS: List<String> = listOf(
        "music.amazon.com", "music.amazon.co.uk", "music.amazon.de", "music.amazon.fr",
        "music.amazon.it", "music.amazon.es", "music.amazon.co.jp", "music.amazon.ca",
        "music.amazon.com.au", "music.amazon.com.br", "music.amazon.com.mx",
        "music.amazon.in", "music.amazon.ae", "music.amazon.sa", "music.amazon.eg",
    )

    fun isMusicHost(host: String?): Boolean = host != null && host in MUSIC_HOSTS

    /** The Music marketplace host of an `https://` URL, or null for anything else (retail, sign-in, http). */
    fun musicHostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (!"https".equals(uri.scheme, ignoreCase = true)) return null
        if (uri.rawUserInfo != null || uri.port != -1) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        return host.takeIf { isMusicHost(it) }
    }

    private val STORE_BY_COUNTRY = mapOf(
        "GB" to "music.amazon.co.uk", "DE" to "music.amazon.de", "FR" to "music.amazon.fr",
        "IT" to "music.amazon.it", "ES" to "music.amazon.es", "JP" to "music.amazon.co.jp",
        "CA" to "music.amazon.ca", "AU" to "music.amazon.com.au", "BR" to "music.amazon.com.br",
        "MX" to "music.amazon.com.mx", "IN" to "music.amazon.in", "AE" to "music.amazon.ae",
        "SA" to "music.amazon.sa", "EG" to "music.amazon.eg",
    )

    /**
     * The sign-in store to suggest for a device country (ISO 3166 code): the marketplace whose domain is
     * that country's, else music.amazon.com. Only a suggestion — the screen lets the user pick another.
     */
    fun storeForCountry(country: String?): String =
        STORE_BY_COUNTRY[country.orEmpty().uppercase(Locale.ROOT)] ?: DEFAULT_MUSIC_HOST

    // ── Cookie allowlist (SongMirror `_COMMON_RENEWAL_COOKIES` / `_RETAIL_COOKIE_RE`) ─────────────────

    private val COMMON_RENEWAL_COOKIES = setOf(
        "am-token", "at-main-music", "sid", "session-id", "session-id-time", "session-token",
    )
    private val RETAIL_COOKIE_NAMES = listOf("at", "sess-at", "sso-state", "sst", "ubid", "x")
    private val RETAIL_COOKIE_RE = Regex("^(?:at|sess-at|sso-state|sst|ubid|x)-([a-z0-9]+(?:-[a-z0-9]+)*)$")

    /** Amazon sets this one on the Music host; every other kept cookie lives on the marketplace parent. */
    private val MUSIC_SCOPED_COOKIES = setOf("at-main-music")

    /** True for the account/session cookies `/pandaToken` needs; analytics, cart, AWS etc. are false. */
    fun isRenewalCookie(name: String): Boolean =
        name in COMMON_RENEWAL_COOKIES || RETAIL_COOKIE_RE.matches(name)

    /**
     * Names a response may rotate: the common set plus the retail families actually present in [cookies]
     * (`at-main` present → `x-main`, `ubid-main`… may rotate; a family never seen may not be introduced).
     */
    fun allowedRenewalCookies(cookies: Map<String, String>): Set<String> {
        val allowed = COMMON_RENEWAL_COOKIES.toMutableSet()
        val suffixes = cookies.keys
            .filter { it !in COMMON_RENEWAL_COOKIES }
            .mapNotNull { RETAIL_COOKIE_RE.matchEntire(it)?.groupValues?.get(1) }
            .toSet()
        for (suffix in suffixes) RETAIL_COOKIE_NAMES.forEach { allowed += "$it-$suffix" }
        return allowed
    }

    /**
     * Minimises a `name=value; name=value` cookie string (what the WebView's CookieManager returns) to the
     * renewal allowlist. Names are lower-cased; empty or malformed values are dropped, never kept.
     */
    fun renewalCookies(cookieHeader: String?): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (part in cookieHeader.orEmpty().split(';')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val name = part.substring(0, eq).trim().lowercase(Locale.ROOT)
            val value = cleanCookieValue(part.substring(eq + 1)) ?: continue
            if (isRenewalCookie(name)) out[name] = value
        }
        return out
    }

    internal fun cleanCookieValue(raw: String): String? {
        val value = raw.trim().trim('"')
        if (value.isEmpty() || value.any { it == '\r' || it == '\n' || it == ';' }) return null
        return value
    }

    /** SongMirror's "has an account cookie" test: without one there is no signed-in session to renew. */
    fun hasAccountCookie(cookies: Map<String, String>): Boolean =
        cookies.keys.any { it == "at-main-music" || it.startsWith("at-") }

    /** The domain a kept cookie must come from: the Music host, or its marketplace parent (`amazon.es`). */
    fun cookieDomain(name: String, musicHost: String): String =
        if (name in MUSIC_SCOPED_COOKIES) musicHost else musicHost.removePrefix("music.")

    fun cookieHeader(cookies: Map<String, String>): String =
        cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }

    // ── Header minting (SongMirror `parse_web_headers`, config.json branch) ─────────────────────────────

    /**
     * Builds the GraphQL headers from a signed-in config.json (plus a pandaToken access token already
     * merged into it). Only these names ever leave the device towards GraphQL — never a cookie.
     */
    fun headersFromConfig(config: JsonObject): Map<String, String> {
        val accessToken = config.text("accessToken")
        val deviceId = config.text("deviceId")
        val deviceType = config.text("deviceType")
        if (accessToken.isNullOrEmpty()) throw AmazonMusicException.signInAgain("config.json has no accessToken")
        if (deviceId.isNullOrEmpty() || deviceType.isNullOrEmpty()) {
            throw AmazonMusicException.unexpected("config.json is missing deviceId or deviceType")
        }
        if (listOf(accessToken, deviceId, deviceType).any { it.contains('\r') || it.contains('\n') }) {
            throw AmazonMusicException.unexpected("config.json value contains a line break")
        }
        val payload = buildJsonObject {
            put("deviceId", deviceId)
            put("deviceType", deviceType)
            put("access_token", accessToken)
        }.toString()
        val headers = linkedMapOf(
            "authorization" to "AmznMusic " + Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8)),
            "x-api-key" to FIREFLY_WEB_API_KEY,
            "device-id" to deviceId,
            "device-type" to deviceType,
        )
        val optional = listOf(
            "music-territory" to config.text("musicTerritory"),
            "x-amzn-session-id" to config.text("sessionId"),
            "x-amzn-client-app-version" to config.text("version"),
            "accept-language" to (config.text("locale") ?: config.text("language")),
        )
        for ((name, value) in optional) {
            if (value.isNullOrEmpty()) continue
            if (value.contains('\r') || value.contains('\n')) {
                throw AmazonMusicException.unexpected("$name value contains a line break")
            }
            headers[name] = value
        }
        return headers
    }

    /** The `{deviceId, deviceType, access_token}` inside an `AmznMusic <base64>` authorization, if any. */
    fun authorizationContext(headers: Map<String, String>): JsonObject? {
        val authorization = headers["authorization"] ?: return null
        if (!authorization.lowercase(Locale.ROOT).startsWith("amznmusic ")) return null
        return runCatching {
            val decoded = Base64.getDecoder().decode(authorization.substringAfter(' ').trim())
            Json.parseToJsonElement(String(decoded, Charsets.UTF_8)).jsonObject
        }.getOrNull()
    }

    /** A JSON value as trimmed text (strings and numbers alike, as SongMirror's `str(value)` does). */
    internal fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    // ── GraphQL (READ ONLY: no mutation is defined here, and the client refuses one) ─────────────────────

    const val SESSION_OPERATION = "AuraAmazonSession"
    val SESSION_QUERY = """
query AuraAmazonSession {
  user { id }
}
"""

    const val PLAYLISTS_OPERATION = "AuraAmazonPlaylists"
    val PLAYLISTS_QUERY = """
query AuraAmazonPlaylists(${'$'}cursor: String, ${'$'}limit: Float!) {
  user {
    playlists(roleFilter: OWNER, sortBy: "RECENTLY_UPDATED", cursor: ${'$'}cursor, limit: ${'$'}limit) {
      edges {
        node {
          id title description visibility trackCount lastModifiedDate
          images { url width height imageType aspectRatio }
        }
      }
      pageInfo { hasNextPage token }
    }
  }
}
"""

    const val PLAYLIST_TRACKS_OPERATION = "AuraAmazonPlaylistTracks"
    val PLAYLIST_TRACKS_QUERY = """
query AuraAmazonPlaylistTracks(${'$'}id: String!, ${'$'}cursor: String, ${'$'}limit: Float!) {
  playlist(id: ${'$'}id) {
    id
    tracks(limit: ${'$'}limit, cursor: ${'$'}cursor) {
      edges {
        cursor
        itemId
        node {
          id
          title
          isrc
          duration
          album { id title }
          contributingArtists { edges { role node { id name } } }
        }
      }
      pageInfo { hasNextPage token }
    }
  }
}
"""

    const val LIKED_TRACKS_OPERATION = "AuraAmazonLikedTracks"
    val LIKED_TRACKS_QUERY = """
query AuraAmazonLikedTracks(${'$'}cursor: String, ${'$'}limit: Float!) {
  user {
    tracks(limit: ${'$'}limit, cursor: ${'$'}cursor) {
      edges {
        likeState
        node {
          id
          title
          isrc
          duration
          album { id title }
          contributingArtists { edges { role node { id name } } }
        }
      }
      pageInfo { hasNextPage token }
    }
  }
}
"""

    /** SongMirror pages a full read 100 at a time. */
    const val PAGE_SIZE = 100

    data class Page<T>(val items: List<T>, val nextCursor: String?)

    /**
     * The next cursor, failing CLOSED on contradictory metadata (SongMirror `_next_cursor`): "more pages"
     * without a token, or a token that does not advance, would otherwise end the read early and migrate a
     * silently truncated list.
     */
    fun nextCursor(pageInfo: JsonObject?, current: String?, what: String): String? {
        val hasNext = (pageInfo?.get("hasNextPage") as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true
        if (!hasNext) return null
        val token = pageInfo.text("token")
            ?: throw AmazonMusicException.incomplete("$what: hasNextPage without a continuation token")
        if (token == current) throw AmazonMusicException.incomplete("$what: continuation token did not advance")
        return token
    }

    /** `data.user.playlists` → one page of the user's OWN playlists. */
    fun parsePlaylistsPage(data: JsonObject, current: String?): Page<SourcePlaylist> {
        val connection = data.obj("user")?.obj("playlists")
            ?: throw AmazonMusicException.unexpected("user.playlists missing")
        val playlists = connection.arr("edges").mapNotNull { edge ->
            val node = edge.obj("node") ?: return@mapNotNull null
            val id = node.text("id") ?: return@mapNotNull null
            SourcePlaylist(
                id = id,
                name = node.strOrNull("title")?.trim().orEmpty(),
                description = node.strOrNull("description"),
                trackCount = node.number("trackCount")?.toInt() ?: 0,
                artworkUrl = node.arr("images").firstNotNullOfOrNull { it.strOrNull("url") },
                origin = SourceType.AMAZON,
                kind = CollectionKind.PLAYLIST,
            )
        }
        val next = nextCursor(connection.obj("pageInfo"), current, "playlist listing")
        return Page(playlists, next)
    }

    /** `data.playlist.tracks` → one page of a playlist, numbered from [startPosition]. */
    fun parsePlaylistTracksPage(data: JsonObject, current: String?, startPosition: Int): Page<SourceTrack> {
        val connection = data.obj("playlist")?.obj("tracks")
            ?: throw AmazonMusicException.unexpected("playlist.tracks missing")
        val edges = connection.arr("edges")
        val next = nextCursor(connection.obj("pageInfo"), current, "playlist tracks")
        if (next != null && edges.isEmpty()) {
            throw AmazonMusicException.incomplete("playlist tracks: an empty page advertised more tracks")
        }
        return Page(tracksFrom(edges.mapNotNull { it.obj("node") }, startPosition), next)
    }

    /** `data.user.tracks` → one page of liked tracks (`likeState` absent counts as LIKE, as in SongMirror). */
    fun parseLikedTracksPage(data: JsonObject, current: String?, startPosition: Int): Page<SourceTrack> {
        val connection = data.obj("user")?.obj("tracks")
            ?: throw AmazonMusicException.unexpected("user.tracks missing")
        val edges = connection.arr("edges")
        val next = nextCursor(connection.obj("pageInfo"), current, "liked tracks")
        if (next != null && edges.isEmpty()) {
            throw AmazonMusicException.incomplete("liked tracks: an empty page advertised more tracks")
        }
        val liked = edges.filter { (it.text("likeState") ?: "LIKE").uppercase(Locale.ROOT) == "LIKE" }
        return Page(tracksFrom(liked.mapNotNull { it.obj("node") }, startPosition), next)
    }

    /** SongMirror `_normalized_track`, narrowed to what the migration matcher reads. Untitled nodes are skipped. */
    private fun tracksFrom(nodes: List<JsonObject>, startPosition: Int): List<SourceTrack> {
        val out = mutableListOf<SourceTrack>()
        for (node in nodes) {
            val title = (node.strOrNull("title") ?: node.strOrNull("name"))?.trim()
            if (title.isNullOrEmpty()) continue
            var artists = node.arr("artists").mapNotNull { it.strOrNull("name")?.trim() }.filter { it.isNotEmpty() }
            if (artists.isEmpty()) {
                artists = node.obj("contributingArtists")?.arr("edges").orEmpty()
                    .mapNotNull { it.obj("node")?.strOrNull("name")?.trim() }
                    .filter { it.isNotEmpty() }
            }
            val album = node.obj("album")
            out += SourceTrack(
                title = title,
                artists = artists,
                album = (album?.strOrNull("title") ?: album?.strOrNull("name"))?.trim(),
                durationMs = node.number("duration")?.let { (it * 1000).toLong() }?.takeIf { it > 0 },
                isrc = node.strOrNull("isrc")?.trim()?.takeIf { it.isNotEmpty() },
                sourcePosition = startPosition + out.size,
            )
        }
        return out
    }

    /** A JSON NUMBER only (SongMirror checks `isinstance(duration, (int, float))`): a quoted value is ignored. */
    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    /** SongMirror `_auth_error`: GraphQL error text that means "the token is no good", not "bad query". */
    fun isAuthError(text: String): Boolean {
        val lowered = text.lowercase(Locale.ROOT)
        return AUTH_MARKERS.any { it in lowered }
    }

    private val AUTH_MARKERS = listOf(
        "access denied", "auth_", "authorization", "forbidden", "invalid access token", "not authenticated",
        "not authorized", "session expired", "token expired", "token_expired", "unauthenticated", "unauthorized",
    )
}

/**
 * Amazon-specific failure with a message the migration screen can show as is (the module's other errors
 * are Spanish text too, see [com.aura.migration.source.SourceError]). Never carries a cookie, a token, a
 * response body or a name from the user's library: only what went wrong, in words.
 */
class AmazonMusicException(
    message: String,
    val reason: Reason,
    /** Technical detail for tests and code paths; same rule — never user data. */
    val detail: String,
) : Exception(message) {

    enum class Reason { SIGN_IN_AGAIN, UNEXPECTED_RESPONSE, INCOMPLETE_READ, REJECTED_QUERY }

    companion object {
        fun signInAgain(detail: String) = AmazonMusicException(
            "Amazon Music pidió iniciar sesión de nuevo. Vuelve a entrar en Amazon Music.",
            Reason.SIGN_IN_AGAIN,
            detail,
        )

        fun unexpected(detail: String) = AmazonMusicException(
            "Amazon Music respondió con un formato que Aura no reconoce. Puede que Amazon haya cambiado su reproductor web.",
            Reason.UNEXPECTED_RESPONSE,
            detail,
        )

        fun incomplete(detail: String) = AmazonMusicException(
            "Amazon Music cortó la lista a medias. No se migra una lista incompleta: inténtalo de nuevo.",
            Reason.INCOMPLETE_READ,
            detail,
        )

        fun rejected(detail: String) = AmazonMusicException(
            "Amazon Music rechazó la consulta.",
            Reason.REJECTED_QUERY,
            detail,
        )
    }
}
