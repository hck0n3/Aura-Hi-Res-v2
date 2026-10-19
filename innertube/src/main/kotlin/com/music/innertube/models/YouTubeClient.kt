package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val buildId: String? = null,
    val cronetVersion: String? = null,
    val packageName: String? = null,
    val friendlyName: String? = null,
    val loginSupported: Boolean = false,
    val loginRequired: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val isEmbedded: Boolean = false,
    val useWebPoTokens: Boolean = false,
    // Send /player to www.youtube.com (with the www origin for SAPISIDHASH) instead of music.youtube.com: the clients yt-dlp
    // uses for age-gated videos (web_embedded, tv) are served by the www host.
    val useWwwHost: Boolean = false,
    // The page the embedded player claims to sit in (thirdParty.embedUrl). null = the video's own watch URL.
    val embedUrl: String? = null,
) {
    fun toContext(locale: YouTubeLocale, visitorData: String?, dataSyncId: String?) = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            osName = osName,
            osVersion = osVersion,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            androidSdkVersion = androidSdkVersion,
            gl = locale.gl,
            hl = locale.hl,
            visitorData = visitorData
        ),
        user = Context.User(
            onBehalfOfUser = if (loginSupported) dataSyncId else null
        ),
    )

    companion object {
        const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/"
        const val API_URL_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/youtubei/v1/"

        const val ORIGIN_YOUTUBE_WWW = "https://www.youtube.com"
        const val REFERER_YOUTUBE_WWW = "$ORIGIN_YOUTUBE_WWW/"
        const val API_URL_YOUTUBE_WWW = "$ORIGIN_YOUTUBE_WWW/youtubei/v1/"

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientVersion = "2.20260213.00.00",
            clientId = "1",
            userAgent = USER_AGENT_WEB,
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260213.01.00",
            clientId = "67",
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val WEB_CREATOR = YouTubeClient(
            clientName = "WEB_CREATOR",
            clientVersion = "1.20260213.00.00",
            clientId = "62",
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            // yt-dlp's client table marks web_creator's GVS PO token as required (not_required_with_player_token=False).
            useWebPoTokens = true,
        )

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "7.20260213.00.00",
            clientId = "7",
            userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        /**
         * Embedded player that can bypass age-restriction.
         * Does not require login for age-restricted content.
         */
        val TVHTML5_SIMPLY_EMBEDDED_PLAYER = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientId = "85",
            userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
            loginSupported = true,
            loginRequired = false,
            useSignatureTimestamp = true,
            isEmbedded = true,
        )

        /**
         * The embedded web player — the client yt-dlp (master, 2026) appends first for an age-gated video and lists among
         * its default clients for a signed-in session (`web_embedded`: WEB_EMBEDDED_PLAYER, id 56, supports cookies, no
         * GVS PO token policy of its own). The embed page may be ANY non-YouTube URL (yt-dlp: "Can be any valid non-YouTube
         * URL"). User agent = [USER_AGENT_WEB] on purpose: the byte fetch picks its User-Agent from the `c=` of the URL
         * (c=WEB_EMBEDDED_PLAYER → USER_AGENT_WEB in MusicService / RingtoneHelper), so mint and fetch must agree.
         */
        val WEB_EMBEDDED_PLAYER = YouTubeClient(
            clientName = "WEB_EMBEDDED_PLAYER",
            clientVersion = "2.20260708.00.00",
            clientId = "56",
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            useSignatureTimestamp = true,
            isEmbedded = true,
            useWwwHost = true,
            embedUrl = "https://www.reddit.com/",
        )

        /**
         * yt-dlp's `tv_samsung` (TVHTML5 5.2026…, Samsung Tizen): in its default list for a signed-in session, supports
         * cookies, no PO token needed for the stream. Same User-Agent as [TVHTML5] because the byte fetch maps c=TVHTML5 to
         * that one agent (MusicService); only the request context differs.
         */
        val TVHTML5_SAMSUNG = YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "5.20260707",
            clientId = "7",
            userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
            osName = "Tizen",
            osVersion = "2.4.0",
            deviceMake = "Samsung",
            deviceModel = "UKS9800",
            friendlyName = "TV Samsung",
            loginSupported = true,
            useSignatureTimestamp = true,
            useWwwHost = true,
        )

        /**
         * Rebuilt 2026-08-23 from the WORKING SimpMusic v1.7.0 APK dex strings: the old 21.03.1
         * persona 403'd on every HEAD validation even with a poToken present — burned alongside
         * the rest of the Echo Music inheritance. SimpMusic plays fine with IOS 19.45.4 on
         * iPhone16,2 / iOS 18.1; copied verbatim.
         */
        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "19.45.4",
            clientId = "5",
            userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
            osVersion = "18.1.0",
        )

        val MOBILE = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "21.03.38",
            clientId = "3",
            userAgent = "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip",
            loginSupported = true,
            useSignatureTimestamp = true
        )

        /**
         * Video not playable: Paid / Movie / Private / Age-restricted.
         * Note: The 'Authorization' key must be excluded from the header.
         * For some reason, PoToken is not required.
         */
        val ANDROID_NO_SDK = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "21.03.38",
            clientId = "3",
            userAgent = "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip",
            friendlyName = "Android No SDK",
            loginSupported = false,
            useSignatureTimestamp = false
        )

        /**
         * Rebuilt 2026-08-23 from the WORKING SimpMusic v1.7.0 APK on the owner's device (same
         * network/account): the old 1.43.32 / 1.61.48 "Quest 3" + Cronet personas were inherited
         * from Echo Music — an app YouTube banned — and are burned server-side (owner's logcat:
         * LOGIN_REQUIRED "confirm you're not a bot" on every resolve). SimpMusic plays fine today
         * impersonating ANDROID_VR 1.65.10 as `eureka-user` on Android 12L with a plain gzip UA —
         * NO Cronet — so that exact fingerprint replaces ours. UA and context fields below are
         * copied VERBATIM from its dex strings, not guessed.
         * Like its predecessors: uses non-adaptive bitrate (fixes audio stuttering with YT Music)
         * and does not use AV1. Video not playable: Kids / Paid / Movie / Private / Age-restricted;
         * only usable logged out.
         */
        val ANDROID_VR_1_65_10 = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "eureka-user",
            androidSdkVersion = "32",
            buildId = "SQ3A.220605.009.A1",
            packageName = "com.google.android.apps.youtube.vr.oculus",
            friendlyName = "Android VR 1.65",
            loginSupported = false,
            useSignatureTimestamp = false
        )

        /**
         * Alias kept so the existing call sites (fallback list, plus the googlevideo UA replay in
         * MusicService / RingtoneHelper / SongPreviewController / CanvasArtworkPlayer) keep
         * compiling while now downloading with the same unburned 1.65.10 UA that resolved the URL.
         */
        val ANDROID_VR_NO_AUTH = ANDROID_VR_1_65_10

        /**
         * Cannot play livestreams and lacks HDR, but can play videos with music and labeled "for children".
         */
        val ANDROID_CREATOR = YouTubeClient(
            clientName = "ANDROID_CREATOR",
            clientVersion = "25.03.101",
            clientId = "14",
            userAgent = "com.google.android.apps.youtube.creator/25.03.101 (Linux; U; Android 15; en_US; Pixel 9 Pro Fold; Build/AP3A.241005.015.A2; Cronet/132.0.6779.0)",
            osName = "Android",
            osVersion = "15",
            deviceMake = "Google",
            deviceModel = "Pixel 9 Pro Fold",
            androidSdkVersion = "35",
            buildId = "AP3A.241005.015.A2",
            cronetVersion = "132.0.6779.0",
            packageName = "com.google.android.apps.youtube.creator",
            friendlyName = "Android Studio",
            loginSupported = true,
            useSignatureTimestamp = true
        )

        /**
         * Internal YT client for an unreleased YT client. May stop working at any time.
         */
        val VISIONOS = YouTubeClient(
            clientName = "VISIONOS",
            clientVersion = "0.1",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            osName = "visionOS",
            osVersion = "1.3.21O771",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1",
            friendlyName = "visionOS",
            loginSupported = false,
            useSignatureTimestamp = false
        )

        /**
         * The device machine id for the iPad 6th Gen (iPad7,6).
         * AV1 hardware decoding is not supported.
         */
        val IPADOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "21.03.3",
            clientId = "5",
            userAgent = "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)",
            osName = "iPadOS",
            osVersion = "17.7.10.21H450",
            deviceMake = "Apple",
            deviceModel = "iPad7,6",
            friendlyName = "iPadOS",
            loginSupported = false,
            useSignatureTimestamp = false,
            packageName = "com.google.ios.youtube"
        )
    }
}
