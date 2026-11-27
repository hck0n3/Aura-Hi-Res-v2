package iad1tya.echo.music.tvlink

import java.util.Base64
import org.json.JSONObject

/**
 * Row 907: what the TV says about itself (sealed, shown on the phone before anything is sent): its model and the public key
 * of the pair it made for this code ([TvLinkKeys]; X.509 bytes), which the session is sealed to.
 */
class TvLinkHello(val device: String, val publicKey: ByteArray)

/**
 * Row 907: the Google session the phone hands to the TV — exactly the three values the normal login stores
 * (`completeLogin` in ui/screens/LoginScreen.kt: InnerTubeCookieKey, VisitorDataKey, DataSyncIdKey) — plus the phone's model
 * so the TV can say where it came from. Only ever travels sealed ([TvLinkSeal]); never logged, never put in a backup.
 */
data class TvLinkSession(val cookie: String, val visitorData: String, val dataSyncId: String, val fromDevice: String) {
    override fun toString(): String = "TvLinkSession(<redacted>)"
}

object TvLinkPayload {
    private const val VERSION = 1
    private const val MAX_DEVICE = 60

    /** A device label safe to show: no control characters, at most [MAX_DEVICE] characters, never empty. */
    fun deviceLabel(raw: String?): String =
        raw.orEmpty().filter { !it.isISOControl() }.trim().take(MAX_DEVICE).ifBlank { "Android" }

    fun hello(device: String, publicKey: ByteArray): ByteArray =
        JSONObject()
            .put("v", VERSION)
            .put("device", deviceLabel(device))
            .put("pub", Base64.getEncoder().encodeToString(publicKey))
            .toString()
            .toByteArray(Charsets.UTF_8)

    /** Null unless it is a hello of this version with a key in it. */
    fun parseHello(bytes: ByteArray): TvLinkHello? = runCatching {
        val o = JSONObject(String(bytes, Charsets.UTF_8))
        if (o.optInt("v") != VERSION) return null
        val pub = Base64.getDecoder().decode(o.optString("pub"))
        if (pub.isEmpty()) return null
        TvLinkHello(deviceLabel(o.optString("device")), pub)
    }.getOrNull()

    fun session(s: TvLinkSession): ByteArray =
        JSONObject()
            .put("v", VERSION)
            .put("cookie", s.cookie)
            .put("visitorData", s.visitorData)
            .put("dataSyncId", s.dataSyncId)
            .put("from", deviceLabel(s.fromDevice))
            .toString()
            .toByteArray(Charsets.UTF_8)

    /** Null unless it is a session of this version with a cookie in it. */
    fun parseSession(bytes: ByteArray): TvLinkSession? = runCatching {
        val o = JSONObject(String(bytes, Charsets.UTF_8))
        if (o.optInt("v") != VERSION) return null
        val cookie = o.optString("cookie")
        if (cookie.isBlank()) return null
        TvLinkSession(cookie, o.optString("visitorData"), o.optString("dataSyncId"), deviceLabel(o.optString("from")))
    }.getOrNull()
}
