package com.music.innertube

import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import dev.maxrave.pipepipe.extractor.NewPipe
import dev.maxrave.pipepipe.extractor.ServiceList
import dev.maxrave.pipepipe.extractor.downloader.CancellableCall
import dev.maxrave.pipepipe.extractor.downloader.Downloader
import dev.maxrave.pipepipe.extractor.downloader.Request
import dev.maxrave.pipepipe.extractor.downloader.Response
import dev.maxrave.pipepipe.extractor.exceptions.ParsingException
import dev.maxrave.pipepipe.extractor.exceptions.ReCaptchaException
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import dev.maxrave.pipepipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

// PipePipeExtractor is SimpMusic's own NewPipeExtractor fork (maxrave-dev). Same stream pipeline as
// SimpMusic: extraction runs through the ANDROID_VR InnerTube client, which YouTube's bot-check has
// NOT burned, so it returns the FULL adaptive format set (video-only 137/136/134…, audio 250/251/774)
// instead of the bot-limited muxed itag 18 that stock TeamNewPipe v0.25.2 gets. Setting the service
// tokens to the user's YouTube cookie additionally enables the fork's supplementary WEB_REMIX call
// (Premium itags 141/774); anonymous (empty tokens) still works and is the default.
class NewPipeDownloaderImpl(
    proxy: Proxy?,
    proxyAuth: String? = null,
) : Downloader() {
    private val client =
        OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val addresses = Dns.SYSTEM.lookup(hostname)
                    return when (YouTube.ipVersion) {
                        IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                        IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                        IpVersion.AUTO -> addresses
                    }
                }
            })
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)
                // Was an empty body, so a proxy user got ZERO signal: every NewPipe deobfuscation
                // request failed invisibly and the report read as "nothing plays". Host only —
                // never the full URI, whose query string carries the credentials.
                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                    timber.log.Timber.tag("RESOLVE_CIPHER").w(
                        "proxy connect failed host=${uri?.host}: ${ioe?.javaClass?.simpleName}: ${ioe?.message}"
                    )
                }
            })
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            // HALLAZGO-019 resto (2026-08-25): la extracción (llamadas InnerTube + player JS para
            // deobfuscación) son fetches acotados que corren ANTES del stream. Sin topes, una
            // extracción colgada dejaba al usuario con el spinner para siempre; con topes la app
            // falla rápido y la capa de fallback puede intentar el siguiente camino. Sin callTimeout
            // a propósito: el player JS pesa ~2MB y el readTimeout topa stalls, no descargas lentas
            // legítimas (mismo criterio que PlayerJsFetcher).
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val response = client.newCall(buildOkHttpRequest(request)).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", request.url())
        }

        return response.toNewPipeResponse()
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun executeAsync(
        request: Request,
        callback: AsyncCallback?,
    ): CancellableCall {
        val call = client.newCall(buildOkHttpRequest(request))
        val cancellable = CancellableCall(call)
        call.enqueue(
            object : okhttp3.Callback {
                override fun onFailure(
                    call: okhttp3.Call,
                    e: IOException,
                ) {
                    cancellable.setFinished()
                    callback?.onError(e)
                }

                override fun onResponse(
                    call: okhttp3.Call,
                    response: okhttp3.Response,
                ) {
                    try {
                        if (response.code == 429) {
                            response.close()
                            callback?.onError(
                                ReCaptchaException("reCaptcha Challenge requested", request.url()),
                            )
                            return
                        }
                        callback?.onSuccess(response.toNewPipeResponse())
                    } catch (e: Exception) {
                        callback?.onError(e)
                    } finally {
                        cancellable.setFinished()
                    }
                }
            },
        )
        return cancellable
    }

    // The fork's Response carries the raw body bytes too (6-arg constructor) — stock TeamNewPipe's
    // 5-arg one does not exist here.
    private fun okhttp3.Response.toNewPipeResponse(): Response {
        val rawBytes = body?.bytes() ?: ByteArray(0)
        return Response(
            code,
            message,
            headers.toMultimap(),
            rawBytes.toString(Charsets.UTF_8),
            rawBytes,
            request.url.toString(),
        )
    }

    private fun buildOkHttpRequest(request: Request): okhttp3.Request {
        val builder =
            okhttp3.Request
                .Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())
                .addHeader("User-Agent", YouTubeClient.USER_AGENT_WEB)

        request.headers().forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                builder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    builder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                builder.header(headerName, headerValueList[0])
            }
        }
        return builder.build()
    }
}

