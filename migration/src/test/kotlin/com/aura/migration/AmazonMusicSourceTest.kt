package com.aura.migration

import com.aura.migration.model.CollectionKind
import com.aura.migration.model.SourceType
import com.aura.migration.source.SourceError
import com.aura.migration.source.amazon.AmazonHttp
import com.aura.migration.source.amazon.AmazonHttpRequest
import com.aura.migration.source.amazon.AmazonHttpResponse
import com.aura.migration.source.amazon.AmazonMusicClient
import com.aura.migration.source.amazon.AmazonMusicException
import com.aura.migration.source.amazon.AmazonMusicSession
import com.aura.migration.source.amazon.AmazonMusicSource
import com.aura.migration.source.amazon.AmazonMusicWeb
import com.aura.migration.source.amazon.AmazonSessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Registry rows 844-845: Amazon Music as a READ-ONLY migration source.
 *
 * Amazon's web-player endpoints cannot be called from here (no account, no network), so every response
 * below is a minimal fixture written from SongMirror's own parsing code (ahnafnafee/songmirror@e5bad7a,
 * MIT). What these pin is Aura's side: which cookies are kept, which headers go where, that every page is
 * read to the end, that a contradictory page fails closed, and that nothing is ever written to Amazon.
 */
class AmazonMusicSourceTest {

    // ── fakes ────────────────────────────────────────────────────────────────────────────────────

    private class MemoryStore(var session: AmazonMusicSession? = null) : AmazonSessionStore {
        var saves = 0
        override fun load() = session
        override fun save(session: AmazonMusicSession) { this.session = session; saves++ }
        override fun clear() { session = null }
    }

    private class FakeHttp(private val route: (AmazonHttpRequest) -> AmazonHttpResponse) : AmazonHttp {
        val requests = mutableListOf<AmazonHttpRequest>()
        override suspend fun execute(request: AmazonHttpRequest): AmazonHttpResponse {
            requests += request
            return route(request)
        }
    }

    private val now = 1_000_000_000L

    private fun ok(body: String, vararg setCookies: String) = AmazonHttpResponse(200, body, setCookies.toList())

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun variablesOf(request: AmazonHttpRequest): JsonObject =
        json(request.body!!)["variables"]!!.jsonObject

    private fun operationOf(request: AmazonHttpRequest): String =
        json(request.body!!)["operationName"]!!.jsonPrimitive.content

    private val config = """{"accessToken":"cfg-token","deviceId":"dev-1","deviceType":"A16ZV8BU3SN1N3","musicTerritory":"ES","sessionId":"sess-1","version":"1.0.1","locale":"es_ES"}"""

    private fun signedInSession(expiresAtMs: Long = now + 3_600_000L) = AmazonMusicSession(
        musicHost = "music.amazon.es",
        cookies = mapOf("at-acbes" to "AT", "session-id" to "SID", "session-token" to "STOK"),
        headers = AmazonMusicWeb.headersFromConfig(json(config)),
        expiresAtMs = expiresAtMs,
    )

    private fun client(http: AmazonHttp, store: AmazonSessionStore) =
        AmazonMusicClient(http, store, now = { now }, sleep = {})

    // ── 844: what is kept, and where it may go ───────────────────────────────────────────────────

    @Test
    fun `only the renewal cookies survive, retail and analytics cookies are dropped`() {
        val header = "session-id=123-456; session-id-time=2082787201l; i18n-prefs=EUR; lc-acbes=es_ES; " +
            "csm-hit=tb:XYZ; ubid-acbes=999; at-acbes=Atza|secret; sess-at-acbes=\"quoted\"; x-acbes=abc; " +
            "session-token=TOKEN; skin=noskin; aws-userInfo=%7B%7D; at-main-music=MUSIC; am-token=AM; sid=S"
        val kept = AmazonMusicWeb.renewalCookies(header)
        assertEquals(
            setOf(
                "session-id", "session-id-time", "ubid-acbes", "at-acbes", "sess-at-acbes", "x-acbes",
                "session-token", "at-main-music", "am-token", "sid",
            ),
            kept.keys,
        )
        assertEquals("quotes are stripped like SongMirror does", "quoted", kept["sess-at-acbes"])
        for (dropped in listOf("i18n-prefs", "lc-acbes", "csm-hit", "skin", "aws-userinfo")) {
            assertFalse("$dropped must never be stored", dropped in kept)
        }
        assertTrue(AmazonMusicWeb.hasAccountCookie(kept))
        assertFalse(AmazonMusicWeb.hasAccountCookie(mapOf("session-id" to "1", "ubid-main" to "2")))
    }

