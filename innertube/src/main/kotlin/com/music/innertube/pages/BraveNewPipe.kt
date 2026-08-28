package com.music.innertube

import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

// BravePipeExtractor (maxrave-dev/BravePipeExtractor) — the LAST-RESORT fallback layer of
// NewPipeExtractor.newPipePlayer, and the SAME fallback SimpMusic uses in its Extractor.android.kt
// when its PipePipe fork fails. Fork of TeamNewPipe keeping the org.schabi.* package tree, but its
// extraction client is ANDROID (ReelPlayer/player endpoints) instead of WEB, so it is not limited to
// the muxed itag 18 that stock TeamNewPipe v0.25.2 got behind the bot wall. It replaced stock
// TeamNewPipe v0.25.2 on 2026-08-23, after that dependency's anonymous path started returning zero
// streams on the owner's device/IP (same package tree, duplicate classes, cannot coexist). Lives in
// its own file so it stays isolated from the dev.maxrave.pipepipe fork.
class BraveNewPipeDownloaderImpl(
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
                // Host only — never the full URI, whose query string carries the credentials.
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
            // HALLAZGO-019 resto (2026-08-25): mismo criterio que NewPipeDownloaderImpl — extracción
            // acotada antes del stream; sin callTimeout por el player JS (~2MB).
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder =
            okhttp3.Request
                .Builder()
                .method(httpMethod, dataToSend?.toRequestBody())
                .url(url)
                .addHeader("User-Agent", YouTubeClient.USER_AGENT_WEB)

        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBodyToReturn = response.body.string()
        val latestUrl = response.request.url.toString()
        return Response(response.code, response.message, response.headers.toMultimap(), responseBodyToReturn, latestUrl)
    }
}

class BraveNewPipeUtils(
    downloader: Downloader,
) {
    init {
        NewPipe.init(downloader)
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
            timber.log.Timber.tag("RESOLVE_CIPHER").e(
                "braveNewPipe deobfuscation failed videoId=$videoId itag=${format.itag}: ${e.javaClass.simpleName}: ${e.message}"
            )
            null
        }
}

object BraveNewPipeExtractor {
    private var braveNewPipeDownloader: BraveNewPipeDownloaderImpl? = null
    private var braveNewPipeUtils: BraveNewPipeUtils? = null
    private var isInitialized = false

    @Synchronized
    fun init() {
        if (!isInitialized) {
            braveNewPipeDownloader = BraveNewPipeDownloaderImpl(
                proxy = YouTube.proxy,
                proxyAuth = YouTube.proxyAuth
            )
            braveNewPipeUtils = BraveNewPipeUtils(braveNewPipeDownloader!!)
            isInitialized = true
        }
    }

    fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        init()
        return braveNewPipeUtils?.getStreamUrl(format, videoId)
    }

    fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        init()
        return try {
            val streamInfo = StreamInfo.getInfo(
                NewPipe.getService(0),
                "https://www.youtube.com/watch?v=$videoId"
            )
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            streamsList.mapNotNull {
                (it.itagItem?.id ?: return@mapNotNull null) to it.content
            }
        } catch (e: Exception) {
            // The fallback of the fallback: when even bot-limited itag 18 cannot be extracted, name the
            // failure so the shared app.log carries evidence instead of a generic "song unavailable".
            timber.log.Timber.tag("RESOLVE_CIPHER").e(
                "braveNewPipePlayer failed videoId=$videoId: ${e.javaClass.simpleName}: ${e.message}"
            )
            emptyList()
        }
    }
}
