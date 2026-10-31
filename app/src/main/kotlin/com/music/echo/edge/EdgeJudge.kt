package iad1tya.echo.music.edge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Row 606: the wire format of the smart queue's second opinion (`POST /v1/judge`, docs/worker/edge). Pure, so the JVM tests pin it.
 * What travels: the title and primary artist of the songs playing and of the doubtful candidates, the candidates' position in the batch
 * (never a video id), and the style / language / faith the queue already decided. Nothing else.
 */
object EdgeJudge {
    const val MAX_CONTEXT = 10
    const val MAX_CANDIDATES = 24

    class Song(val title: String, val artist: String)

    fun body(context: List<Song>, candidates: List<Song>, styles: List<String>, language: String?, faith: Boolean): String = buildJsonObject {
        put("ctx", buildJsonArray {
            context.take(MAX_CONTEXT).forEach { add(buildJsonObject { put("t", it.title); put("a", it.artist) }) }
        })
        put("cand", buildJsonArray {
            candidates.take(MAX_CANDIDATES).forEachIndexed { i, s -> add(buildJsonObject { put("i", i); put("t", s.title); put("a", s.artist) }) }
        })
        put("hint", buildJsonObject {
            put("styles", buildJsonArray { styles.take(4).forEach { add(JsonPrimitive(it)) } })
            language?.let { put("lang", it) }
            put("faith", faith)
        })
    }.toString()

    /** `{"keep":[i…]}` → the positions the AI kept, or null when the answer is not that (the queue then goes on with its own filter). */
    fun parse(body: String, candidateCount: Int): Set<Int>? = try {
        (Json.parseToJsonElement(body).jsonObject["keep"] as? JsonArray)
            ?.map { (it as JsonPrimitive).int }
            ?.filter { it in 0 until candidateCount }
            ?.toSet()
    } catch (_: Exception) {
        null
    }
}
