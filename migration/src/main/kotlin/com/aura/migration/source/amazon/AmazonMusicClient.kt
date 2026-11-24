/*
 * Adapted from ahnafnafee/songmirror@e5bad7a (MIT): `AmazonMusicWebClient` in songmirror/amazon_music_web.py,
 * READ side only. Copyright (c) 2026 Ahnaf An Nafee. The MIT permission notice is reproduced in full in
 * AmazonMusicWeb.kt, next to this file, and applies to this file too.
 */

package com.aura.migration.source.amazon

import com.aura.migration.model.SourceType
import com.aura.migration.net.arr
import com.aura.migration.net.obj
import com.aura.migration.source.SourceError
import com.aura.migration.source.amazon.AmazonMusicWeb.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import kotlin.random.Random

/** One HTTP exchange, kept abstract so the client is tested with recorded responses and no network. */
interface AmazonHttp {
    /** Must NOT follow redirects: a redirect would carry the Cookie header to a host we never chose. */
    suspend fun execute(request: AmazonHttpRequest): AmazonHttpResponse
}

class AmazonHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    /** JSON body for a POST; null for a GET. An empty string is an empty POST (config.json). */
    val body: String? = null,
) {
    // Headers carry the session: never printed, not even by accident in a test failure.
    override fun toString(): String = "AmazonHttpRequest($method ${url.substringBefore('?')})"
}

class AmazonHttpResponse(
    val code: Int,
    val body: String,
    /** Raw `Set-Cookie` header values, in order. */
    val setCookies: List<String> = emptyList(),
    val retryAfterSeconds: Long? = null,
)

/**
 * The minimal secret Aura keeps for Amazon Music: the allowlisted renewal cookies, the minted GraphQL
 * headers and their expiry, and the marketplace host they belong to. Stored only through an encrypted
 * [AmazonSessionStore]; its [toString] prints counts, never a value.
 */
class AmazonMusicSession(
    val musicHost: String,
    val cookies: Map<String, String>,
    val headers: Map<String, String>,
    /** Epoch ms after which the headers are renewed; 0 = unknown (renewed only on rejection). */
    val expiresAtMs: Long,
    val userAgent: String = AmazonMusicWeb.DESKTOP_USER_AGENT,
) {
    fun copy(
        cookies: Map<String, String> = this.cookies,
        headers: Map<String, String> = this.headers,
        expiresAtMs: Long = this.expiresAtMs,
    ) = AmazonMusicSession(musicHost, cookies, headers, expiresAtMs, userAgent)

    fun toJson(): String = buildJsonObject {
        put("v", 1)
        put("music_host", musicHost)
        put("user_agent", userAgent)
        put("expires_at", expiresAtMs)
        put("cookies", JsonObject(cookies.mapValues { JsonPrimitive(it.value) }))
        put("headers", JsonObject(headers.mapValues { JsonPrimitive(it.value) }))
    }.toString()

    override fun toString(): String =
        "AmazonMusicSession(host=$musicHost, cookies=${cookies.size}, headers=${headers.size})"

    companion object {
        /** Null for anything unreadable or for a host outside the Music allowlist — the store then re-asks. */
        fun fromJson(raw: String?): AmazonMusicSession? = runCatching {
            val o = Json.parseToJsonElement(raw ?: return null).jsonObject
            val host = o.text("music_host")?.takeIf { AmazonMusicWeb.isMusicHost(it) } ?: return null
            AmazonMusicSession(
                musicHost = host,
                cookies = o.stringMap("cookies").filterKeys { AmazonMusicWeb.isRenewalCookie(it) },
                headers = o.stringMap("headers"),
                expiresAtMs = o.text("expires_at")?.toLongOrNull() ?: 0L,
                userAgent = o.text("user_agent") ?: AmazonMusicWeb.DESKTOP_USER_AGENT,
            )
        }.getOrNull()

        private fun JsonObject.stringMap(key: String): Map<String, String> =
            (this[key] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }
                ?.toMap().orEmpty()
    }
}