    @Test
    fun `rotation may only touch the retail families that were captured`() {
        val allowed = AmazonMusicWeb.allowedRenewalCookies(mapOf("at-acbes" to "a", "session-id" to "b"))
        assertTrue("x-acbes" in allowed)
        assertTrue("ubid-acbes" in allowed)
        assertFalse("a family never captured is not introduced", "at-main" in allowed)
        assertEquals("music.amazon.es", AmazonMusicWeb.cookieDomain("at-main-music", "music.amazon.es"))
        assertEquals("amazon.es", AmazonMusicWeb.cookieDomain("session-token", "music.amazon.es"))
    }

    @Test
    fun `only first-party https Music hosts are accepted, in every region`() {
        assertEquals("music.amazon.es", AmazonMusicWeb.musicHostOf("https://music.amazon.es/my/library"))
        assertEquals("music.amazon.com.mx", AmazonMusicWeb.musicHostOf("https://music.amazon.com.mx/"))
        assertEquals("music.amazon.co.uk", AmazonMusicWeb.musicHostOf("https://MUSIC.amazon.co.uk/home"))
        assertNull(AmazonMusicWeb.musicHostOf("http://music.amazon.com/"))
        assertNull(AmazonMusicWeb.musicHostOf("https://www.amazon.com/ap/signin"))
        assertNull(AmazonMusicWeb.musicHostOf("https://music.amazon.com.evil.example/"))
        assertNull(AmazonMusicWeb.musicHostOf("https://user:pw@music.amazon.com/"))
        assertNull(AmazonMusicWeb.musicHostOf("https://music.amazon.com:8443/"))
        assertEquals(15, AmazonMusicWeb.MUSIC_HOSTS.size)
    }

    @Test
    fun `config json mints the web-player headers and nothing else`() {
        val headers = AmazonMusicWeb.headersFromConfig(json(config))
        assertEquals(
            setOf(
                "authorization", "x-api-key", "device-id", "device-type", "music-territory", "x-amzn-session-id",
                "x-amzn-client-app-version", "accept-language",
            ),
            headers.keys,
        )
        val auth = headers.getValue("authorization")
        assertTrue(auth.startsWith("AmznMusic "))
        val payload = String(Base64.getDecoder().decode(auth.removePrefix("AmznMusic ")))
        assertEquals("""{"deviceId":"dev-1","deviceType":"A16ZV8BU3SN1N3","access_token":"cfg-token"}""", payload)
        assertEquals(AmazonMusicWeb.FIREFLY_WEB_API_KEY, headers["x-api-key"])
        assertEquals("sess-1", headers["x-amzn-session-id"])
        assertEquals("1.0.1", headers["x-amzn-client-app-version"])

        try {
            AmazonMusicWeb.headersFromConfig(json("""{"deviceId":"d","deviceType":"t"}"""))
            fail("a config without accessToken is a signed-out config")
        } catch (e: AmazonMusicException) {
            assertEquals(AmazonMusicException.Reason.SIGN_IN_AGAIN, e.reason)
        }
    }

