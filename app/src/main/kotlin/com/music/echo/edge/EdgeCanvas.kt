package iad1tya.echo.music.edge

import iad1tya.echo.music.canvas.CanvasArtwork
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Function 3 of docs/PLAN_WORKER_9_FUNCIONES.md: the wire format of the shared motion-cover cache (plain JSON elements, no generated serializers). */
object EdgeCanvas {
    /** `{"artwork":{…}}` from `GET /v1/canvas` → the cover, or null (a miss, junk, or a cover with nothing animated). */
    fun parse(body: String): CanvasArtwork? = try {
        val a = Json.parseToJsonElement(body).jsonObject["artwork"] as? JsonObject
        fun f(name: String) = (a?.get(name) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        a?.let {
            CanvasArtwork(
                name = f("name"), artist = f("artist"), albumId = f("albumId"), albumName = f("albumName"),
                static = f("static"), animated = f("animated"), videoUrl = f("videoUrl"),
            )
        }?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
    } catch (_: Exception) {
        null
    }

    /** The body of `PUT /v1/canvas`: the album key (names in lower-case letters and digits only) and the cover the providers returned. */
    fun body(key: String, artwork: CanvasArtwork): String = buildJsonObject {
        put("k", key)
        put("artwork", buildJsonObject {
            artwork.name?.let { put("name", it) }
            artwork.artist?.let { put("artist", it) }
            artwork.albumId?.let { put("albumId", it) }
            artwork.albumName?.let { put("albumName", it) }
            artwork.static?.let { put("static", it) }
            artwork.animated?.let { put("animated", it) }
            artwork.videoUrl?.let { put("videoUrl", it) }
        })
    }.toString()
}
