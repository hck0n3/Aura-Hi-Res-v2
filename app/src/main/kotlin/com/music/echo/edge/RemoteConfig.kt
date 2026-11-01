package iad1tya.echo.music.edge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** One dismissible card, shown once per [id]. */
data class EdgeNotice(val id: String, val title: String, val text: String, val untilMs: Long)

/**
 * What the Worker may tell the app (function 1 of docs/PLAN_WORKER_9_FUNCIONES.md): switch a function that already exists in the
 * app on or off, show one notice, name a minimum version. It never carries code.
 */
data class RemoteConfig(val flags: Map<String, Boolean>, val notice: EdgeNotice?, val minVersion: Int, val issuedAt: Long)

object RemoteConfigs {
    /** The only names a flag may have. Playback and the licence are not here, so no config can turn them off. */
    val KNOWN_FLAGS = setOf(
        "cola_inteligente", "portadas_animadas", "pedir_musica", "letras",
        "sync_dispositivos", "sync_biblioteca", "radar_lanzamientos", "copia_nube",
    )

    @Volatile
    var current: RemoteConfig? = null
        private set

    /** Absent flag, no config, no network, bad signature: ON — exactly how the app behaves without this layer. */
    fun on(flag: String): Boolean = current?.flags?.get(flag) ?: true

    /** The notice to show now: not expired (0 = never expires) and not already dismissed. */
    fun noticeToShow(nowMs: Long, dismissedId: String?): EdgeNotice? =
        current?.notice?.takeIf { it.id != dismissedId && (it.untilMs == 0L || it.untilMs > nowMs) }

    fun install(config: RemoteConfig?) {
        current = config
    }

    /**
     * [envelope] is `{"payload":"<json string>","sig":"<base64>"}`. Returns the config when — and only when — the signature verifies
     * against [publicKey]; anything else (no key, bad signature, junk) is null and the caller keeps what it had.
     */
    fun accept(envelope: String, publicKey: String): RemoteConfig? = try {
        val o = Json.parseToJsonElement(envelope).jsonObject
        val payload = (o["payload"] as? JsonPrimitive)?.contentOrNull
        val sig = (o["sig"] as? JsonPrimitive)?.contentOrNull
        if (payload == null || sig == null || !EdgeSignature.verify(publicKey, payload, sig)) null else parse(payload)
    } catch (_: Exception) {
        null
    }

    fun parse(payload: String): RemoteConfig? = try {
        val o = Json.parseToJsonElement(payload).jsonObject
        val flags = HashMap<String, Boolean>()
        (o["flags"] as? JsonObject)?.forEach { (k, v) ->
            val b = (v as? JsonPrimitive)?.booleanOrNull
            if (k in KNOWN_FLAGS && b != null) flags[k] = b
        }
        val notice = (o["notice"] as? JsonObject)?.let { n ->
            val id = (n["id"] as? JsonPrimitive)?.contentOrNull
            val text = (n["texto"] as? JsonPrimitive)?.contentOrNull
            if (id.isNullOrBlank() || text.isNullOrBlank()) null
            else EdgeNotice(id, (n["titulo"] as? JsonPrimitive)?.contentOrNull.orEmpty(), text, (n["hasta"] as? JsonPrimitive)?.longOrNull ?: 0L)
        }
        RemoteConfig(
            flags,
            notice,
            ((o["minVersion"] as? JsonPrimitive)?.longOrNull ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            (o["issuedAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
        )
    } catch (_: Exception) {
        null
    }
}
