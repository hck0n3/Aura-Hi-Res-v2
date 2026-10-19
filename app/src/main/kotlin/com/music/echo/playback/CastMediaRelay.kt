package iad1tya.echo.music.playback

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What the Cast receiver would get if it fetched a video URL itself (see [CastVideoProbe.probe]).
 * [status] is the HTTP status, or -1 when the request never completed.
 */
internal data class CastVideoProbeResult(
    val status: Int,
    val contentType: String?,
    val bytesRead: Int,
    val elapsedMs: Long,
) {
    /** A 200/206 that carries video bytes: the only answer a receiver can turn into a picture. */
    val playable: Boolean
        get() = (status == 200 || status == 206) &&
            contentType?.startsWith("video/", ignoreCase = true) == true &&
            bytesRead > 0
}

/**
 * A ranged GET of the first bytes of a video URL, to learn BEFORE the TV is told to play it whether the
 * URL answers with video at all.
 *
 * Why it exists (registry row 449): googlevideo URLs are bound to the client that asked for them and 403 when
 * fetched with another User-Agent (MusicService.videoOkHttpClient exists for exactly that reason: it replays the
 * per-client UA by the `c=` query parameter). The Cast receiver cannot do that: it fetches the URL itself, with
 * its own Chromecast User-Agent. A muxed URL that only works with the phone's UA reached the TV, failed there, and
 * the receiver skipped on to the next audio item («se queda en negro… luego cambia de canción»).
 *
 * Never logs the URL (AGENTS.md rule 4): only numbers and the content type.
 */
internal object CastVideoProbe {
    /** A Chromecast's own User-Agent (Chromium on Linux with the CrKey token), as the receiver would send it. */
    const val RECEIVER_USER_AGENT =
        "Mozilla/5.0 (X11; Linux armv7l) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/114.0.0.0 Safari/537.36 CrKey/1.56.500000"

    private const val DEFAULT_PROBE_BYTES = 64 * 1024

    /**
     * Fetches up to [maxBytes] of [url]. With [userAgent] null the [client]'s own interceptors decide the
     * User-Agent (the phone's way of fetching it); otherwise exactly [userAgent] is sent.
     */
    fun probe(
        client: OkHttpClient,
        url: String,
        userAgent: String?,
        maxBytes: Int = DEFAULT_PROBE_BYTES,
    ): CastVideoProbeResult {
        val started = System.nanoTime()
        fun elapsed() = (System.nanoTime() - started) / 1_000_000L
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${maxBytes - 1}")
                .apply { if (userAgent != null) header("User-Agent", userAgent) }
                .build()
            client.newCall(request).execute().use { response ->
                var read = 0
                if (response.isSuccessful) {
                    val buffer = ByteArray(8 * 1024)
                    val body = response.body.byteStream()
                    while (read < maxBytes) {
                        val n = body.read(buffer, 0, minOf(buffer.size, maxBytes - read))
                        if (n < 0) break
                        read += n
                    }
                }
                CastVideoProbeResult(response.code, response.header("Content-Type"), read, elapsed())
            }
        } catch (e: IOException) {
            CastVideoProbeResult(-1, null, 0, elapsed())
        } catch (e: IllegalArgumentException) {
            CastVideoProbeResult(-1, null, 0, elapsed())
        }
    }
}

/**
 * A tiny HTTP relay on the phone's LAN address, so the Cast receiver can play a video URL it could not fetch
 * itself (registry row 449).
 *
 * The receiver asks the phone (`http://<phone>:<port>/<token>.mp4`, with Range requests as a `<video>` element
 * makes them); the phone fetches the real URL with [upstream] — the client that replays the right per-client
 * User-Agent — and pipes the bytes back. Only used when [CastVideoProbe] showed the receiver's own fetch fails
 * while the phone's succeeds, so a video that already plays direct never goes through it.
 *
 * Scope and safety: bound to ONE LAN address (never 0.0.0.0), serves ONE registered upstream behind a random
 * 128-bit token, GET/HEAD/OPTIONS only, closed with the Cast session. It moves bytes only while the TV plays.
 */