    @Test
    fun `sign-in proves renewal and identity before storing, and cookies only go to the Music host`() = runBlocking {
        val store = MemoryStore()
        val http = FakeHttp { r ->
            when {
                r.url == "https://music.amazon.es/config.json?skipToken=false" -> ok(config)
                r.url == "https://music.amazon.es/pandaToken" ->
                    ok("""{"accessToken":"panda-token","expiresIn":3600}""", "session-token=ROTATED; Domain=.amazon.es; Path=/; Secure")
                r.url == AmazonMusicWeb.GRAPHQL_ENDPOINT -> ok("""{"data":{"user":{"id":"amzn1.account.X"}}}""")
                else -> error("unexpected ${r.url}")
            }
        }
        client(http, store).connect(
            "music.amazon.es",
            "session-id=1; session-token=T; at-acbes=AT; csm-hit=x; i18n-prefs=EUR",
            AmazonMusicWeb.DESKTOP_USER_AGENT,
        )

        assertEquals(listOf("POST", "GET", "POST"), http.requests.map { it.method })
        val (configCall, pandaCall, gqlCall) = http.requests
        for (renewal in listOf(configCall, pandaCall)) {
            val cookie = renewal.headers.getValue("Cookie")
            assertFalse("analytics cookie replayed: $cookie", cookie.contains("csm-hit"))
            assertFalse("retail prefs cookie replayed: $cookie", cookie.contains("i18n-prefs"))
            assertEquals("https://music.amazon.es", renewal.headers["Origin"])
        }
        assertEquals("", configCall.body)
        assertNull(pandaCall.body)
        assertFalse("GraphQL must never receive a cookie", gqlCall.headers.keys.any { it.equals("cookie", true) })
        val gqlAuth = String(Base64.getDecoder().decode(gqlCall.headers.getValue("authorization").removePrefix("AmznMusic ")))
        assertTrue("the pandaToken token is the one used", gqlAuth.contains("panda-token"))

        val saved = assertNotNullAndGet(store.session)
        assertEquals("music.amazon.es", saved.musicHost)
        assertEquals("ROTATED", saved.cookies["session-token"])
        assertEquals(setOf("session-id", "session-token", "at-acbes"), saved.cookies.keys)
        assertEquals(now + 3_600_000L, saved.expiresAtMs)
    }

    @Test
    fun `a sign-in Amazon does not recognise is never stored`() = runBlocking {
        val store = MemoryStore()
        val http = FakeHttp { r ->
            when {
                r.url.endsWith("/config.json?skipToken=false") -> ok(config)
                r.url.endsWith("/pandaToken") -> ok("""{"accessToken":"p"}""")
                else -> ok("""{"data":{"user":null}}""")
            }
        }
        try {
            client(http, store).connect("music.amazon.com", "at-main=A; session-id=S", "UA")
            fail("no user id must not connect")
        } catch (e: AmazonMusicException) {
            assertEquals(AmazonMusicException.Reason.SIGN_IN_AGAIN, e.reason)
        }
        assertNull(store.session)

        try {
            client(FakeHttp { error("no call expected") }, store).connect("music.amazon.com", "session-id=S; ubid-main=U", "UA")
            fail("without an account cookie there is nobody signed in yet")
        } catch (e: AmazonMusicException) {
            assertEquals(AmazonMusicException.Reason.SIGN_IN_AGAIN, e.reason)
        }
        try {
            client(FakeHttp { error("no call expected") }, store).connect("www.amazon.com", "at-main=A", "UA")
            fail("cookies are never replayed to a non-Music host")
        } catch (e: AmazonMusicException) {
            assertEquals(AmazonMusicException.Reason.SIGN_IN_AGAIN, e.reason)
        }
        assertEquals(0, store.saves)
    }

    @Test
    fun `an expired token is renewed through pandaToken and rotations keep to their own domain`() = runBlocking {
        val store = MemoryStore(signedInSession(expiresAtMs = now + 10_000L))
        val http = FakeHttp { r ->
            when {
                r.url.endsWith("/pandaToken") -> ok(
                    """{"accessToken":"fresh","expiresIn":3600}""",
                    "session-token=NEW; Domain=.amazon.es; Path=/",
                    "at-main=SMUGGLED; Domain=.amazon.es; Path=/",
                    "session-id=EXPIRED; Domain=.amazon.es; Max-Age=0",
                    "at-main-music=WRONGDOMAIN; Domain=.amazon.es; Path=/",
                )
                r.url == AmazonMusicWeb.GRAPHQL_ENDPOINT -> ok("""{"data":{"ok":true}}""")
                else -> error("config.json is not needed while the device context is known: ${r.url}")
            }
        }
        client(http, store).query("Op", "query Op { ok }", JsonObject(emptyMap()))

        val saved = assertNotNullAndGet(store.session)
        assertEquals("NEW", saved.cookies["session-token"])
        assertFalse("a family that was never captured is not introduced", "at-main" in saved.cookies)
        assertFalse("Max-Age=0 removes the cookie", "session-id" in saved.cookies)
        assertFalse("a Music-scoped cookie from the parent domain is refused", "at-main-music" in saved.cookies)
        assertTrue(String(Base64.getDecoder().decode(saved.headers.getValue("authorization").removePrefix("AmznMusic "))).contains("fresh"))
        assertEquals("territory carried over from the previous config", "ES", saved.headers["music-territory"])
    }

