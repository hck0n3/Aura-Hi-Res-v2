package iad1tya.echo.music.playback

import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Registry row 449 (owner 2026-10-19, Cast): the TV got a video URL that only works with the phone's per-client
 * User-Agent and stayed black. The relay lets the receiver fetch through the phone, which replays that User-Agent.
 *
 * The upstream here is a real HTTP server on loopback that answers 403 to any request whose User-Agent is not the
 * «client» one (the googlevideo behaviour the repo documents in MusicService.videoOkHttpClient) and serves Range
 * requests; the relay is the real class; the «receiver» is a plain HttpURLConnection with its own User-Agent.
 */
class CastMediaRelayTest {
    private val clientUserAgent = "client-ua/1.0"
    private val file = ByteArray(100_000) { (it * 31 + 7).toByte() }

    private lateinit var upstream: HttpServer
    private lateinit var relay: CastMediaRelay
    private val loopback = InetAddress.getByName("127.0.0.1")

    @Before
    fun setUp() {
        upstream = HttpServer.create(InetSocketAddress(loopback, 0), 0)
        upstream.createContext("/video") { exchange ->
            val ua = exchange.requestHeaders.getFirst("User-Agent")
            if (ua != clientUserAgent) {
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
                return@createContext
            }
            exchange.responseHeaders.add("Content-Type", "video/mp4")
            val range = exchange.requestHeaders.getFirst("Range")
            val match = range?.let { Regex("bytes=(\\d+)-(\\d*)").matchEntire(it) }
            if (match == null) {
                exchange.sendResponseHeaders(200, file.size.toLong())
                exchange.responseBody.use { it.write(file) }
            } else {
                val from = match.groupValues[1].toInt()
                val to = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()?.coerceAtMost(file.size - 1) ?: (file.size - 1)
                if (from >= file.size) {
                    exchange.sendResponseHeaders(416, -1)
                } else {
                    exchange.responseHeaders.add("Content-Range", "bytes $from-$to/${file.size}")
                    exchange.sendResponseHeaders(206, (to - from + 1).toLong())
                    exchange.responseBody.use { it.write(file, from, to - from + 1) }
                }
            }
            exchange.close()
        }
        upstream.start()
        val phoneClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", clientUserAgent).build())
            }
            .build()
        relay = CastMediaRelay(phoneClient, loopback)
    }

    @After
    fun tearDown() {
        relay.close()
        upstream.stop(0)
    }

    private val upstreamUrl get() = "http://127.0.0.1:${upstream.address.port}/video"

    private fun receiverGet(url: String, range: String? = null, method: String = "GET"): Triple<Int, Map<String, String>, ByteArray> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("User-Agent", CastVideoProbe.RECEIVER_USER_AGENT)
        if (range != null) connection.setRequestProperty("Range", range)
        val code = connection.responseCode
        val headers = connection.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.first() }
        val stream = if (method == "HEAD") null else if (code >= 400) connection.errorStream else connection.inputStream
        val body = stream?.use { it.readBytes() } ?: ByteArray(0)
        return Triple(code, headers, body)
    }

    @Test
    fun `the upstream refuses the receiver's own user agent, which is the premise of the relay`() {
        val (code, _, _) = receiverGet(upstreamUrl)
        assertEquals(403, code)
    }

    @Test
    fun `a ranged request through the relay returns exactly those bytes with the right headers`() {
        val url = relay.publish(upstreamUrl)
        assertNotNull(url)
        val (code, headers, body) = receiverGet(url!!, range = "bytes=100-199")
        assertEquals(206, code)
        assertEquals("bytes 100-199/${file.size}", headers["content-range"])
        assertEquals("100", headers["content-length"])
        assertEquals("video/mp4", headers["content-type"])
        assertEquals("bytes", headers["accept-ranges"])
        assertEquals("*", headers["access-control-allow-origin"])
        assertArrayEquals(file.copyOfRange(100, 200), body)
    }

    @Test
    fun `an open-ended range streams to the end of the file`() {
        val url = relay.publish(upstreamUrl)!!
        val (code, headers, body) = receiverGet(url, range = "bytes=99000-")
        assertEquals(206, code)
        assertEquals("bytes 99000-99999/${file.size}", headers["content-range"])
        assertArrayEquals(file.copyOfRange(99_000, file.size), body)
    }

    @Test
    fun `a request without Range gets the whole file as a plain 200`() {
        val url = relay.publish(upstreamUrl)!!
        val (code, headers, body) = receiverGet(url)
        assertEquals(200, code)
        assertNull("a 200 carries no Content-Range", headers["content-range"])
        assertEquals(file.size.toString(), headers["content-length"])
        assertArrayEquals(file, body)
    }

    @Test
    fun `HEAD reports the total size without a body`() {
        val url = relay.publish(upstreamUrl)!!
        val (code, headers, body) = receiverGet(url, method = "HEAD")
        assertEquals(200, code)
        assertEquals(file.size.toString(), headers["content-length"])
        assertEquals(0, body.size)
    }

    @Test
    fun `a range past the end is a 416 and an unknown path is a 404`() {
        val url = relay.publish(upstreamUrl)!!
        assertEquals(416, receiverGet(url, range = "bytes=200000-").first)
        assertEquals(404, receiverGet(url.substringBeforeLast('/') + "/not-the-token.mp4").first)
    }

    @Test
    fun `publishing again issues a new token and the old URL stops working`() {
        val first = relay.publish(upstreamUrl)!!
        val second = relay.publish(upstreamUrl)!!
        assertNotEquals(first, second)
        assertEquals(404, receiverGet(first, range = "bytes=0-9").first)
        assertEquals(206, receiverGet(second, range = "bytes=0-9").first)
    }

    @Test
    fun `an upstream that refuses is reported as a bad gateway, not as video`() {
        val refusing = CastMediaRelay(OkHttpClient(), loopback) // no client User-Agent -> upstream answers 403
        try {
            val url = refusing.publish(upstreamUrl)!!
            assertEquals(502, receiverGet(url, range = "bytes=0-9").first)
        } finally {
            refusing.close()
        }
    }

    @Test
    fun `a closed relay publishes nothing`() {
        relay.close()
        assertNull(relay.publish(upstreamUrl))
    }

    @Test
    fun `the probe tells a URL that serves video from one that does not`() {
        val withUa = OkHttpClient()
        val ok = CastVideoProbe.probe(withUa, upstreamUrl, clientUserAgent)
        assertTrue("status=${ok.status} type=${ok.contentType}", ok.playable)
        val receiver = CastVideoProbe.probe(withUa, upstreamUrl, CastVideoProbe.RECEIVER_USER_AGENT)
        assertEquals(403, receiver.status)
        assertFalse(receiver.playable)
        val unreachable = CastVideoProbe.probe(withUa, "http://127.0.0.1:1/none", null)
        assertEquals(-1, unreachable.status)
        assertFalse(unreachable.playable)
    }
}