class NewPipeUtils(
    downloader: Downloader,
) {
    init {
        NewPipe.init(downloader)
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> =
        runCatching {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
        }

    fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
    ): String? =
        try {
            val url =
                format.url ?: format.signatureCipher?.let { signatureCipher ->
                    val params = parseQueryString(signatureCipher)
                    val obfuscatedSignature =
                        params["s"]
                            ?: throw ParsingException("Could not parse cipher signature")
                    val signatureParam =
                        params["sp"]
                            ?: throw ParsingException("Could not parse cipher signature parameter")
                    val url =
                        params["url"]?.let { URLBuilder(it) }
                            ?: throw ParsingException("Could not parse cipher url")
                    url.parameters[signatureParam] =
                        YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                            videoId,
                            obfuscatedSignature,
                        )
                    url.toString()
                } ?: throw ParsingException("Could not find format url")

            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId,
                url,
            )
        } catch (e: Exception) {
            // THE cipher-rotation blind spot. "Caller handles errors" was true only in the sense that
            // the caller turned this null into a generic "no stream URL" — so when YouTube rotates
            // player.js and signature/throttling deobfuscation starts failing for EVERY user at once,
            // the shared log said nothing more than "song unavailable". Naming the exception class and
            // message is what separates "YouTube rotated the cipher, publish a player_configs.json"
            // from "this one song is region-locked". ERROR so AppLogger persists it; itag and videoId
            // only — no URL, which would carry the credentialed query string.
            timber.log.Timber.tag("RESOLVE_CIPHER").e(
                "deobfuscation failed videoId=$videoId itag=${format.itag}: ${e.javaClass.simpleName}: ${e.message}"
            )
            null
        }
}

object NewPipeExtractor {
    private var newPipeDownloader: NewPipeDownloaderImpl? = null
    private var newPipeUtils: NewPipeUtils? = null
    private var isInitialized = false

    @Synchronized
    fun init() {
        if (!isInitialized) {
            newPipeDownloader = NewPipeDownloaderImpl(
                proxy = YouTube.proxy,
                proxyAuth = YouTube.proxyAuth
            )
            newPipeUtils = NewPipeUtils(newPipeDownloader!!)
            isInitialized = true
        }
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> {
        init()
        return newPipeUtils?.getSignatureTimestamp(videoId)
            ?: Result.failure(Exception("NewPipeUtils not initialized"))
    }

    fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        init()
        return newPipeUtils?.getStreamUrl(format, videoId)
    }

    /**
     * Feeds the user's YouTube cookie to the fork (SimpMusic does exactly this in its logIn). With a
     * cookie the fork adds a supplementary WEB_REMIX extraction call (Premium itags 141/774); null or
     * empty keeps fully-working anonymous extraction. Called from YouTube.cookie's setter, so login and
     * app-startup restore both reach it with zero extra wiring.
     *
     * TEMPORARY EMERGENCY (2026-08-23): extraction is forced ANONYMOUS. On-device evidence: once the
     * owner logged in, every fork call failed with "android_vr player response is not valid" and
     * returned itags=[], which dropped resolution onto the burned InnerTube direct URLs (206 probe,
     * 403 real fetch) and nothing played. Anonymous extraction still returns the full adaptive set;
     * restore the cookie here once the fork's authenticated-call requirements are confirmed.
     */
    @Synchronized
    fun setTokens(cookie: String?) {
        init()
        ServiceList.YouTube.tokens = ""
    }

    fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        init()
        val pipePipeStreams = try {
            // music.youtube.com, same entry URL SimpMusic uses with the fork.
            val streamInfo = StreamInfo.getInfo(
                ServiceList.YouTube,
                "https://music.youtube.com/watch?v=$videoId"
            )
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            streamsList.mapNotNull {
                (it.itagItem?.id ?: return@mapNotNull null) to it.content
            }
        } catch (e: Exception) {
            // The LAST-RESORT stream source. When this returns empty the song is skipped as NO_STREAM,
            // so a silent failure here is the final step of "no reproduce" — and the one place that
            // could still have explained why.
            timber.log.Timber.tag("RESOLVE_CIPHER").e(
                "newPipePlayer failed videoId=$videoId: ${e.javaClass.simpleName}: ${e.message}"
            )
            emptyList()
        }
        if (pipePipeStreams.isNotEmpty()) return pipePipeStreams

        // 2026-08-23: PipePipe's extraction can be fully blocked on a device (anti-bot "sign in" wall
        // when anonymous, "android_vr player response is not valid" with cookie), and the stock
        // TeamNewPipe v0.25.2 fallback that kept the app playing started returning zero streams on the
        // same device/IP. Returning empty here drops resolution onto the burned InnerTube direct URLs
        // (206 probe, 403 real fetch) and nothing plays — so fall back to BraveNewPipe (SimpMusic's own
        // fallback, ANDROID extraction client) before giving up. Remove once PipePipe's authenticated
        // extraction is fixed.
        val fallback = BraveNewPipeExtractor.newPipePlayer(videoId)
        if (fallback.isNotEmpty()) {
            timber.log.Timber.tag("RESOLVE_CIPHER").w(
                "PipePipe extraction empty videoId=$videoId; BraveNewPipe fallback returned ${fallback.size} streams"
            )
        }
        return fallback
    }
}
