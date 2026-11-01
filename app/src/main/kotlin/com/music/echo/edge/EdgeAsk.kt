package iad1tya.echo.music.edge

import iad1tya.echo.music.api.TrackQuery
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The shared cache of «Pedir música» answers (docs/worker/edge, `/v1/ask`). Pure, so the JVM tests pin it.
 *
 * What travels: the SHA-256 of the normalized request (never the text the user typed) and the plain
 * title/artist pairs the AI proposed. The same request from another listener is then answered without
 * asking the AI again.
 */
object EdgeAsk {
    /** Same words in another case / spacing / punctuation share one answer; a different count is a different answer. */
    fun key(prompt: String, count: Int): String {
        val normalized = prompt.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val digest = MessageDigest.getInstance("SHA-256").digest("v1|$normalized|$count".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** `{"tracks":[{title,artist}…]}` from `GET /v1/ask` → the proposals, or null (a miss, junk, fewer than 3). */
    fun parse(body: String): List<TrackQuery>? = try {
        val arr = Json.parseToJsonElement(body).jsonObject["tracks"] as? JsonArray
        arr?.mapNotNull { e ->
            val o = e.jsonObject
            val title = (o["title"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val artist = (o["artist"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (title.isBlank() || artist.isBlank()) null else TrackQuery(title, artist)
        }?.takeIf { it.size >= 3 }
    } catch (_: Exception) {
        null
    }

    /** The body of `PUT /v1/ask`. */
    fun body(key: String, tracks: List<TrackQuery>): String = buildJsonObject {
        put("k", key)
        put("tracks", buildJsonArray {
            tracks.take(60).forEach { t ->
                add(buildJsonObject {
                    put("title", t.title)
                    put("artist", t.artist)
                })
            }
        })
    }.toString()
}