/** Where the session lives. The app backs it with EncryptedSharedPreferences; never plain storage. */
interface AmazonSessionStore {
    fun load(): AmazonMusicSession?
    fun save(session: AmazonMusicSession)
    fun clear()
}

/**
 * Amazon Music's web-player session, read only (SongMirror `AmazonMusicWebClient`).
 *
 * Cookies only ever go to the Music host's own `/config.json` and `/pandaToken`; GraphQL gets the minted
 * `authorization` + `x-api-key` (+ device) headers and no cookie. Expired headers are renewed through
 * `/pandaToken` (and config.json when needed) and the rotated cookies are kept, so a sign-in survives
 * until Amazon revokes it. Nothing here writes to Amazon: [query] refuses a mutation outright.
 */
class AmazonMusicClient(
    private val http: AmazonHttp,
    private val store: AmazonSessionStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Mutex()

    @Volatile
    private var cached: AmazonMusicSession? = null

    @Volatile
    private var loaded = false

    private fun session(): AmazonMusicSession? {
        if (!loaded) {
            cached = store.load()
            loaded = true
        }
        return cached
    }

    fun isSignedIn(): Boolean = session()?.cookies?.isNotEmpty() == true

    /** The marketplace host of the stored session, for the screen to say which store it is connected to. */
    fun musicHost(): String? = session()?.musicHost

    /** «Cerrar sesión de Amazon»: forget everything Aura kept. */
    fun logout() {
        cached = null
        loaded = true
        store.clear()
    }

    /**
     * First sign-in from the login WebView's cookies. Proves the whole recovery path before anything is
     * stored (SongMirror `validate(require_renewal=True)`): config.json + pandaToken must mint headers AND
     * GraphQL must recognise a signed-in user. Only then is the session written.
     */
    suspend fun connect(musicHost: String, cookieHeader: String?, userAgent: String) {
        if (!AmazonMusicWeb.isMusicHost(musicHost)) throw AmazonMusicException.signInAgain("not a Music host")
        val cookies = AmazonMusicWeb.renewalCookies(cookieHeader)
        if (!AmazonMusicWeb.hasAccountCookie(cookies)) throw AmazonMusicException.signInAgain("no account cookie yet")
        lock.withLock {
            val candidate = AmazonMusicSession(musicHost, cookies, emptyMap(), 0L, userAgent)
            val renewed = renew(candidate, requirePandaToken = true)
            val data = graphql(renewed, AmazonMusicWeb.SESSION_OPERATION, AmazonMusicWeb.SESSION_QUERY, JsonObject(emptyMap()))
            val userId = data.obj("user")?.text("id")
            if (userId.isNullOrEmpty()) throw AmazonMusicException.signInAgain("no signed-in user")
            store.save(renewed)
            cached = renewed
            loaded = true
        }
    }

    /**
     * Runs one READ query, renewing and retrying once on an auth rejection and backing off on 429/5xx
     * (SongMirror `execute`, non-mutation branch: up to 5 attempts).
     */
    suspend fun query(operationName: String, query: String, variables: JsonObject): JsonObject {
        require(!query.trimStart().startsWith("mutation", ignoreCase = true)) {
            "Amazon Music is read-only in Aura"
        }
        var current = session() ?: throw SourceError.NotAuthenticated(SourceType.AMAZON)
        var refreshed = false
        if (current.headers.isEmpty() || (current.expiresAtMs != 0L && current.expiresAtMs <= now() + EXPIRY_MARGIN_MS)) {
            current = renewAndStore(current)
            refreshed = true
        }
        for (attempt in 0 until MAX_ATTEMPTS) {
            val last = attempt == MAX_ATTEMPTS - 1
            val response = try {
                http.execute(graphqlRequest(current, operationName, query, variables))
            } catch (e: IOException) {
                if (!last) { sleep(backoffMs(attempt)); continue }
                throw SourceError.Network(e.javaClass.simpleName)
            }
            when {
                response.code == 401 || response.code == 403 -> {
                    if (!refreshed && current.cookies.isNotEmpty()) {
                        current = renewAndStore(current)
                        refreshed = true
                        continue
                    }
                    throw SourceError.NotAuthenticated(SourceType.AMAZON)
                }
                response.code == 429 && !last -> {
                    sleep((response.retryAfterSeconds?.times(1000) ?: backoffMs(attempt)) + Random.nextLong(500, 2000))
                    continue
                }
                response.code >= 500 && !last -> { sleep(backoffMs(attempt)); continue }
                response.code !in 200..299 -> throw SourceError.Network("HTTP ${response.code}")
            }
            val body = parseObject(response.body) ?: throw AmazonMusicException.unexpected("GraphQL body is not a JSON object")
            val errors = body.arr("errors")
            if (errors.isNotEmpty()) {
                val text = errors.joinToString(" ") { e ->
                    listOfNotNull(e.text("message"), e.obj("extensions")?.text("code")).joinToString(" ")
                }
                if (AmazonMusicWeb.isAuthError(text)) {
                    if (!refreshed && current.cookies.isNotEmpty()) {
                        current = renewAndStore(current)
                        refreshed = true
                        continue
                    }
                    throw SourceError.NotAuthenticated(SourceType.AMAZON)
                }
                throw AmazonMusicException.rejected("GraphQL errors: ${errors.size}")
            }
            return body.obj("data") ?: JsonObject(emptyMap())
        }
        throw SourceError.Network("retry budget exhausted")
    }

    // ── internals ──────────────────────────────────────────────────────────────────────────────────────

    /** One GraphQL call with fixed headers and no retry: the identity check of [connect]. */
    private suspend fun graphql(
        session: AmazonMusicSession,
        operationName: String,
        query: String,
        variables: JsonObject,
    ): JsonObject {
        val response = try {
            http.execute(graphqlRequest(session, operationName, query, variables))
        } catch (e: IOException) {
            throw SourceError.Network(e.javaClass.simpleName)
        }
        if (response.code == 401 || response.code == 403) throw AmazonMusicException.signInAgain("GraphQL ${response.code}")
        if (response.code !in 200..299) throw SourceError.Network("HTTP ${response.code}")
        val body = parseObject(response.body) ?: throw AmazonMusicException.unexpected("GraphQL body is not a JSON object")
        if (body.arr("errors").isNotEmpty()) throw AmazonMusicException.signInAgain("GraphQL errors on the identity check")
        return body.obj("data") ?: JsonObject(emptyMap())
    }

    private suspend fun renewAndStore(session: AmazonMusicSession): AmazonMusicSession = lock.withLock {
        val renewed = renew(session, requirePandaToken = false)
        store.save(renewed)
        cached = renewed
        renewed
    }

    /** SongMirror `_renew`, same order of attempts and the same fail-closed checks. */
    private suspend fun renew(start: AmazonMusicSession, requirePandaToken: Boolean): AmazonMusicSession {
        if (start.cookies.isEmpty()) throw SourceError.NotAuthenticated(SourceType.AMAZON)
        var session = start
        val previous = AmazonMusicWeb.authorizationContext(session.headers)
        val hasDeviceContext =
            (session.headers["device-id"] ?: previous?.text("deviceId")) != null &&
                (session.headers["device-type"] ?: previous?.text("deviceType")) != null

        var config: JsonObject = JsonObject(emptyMap())
        var configRequested = false
        if (requirePandaToken || !hasDeviceContext) {
            val (s, c) = requestConfig(session)
            session = s; config = c; configRequested = true
        }

        var token: JsonObject
        try {
            val (s, t) = requestPandaToken(session)
            session = s; token = t
        } catch (e: RenewalRejected) {
            if (configRequested) throw AmazonMusicException.signInAgain("pandaToken rejected after config")
            val (s1, c) = requestConfig(session)
            session = s1; config = c; configRequested = true
            val (s2, t) = requestPandaTokenOrSignIn(session)
            session = s2; token = t
        }

        var pandaAccess = token.text("accessToken") ?: token.text("access_token")
        if (pandaAccess == null && !configRequested) {
            val (s1, c) = requestConfig(session)
            session = s1; config = c; configRequested = true
            val (s2, t) = requestPandaTokenOrSignIn(session)
            session = s2; token = t
            pandaAccess = token.text("accessToken") ?: token.text("access_token")
        }
        val access = if (requirePandaToken) pandaAccess else pandaAccess ?: config.text("accessToken")
        if (access.isNullOrEmpty()) throw AmazonMusicException.signInAgain("pandaToken returned no access token")
        if (requirePandaToken && !AmazonMusicWeb.hasAccountCookie(session.cookies)) {
            throw AmazonMusicException.signInAgain("account cookie revoked during validation")
        }
        val deviceId = config.text("deviceId") ?: session.headers["device-id"] ?: previous?.text("deviceId")
        val deviceType = config.text("deviceType") ?: session.headers["device-type"] ?: previous?.text("deviceType")
        if (deviceId.isNullOrEmpty() || deviceType.isNullOrEmpty()) {
            throw AmazonMusicException.unexpected("config.json returned no device context")
        }

        val merged = config.toMutableMap<String, JsonElement>()
        merged["accessToken"] = JsonPrimitive(access)
        merged["deviceId"] = JsonPrimitive(deviceId)
        merged["deviceType"] = JsonPrimitive(deviceType)
        // A panda-only renewal does not fetch config.json: keep the marketplace/client context we had.
        for ((header, key) in CARRIED_CONTEXT) {
            if (JsonObject(merged).text(key) == null) session.headers[header]?.let { merged[key] = JsonPrimitive(it) }
        }
        val headers = AmazonMusicWeb.headersFromConfig(JsonObject(merged))
        val expiresIn = (token.text("expiresIn") ?: token.text("expires_in"))?.toDoubleOrNull() ?: 0.0
        val expiresAt = if (expiresIn > 0) now() + (expiresIn * 1000).toLong() else 0L
        return session.copy(headers = headers, expiresAtMs = expiresAt)
    }

    private class RenewalRejected : Exception()

    private suspend fun requestConfig(session: AmazonMusicSession): Pair<AmazonMusicSession, JsonObject> {
        val response = renewalCall(session, "POST", "https://${session.musicHost}/config.json?skipToken=false", body = "")
        val updated = session.copy(cookies = rotateCookies(session, response.setCookies))
        if (response.code in REJECTED) throw AmazonMusicException.signInAgain("config.json ${response.code}")
        if (response.code in 300..399) throw AmazonMusicException.signInAgain("config.json redirected")
        if (response.code !in 200..299) throw SourceError.Network("HTTP ${response.code}")
        val config = parseObject(response.body) ?: throw AmazonMusicException.unexpected("config.json is not a JSON object")
        if (config.text("redirectUrl") != null && (config.text("deviceId") == null || config.text("deviceType") == null)) {
            throw AmazonMusicException.signInAgain("config.json asked to sign in")
        }
        return updated to config
    }

    private suspend fun requestPandaToken(session: AmazonMusicSession): Pair<AmazonMusicSession, JsonObject> {
        val response = renewalCall(session, "GET", "https://${session.musicHost}/pandaToken", body = null)
        val updated = session.copy(cookies = rotateCookies(session, response.setCookies))
        if (response.code in REJECTED || response.code in 300..399) throw RenewalRejected()
        if (response.code !in 200..299) throw SourceError.Network("HTTP ${response.code}")
        val token = parseObject(response.body) ?: throw AmazonMusicException.unexpected("pandaToken is not a JSON object")
        return updated to token
    }

    /** A second pandaToken rejection, after config.json was replayed, means the session is gone. */
    private suspend fun requestPandaTokenOrSignIn(session: AmazonMusicSession): Pair<AmazonMusicSession, JsonObject> =
        try {
            requestPandaToken(session)
        } catch (e: RenewalRejected) {
            throw AmazonMusicException.signInAgain("pandaToken rejected after replaying config")
        }

    private suspend fun renewalCall(session: AmazonMusicSession, method: String, url: String, body: String?): AmazonHttpResponse {
        val origin = "https://${session.musicHost}"
        val headers = linkedMapOf(
            "Accept" to "*/*",
            "Origin" to origin,
            "Referer" to "$origin/",
            "User-Agent" to session.userAgent,
            "Cookie" to AmazonMusicWeb.cookieHeader(session.cookies),
        )
        session.headers["accept-language"]?.let { headers["Accept-Language"] = it }
        return try {
            http.execute(AmazonHttpRequest(method, url, headers, body))
        } catch (e: IOException) {
            throw SourceError.Network(e.javaClass.simpleName)
        }
    }

    /**
     * Applies a response's `Set-Cookie` rotations (SongMirror `_sync_response_cookies`): only allowlisted
     * names, only from the exact domain that cookie belongs to, and an expired/empty one is dropped.
     */
    internal fun rotateCookies(session: AmazonMusicSession, setCookies: List<String>): Map<String, String> {
        if (setCookies.isEmpty()) return session.cookies
        val allowed = AmazonMusicWeb.allowedRenewalCookies(session.cookies)
        val url = "https://${session.musicHost}/".toHttpUrl()
        val out = LinkedHashMap(session.cookies)
        for (header in setCookies) {
            val cookie = Cookie.parse(url, header) ?: continue
            val name = cookie.name.lowercase(Locale.ROOT)
            if (name !in allowed) continue
            if (cookie.domain.lowercase(Locale.ROOT) != AmazonMusicWeb.cookieDomain(name, session.musicHost)) continue
            val value = AmazonMusicWeb.cleanCookieValue(cookie.value)
            if (value == null || cookie.expiresAt < now()) out.remove(name) else out[name] = value
        }
        return out
    }

    private fun graphqlRequest(
        session: AmazonMusicSession,
        operationName: String,
        query: String,
        variables: JsonObject,
    ): AmazonHttpRequest {
        val origin = "https://${session.musicHost}"
        val headers = LinkedHashMap(session.headers)
        headers["Accept"] = "application/json"
        headers["Content-Type"] = "application/json"
        headers["Origin"] = origin
        headers["Referer"] = "$origin/"
        val body = buildJsonObject {
            put("operationName", operationName)
            put("query", query)
            put("variables", variables)
        }.toString()
        return AmazonHttpRequest("POST", AmazonMusicWeb.GRAPHQL_ENDPOINT, headers, body)
    }

    private fun parseObject(body: String): JsonObject? =
        runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    /** SongMirror: `min(2**attempt, 20) + uniform(0, 1.5)` seconds. */
    private fun backoffMs(attempt: Int): Long =
        minOf(1L shl attempt, 20L) * 1000L + Random.nextLong(0, 1500)

    private companion object {
        const val MAX_ATTEMPTS = 5
        /** SongMirror renews when the token has less than 90 s left. */
        const val EXPIRY_MARGIN_MS = 90_000L
        val REJECTED = setOf(400, 401, 403)
        val CARRIED_CONTEXT = listOf(
            "music-territory" to "musicTerritory",
            "x-amzn-session-id" to "sessionId",
            "x-amzn-client-app-version" to "version",
            "accept-language" to "locale",
        )
    }
}

/**
 * OkHttp transport with redirects OFF: OkHttp would otherwise re-send our hand-set Cookie header to a
 * redirect target on another host. A 3xx surfaces as "sign in again" in [AmazonMusicClient].
 */
class OkHttpAmazonHttp(base: OkHttpClient) : AmazonHttp {

    private val client = base.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun execute(request: AmazonHttpRequest): AmazonHttpResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        when {
            request.body == null -> builder.get()
            request.body.isEmpty() -> builder.post(ByteArray(0).toRequestBody(null))
            else -> builder.post(request.body.toRequestBody(JSON))
        }
        client.newCall(builder.build()).execute().use { response ->
            AmazonHttpResponse(
                code = response.code,
                body = response.body.string(),
                setCookies = response.headers("Set-Cookie"),
                retryAfterSeconds = response.header("Retry-After")?.trim()?.toLongOrNull(),
            )
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