    @Test
    fun `a rejected token is renewed once, and a second rejection means sign in again`() = runBlocking {
        val store = MemoryStore(signedInSession())
        var gqlCalls = 0
        val http = FakeHttp { r ->
            when {
                r.url.endsWith("/pandaToken") -> ok("""{"accessToken":"again"}""")
                else -> { gqlCalls++; AmazonHttpResponse(401, "") }
            }
        }
        try {
            client(http, store).query("Op", "query Op { ok }", JsonObject(emptyMap()))
            fail("two rejections must surface as not authenticated")
        } catch (e: SourceError.NotAuthenticated) {
            assertEquals(SourceType.AMAZON, e.type)
        }
        assertEquals(2, gqlCalls)
        assertEquals(1, http.requests.count { it.url.endsWith("/pandaToken") })

        val authError = FakeHttp { r ->
            if (r.url.endsWith("/pandaToken")) ok("""{"accessToken":"again"}""")
            else ok("""{"errors":[{"message":"Token expired","extensions":{"code":"UNAUTHENTICATED"}}]}""")
        }
        try {
            client(authError, MemoryStore(signedInSession())).query("Op", "query Op { ok }", JsonObject(emptyMap()))
            fail("a GraphQL auth error is a rejection too")
        } catch (e: SourceError.NotAuthenticated) {
            // expected
        }
    }

    @Test
    fun `server errors are retried and then reported, never swallowed into an empty list`() = runBlocking {
        var calls = 0
        val http = FakeHttp { calls++; AmazonHttpResponse(503, "") }
        try {
            client(http, MemoryStore(signedInSession())).query("Op", "query Op { ok }", JsonObject(emptyMap()))
            fail("five 503s are a network error")
        } catch (e: SourceError.Network) {
            // expected
        }
        assertEquals(5, calls)
    }