internal class CastMediaRelay(
    private val upstream: OkHttpClient,
    val bindAddress: InetAddress,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val random = SecureRandom()
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "cast-relay").apply { isDaemon = true }
    }
    private val connections: MutableSet<Socket> = Collections.newSetFromMap(ConcurrentHashMap())
    private val activeCalls: MutableSet<okhttp3.Call> = Collections.newSetFromMap(ConcurrentHashMap())

    @Volatile private var server: ServerSocket? = null
    @Volatile private var token: String = ""
    @Volatile private var target: String? = null

    /**
     * Registers [upstreamUrl] and returns the URL the receiver must be given, or null when the relay cannot listen.
     * A new token per call: a URL handed out earlier stops working.
     */
    @Synchronized
    fun publish(upstreamUrl: String): String? {
        if (closed.get()) return null
        val socket = server ?: try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(bindAddress, 0), 8)
            }.also {
                server = it
                workers.execute { acceptLoop(it) }
            }
        } catch (e: IOException) {
            return null
        }
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        token = bytes.joinToString("") { "%02x".format(it) }
        target = upstreamUrl
        return "http://${bindAddress.hostAddress}:${socket.localPort}/$token.mp4"
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!closed.get()) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                return
            }
            connections.add(client)
            try {
                workers.execute { serve(client) }
            } catch (e: java.util.concurrent.RejectedExecutionException) {
                runCatching { client.close() }
                return
            }
        }
    }

    private fun serve(client: Socket) {
        try {
            client.soTimeout = 30_000
            client.tcpNoDelay = true
            val request = readRequest(client.getInputStream()) ?: return
            handle(request, client.getOutputStream())
        } catch (e: IOException) {
            // The receiver closed the connection (it does that on every seek): nothing to report.
        } finally {
            connections.remove(client)
            runCatching { client.close() }
        }
    }

    private class ParsedRequest(val method: String, val path: String, val range: String?)

    private fun readRequest(input: InputStream): ParsedRequest? {
        val head = StringBuilder()
        var matched = 0
        while (head.length < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b < 0) return null
            head.append(b.toChar())
            matched = if (b == "\r\n\r\n"[matched].code) matched + 1 else if (b == '\r'.code) 1 else 0
            if (matched == 4) break
        }
        if (matched != 4) return null
        val lines = head.toString().split("\r\n")
        val requestLine = lines.firstOrNull()?.split(" ") ?: return null
        if (requestLine.size < 2) return null
        var range: String? = null
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon > 0 && line.substring(0, colon).trim().equals("Range", ignoreCase = true)) {
                range = line.substring(colon + 1).trim()
            }
        }
        return ParsedRequest(requestLine[0].uppercase(), requestLine[1].substringBefore('?'), range)
    }

    private fun handle(request: ParsedRequest, out: OutputStream) {
        if (request.method == "OPTIONS") {
            respond(out, 204, "No Content", emptyMap(), null)
            return
        }
        if (request.method != "GET" && request.method != "HEAD") {
            respond(out, 405, "Method Not Allowed", emptyMap(), null)
            return
        }
        val url = target
        if (url == null || request.path != "/$token.mp4") {
            respond(out, 404, "Not Found", emptyMap(), null)
            return
        }
        // HEAD is answered from a one-byte ranged GET: googlevideo edges often refuse a bare HEAD.
        val head = request.method == "HEAD"
        val upstreamRequest = try {
            Request.Builder().url(url).header("Range", if (head) "bytes=0-0" else request.range ?: "bytes=0-").build()
        } catch (e: IllegalArgumentException) {
            respond(out, 502, "Bad Gateway", emptyMap(), null)
            return
        }
        val call = upstream.newCall(upstreamRequest)
        activeCalls.add(call)
        try {
            call.execute().use { response ->
                val contentType = response.header("Content-Type")?.takeIf { it.startsWith("video/", true) } ?: "video/mp4"
                val headers = linkedMapOf("Content-Type" to contentType)
                if (!response.isSuccessful) {
                    // 416 (range past the end) travels as such; every other upstream refusal is a bad gateway.
                    val code = if (response.code == 416) 416 else 502
                    respond(out, code, if (code == 416) "Range Not Satisfiable" else "Bad Gateway", headers, null)
                    return
                }
                val contentRange = response.header("Content-Range")
                val total = contentRange?.substringAfterLast('/')?.toLongOrNull()
                    ?: if (response.code == 200) response.header("Content-Length")?.toLongOrNull() else null
                if (head) {
                    total?.let { headers["Content-Length"] = it.toString() }
                    respond(out, 200, "OK", headers, null)
                    return
                }
                response.header("Content-Length")?.let { headers["Content-Length"] = it }
                // A request without Range gets the whole file as a plain 200, whatever the upstream replied to our «bytes=0-».
                val partial = response.code == 206 && request.range != null
                if (partial) contentRange?.let { headers["Content-Range"] = it }
                respond(out, if (partial) 206 else 200, if (partial) "Partial Content" else "OK", headers, response.body.byteStream())
            }
        } finally {
            activeCalls.remove(call)
        }
    }

    private fun respond(out: OutputStream, code: Int, text: String, headers: Map<String, String>, body: InputStream?) {
        val head = StringBuilder("HTTP/1.1 $code $text\r\n")
        headers.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
        head.append("Accept-Ranges: bytes\r\n")
        head.append("Access-Control-Allow-Origin: *\r\n")
        head.append("Access-Control-Allow-Headers: Range\r\n")
        head.append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
        head.append("Cache-Control: no-store\r\n")
        if (body == null && !headers.containsKey("Content-Length")) head.append("Content-Length: 0\r\n")
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        if (body != null) {
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val n = body.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
        }
        out.flush()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server?.close() }
        activeCalls.forEach { runCatching { it.cancel() } }
        connections.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }

    private companion object {
        const val MAX_HEADER_BYTES = 16 * 1024
    }
}
