package iad1tya.echo.music.edge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Function 4 of docs/PLAN_WORKER_9_FUNCIONES.md: the answer of `GET /v1/tags` as the same `(tag, weight)` list Last.fm gives the app. */
object EdgeTags {
    /** `{"tags":[["cumbia",100],…]}` → the list; an empty list is a definitive «none»; null = not an answer this app understands. */
    fun parse(body: String): List<Pair<String, Int>>? = try {
        val arr = Json.parseToJsonElement(body).jsonObject["tags"] as? JsonArray
        arr?.mapNotNull { item ->
            val pair = item as? JsonArray ?: return@mapNotNull null
            val name = (pair.getOrNull(0) as? JsonPrimitive)?.contentOrNull?.trim()
            val weight = (pair.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: 0
            if (name.isNullOrBlank()) null else name to weight
        }
    } catch (_: Exception) {
        null
    }
}