    @Test
    fun `Aura never writes to Amazon`() = runBlocking {
        try {
            client(FakeHttp { error("no call") }, MemoryStore(signedInSession()))
                .query("X", "mutation X { setTrackLikeState(trackId: \"1\", likeState: \"LIKE\") { __typename } }", JsonObject(emptyMap()))
            fail("a mutation must be refused before it is sent")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        val dir = File(repoRoot(), "migration/src/main/kotlin/com/aura/migration/source/amazon")
        val code = dir.listFiles()!!.filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        for (write in listOf("createPlaylist", "appendTracks", "removeTracks", "setTrackLikeState", "addFavorite")) {
            assertFalse("$write appears in the Amazon source", code.contains(write))
        }
        assertFalse("a GraphQL mutation is defined", Regex("\"\"\"\\s*mutation", RegexOption.IGNORE_CASE).containsMatchIn(code))
    }

    // ── 845: parsing and paging ──────────────────────────────────────────────────────────────────

    private fun playlistsPage(ids: List<String>, next: String?) = """
        {"data":{"user":{"playlists":{
          "edges":[${ids.joinToString(",") { """{"node":{"id":"$it","title":"List $it","trackCount":${it.length},"images":[{"url":"https://m.media-amazon.com/$it.jpg","width":500}]}}""" }}],
          "pageInfo":{"hasNextPage":${next != null},"token":${next?.let { "\"$it\"" } ?: "null"}}
        }}}}
    """.trimIndent()

    private fun trackNode(title: String?, isrc: String? = null) = """
        {"id":"t-$title","title":${title?.let { "\"$it\"" } ?: "null"},"isrc":${isrc?.let { "\"$it\"" } ?: "null"},"duration":215,
         "album":{"id":"al","title":"Album $title"},
         "contributingArtists":{"edges":[{"role":"PRIMARY","node":{"id":"a1","name":"Artist A"}},{"role":"FEATURED","node":{"id":"a2","name":"Artist B"}}]}}
    """.trimIndent()

    @Test
    fun `the library lists liked songs first and then every playlist page to the end`() = runBlocking {
        val http = FakeHttp { r ->
            when (variablesOf(r)["cursor"]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.content }) {
                null -> ok(playlistsPage(listOf("p1", "p2"), "c1"))
                "c1" -> ok(playlistsPage(listOf("p3"), "c2"))
                "c2" -> ok(playlistsPage(listOf("p4", "p2"), null))
                else -> error("unexpected cursor")
            }
        }
        val lists = AmazonMusicSource(client(http, MemoryStore(signedInSession()))).listPlaylists(null)

        assertEquals(CollectionKind.FAVORITE_TRACKS, lists.first().kind)
        assertEquals(AmazonMusicSource.COLLECTION_LIKED_TRACKS, lists.first().id)
        assertEquals("Canciones que te gustan", lists.first().name)
        assertEquals("every page, each playlist once", listOf("p1", "p2", "p3", "p4"), lists.drop(1).map { it.id })
        assertTrue(lists.all { it.origin == SourceType.AMAZON })
        assertEquals("https://m.media-amazon.com/p1.jpg", lists[1].artworkUrl)
        assertEquals(3, http.requests.size)
        assertTrue(http.requests.all { operationOf(it) == AmazonMusicWeb.PLAYLISTS_OPERATION })
        assertEquals(100, variablesOf(http.requests.first())["limit"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `playlist tracks carry title, artists, album, duration and ISRC, numbered across pages`() = runBlocking {
        val http = FakeHttp { r ->
            assertEquals("pl-9", variablesOf(r)["id"]!!.jsonPrimitive.content)
            val cursor = variablesOf(r)["cursor"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content
            if (cursor == null) {
                ok("""{"data":{"playlist":{"id":"pl-9","tracks":{"edges":[{"cursor":"x:1","itemId":"e1","node":${trackNode("One", "USRC17607839")}},{"node":${trackNode(null)}}],"pageInfo":{"hasNextPage":true,"token":"n1"}}}}}""")
            } else {
                ok("""{"data":{"playlist":{"id":"pl-9","tracks":{"edges":[{"node":${trackNode("Two")}}],"pageInfo":{"hasNextPage":false}}}}}""")
            }
        }
        val tracks = AmazonMusicSource(client(http, MemoryStore(signedInSession()))).fetchTracks("pl-9")

        assertEquals("an untitled entry is skipped, not invented", listOf("One", "Two"), tracks.map { it.title })
        val one = tracks.first()
        assertEquals(listOf("Artist A", "Artist B"), one.artists)
        assertEquals("Album One", one.album)
        assertEquals("seconds become milliseconds", 215_000L, one.durationMs)
        assertEquals("USRC17607839", one.isrc)
        assertNull(tracks[1].isrc)
        assertEquals(listOf(0, 1), tracks.map { it.sourcePosition })
        assertTrue(http.requests.all { operationOf(it) == AmazonMusicWeb.PLAYLIST_TRACKS_OPERATION })
    }

    @Test
    fun `liked songs keep only LIKE entries and read every page`() = runBlocking {
        val http = FakeHttp { r ->
            assertEquals(AmazonMusicWeb.LIKED_TRACKS_OPERATION, operationOf(r))
            val cursor = variablesOf(r)["cursor"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content
            if (cursor == null) {
                ok("""{"data":{"user":{"tracks":{"edges":[{"likeState":"LIKE","node":${trackNode("Loved")}},{"likeState":"DISLIKE","node":${trackNode("Hated")}},{"node":${trackNode("NoState")}}],"pageInfo":{"hasNextPage":true,"token":"L2"}}}}}""")
            } else {
                ok("""{"data":{"user":{"tracks":{"edges":[{"likeState":"like","node":${trackNode("Later")}}],"pageInfo":{"hasNextPage":false,"token":null}}}}}""")
            }
        }
        val tracks = AmazonMusicSource(client(http, MemoryStore(signedInSession())))
            .fetchTracks(AmazonMusicSource.COLLECTION_LIKED_TRACKS)
        assertEquals(listOf("Loved", "NoState", "Later"), tracks.map { it.title })
        assertEquals(listOf(0, 1, 2), tracks.map { it.sourcePosition })
    }

    @Test
    fun `contradictory paging fails closed instead of migrating half a list`() {
        fun reasonOf(block: () -> Unit): AmazonMusicException.Reason? =
            try { block(); null } catch (e: AmazonMusicException) { e.reason }

        assertEquals(
            AmazonMusicException.Reason.INCOMPLETE_READ,
            reasonOf { AmazonMusicWeb.nextCursor(json("""{"hasNextPage":true}"""), null, "x") },
        )
        assertEquals(
            AmazonMusicException.Reason.INCOMPLETE_READ,
            reasonOf { AmazonMusicWeb.nextCursor(json("""{"hasNextPage":true,"token":"same"}"""), "same", "x") },
        )
        assertEquals(
            AmazonMusicException.Reason.INCOMPLETE_READ,
            reasonOf {
                AmazonMusicWeb.parsePlaylistTracksPage(
                    json("""{"playlist":{"tracks":{"edges":[],"pageInfo":{"hasNextPage":true,"token":"t"}}}}"""), null, 0,
                )
            },
        )
        assertNull(AmazonMusicWeb.nextCursor(json("""{"hasNextPage":false,"token":"ignored"}"""), null, "x"))

        val cycling = FakeHttp { r ->
            when (variablesOf(r)["cursor"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content) {
                null -> ok(playlistsPage(listOf("p1"), "A"))
                "A" -> ok(playlistsPage(listOf("p2"), "B"))
                else -> ok(playlistsPage(listOf("p3"), "A"))
            }
        }
        val reason = runBlocking {
            try {
                AmazonMusicSource(client(cycling, MemoryStore(signedInSession()))).listPlaylists(null)
                null
            } catch (e: AmazonMusicException) { e.reason }
        }
        assertEquals(AmazonMusicException.Reason.INCOMPLETE_READ, reason)
    }

    @Test
    fun `an unknown response shape is a clear error, never a crash or an empty library`() {
        for (shape in listOf("""{}""", """{"user":null}""", """{"user":{"playlists":"nope"}}""")) {
            try {
                AmazonMusicWeb.parsePlaylistsPage(json(shape), null)
                fail("$shape must not parse as an empty library")
            } catch (e: AmazonMusicException) {
                assertEquals(AmazonMusicException.Reason.UNEXPECTED_RESPONSE, e.reason)
                assertFalse("the message is for the user, not a stack of JSON", e.message!!.contains("{"))
            }
        }
        val garbage = FakeHttp { ok("<html>sign in</html>") }
        val reason = runBlocking {
            try {
                AmazonMusicSource(client(garbage, MemoryStore(signedInSession()))).fetchTracks("p")
                null
            } catch (e: AmazonMusicException) { e.reason }
        }
        assertEquals(AmazonMusicException.Reason.UNEXPECTED_RESPONSE, reason)
    }

    @Test
    fun `without a stored session nothing is sent and the source asks to sign in`() = runBlocking {
        val http = FakeHttp { error("no call expected") }
        try {
            AmazonMusicSource(client(http, MemoryStore())).listPlaylists(null)
            fail("signed out must be reported")
        } catch (e: SourceError.NotAuthenticated) {
            assertEquals(SourceType.AMAZON, e.type)
        }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `the stored session survives a round trip and never prints a value`() {
        val session = signedInSession()
        val back = assertNotNullAndGet(AmazonMusicSession.fromJson(session.toJson()))
        assertEquals(session.cookies, back.cookies)
        assertEquals(session.headers, back.headers)
        assertEquals(session.expiresAtMs, back.expiresAtMs)
        assertNull("a host outside the allowlist is not trusted back", AmazonMusicSession.fromJson(session.toJson().replace("music.amazon.es", "evil.example")))
        assertNull(AmazonMusicSession.fromJson("not json"))

        val printed = session.toString() + AmazonHttpRequest("GET", "https://music.amazon.es/pandaToken?x=1", mapOf("Cookie" to "at-main=AT")).toString()
        for (secret in listOf("AT", "STOK", "AmznMusic", "cfg-token", "x=1")) {
            assertFalse("toString leaked $secret: $printed", printed.contains(secret))
        }
    }

    @Test
    fun `the Amazon source never logs`() {
        val dir = File(repoRoot(), "migration/src/main/kotlin/com/aura/migration/source/amazon")
        for (file in dir.listFiles()!!.filter { it.extension == "kt" }) {
            val text = file.readText()
            for (logger in listOf("Timber", "println", "android.util.Log", "Log.d(", "Log.i(", "Log.w(", "Log.e(", "printStackTrace")) {
                assertFalse("${file.name} uses $logger", text.contains(logger))
            }
        }
    }

    private fun repoRoot(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
