package iad1tya.echo.music.reco

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Registry row 906 (plan, phase 9: «IA que lee tu gusto (resumen, apagada hasta aceptar)»). Pure, so the JVM tests pin it.
 *
 * «Tu gusto» is built from LOCAL listening only: the most played artists with their play counts (`DatabaseDao.mostPlayedArtists`),
 * their genres from the on-device genre cache ([GenreCache]) and the decades of the most played songs. [local] turns that into the
 * summary that always works, with no network. The AI part is optional and off: [TasteAiGate] sends nothing until the user accepted the
 * consent of version [TasteAiGate.CONSENT_VERSION], and [payload] is the WHOLE body that can travel — artist and genre names with
 * counts, decades with counts and the app language. No timestamps, no song titles, no ids of any kind, no account.
 */
object TasteSummary {
    const val MAX_ARTISTS = 8
    const val MAX_GENRES = 5
    const val MAX_DECADES = 4
    const val MAX_NAME_CHARS = 80

    /** What the AI answer may put on screen. */
    const val MAX_SUMMARY_CHARS = 700
    const val MAX_CHIPS = 6
    const val MAX_CHIP_CHARS = 32

    /** The only keys of [payload], and of each of its items: what the consent screen and the privacy policy list. */
    val PAYLOAD_KEYS = setOf("artists", "genres", "decades", "lang")
    val ARTIST_KEYS = setOf("name", "plays")
    val GENRE_KEYS = setOf("name", "count")
    val DECADE_KEYS = setOf("decade", "songs")

    data class Count(val name: String, val count: Int)

    data class DecadeCount(val decade: Int, val count: Int)

    data class Stats(val artists: List<Count>, val genres: List<Count>, val decades: List<DecadeCount>) {
        val isEmpty: Boolean get() = artists.isEmpty() && genres.isEmpty() && decades.isEmpty()
    }

    /**
     * [topArtists]: name and plays of the most played artists. [genreOf]: the cached genre of an artist name, or null.
     * [songYears]: the year of each of the most played songs (null when unknown).
     */
    fun stats(topArtists: List<Count>, genreOf: (String) -> String?, songYears: List<Int?>): Stats {
        val artists = merged(topArtists.mapNotNull { a -> clean(a.name)?.let { Count(it, a.count) } })
        val genres = merged(artists.mapNotNull { a -> genreOf(a.name)?.let(::clean)?.let { Count(it, a.count) } })
        val decades = songYears.filterNotNull().filter { it in 1900..2099 }
            .groupingBy { it / 10 * 10 }.eachCount()
            .map { DecadeCount(it.key, it.value) }
            .sortedWith(compareByDescending<DecadeCount> { it.count }.thenByDescending { it.decade })
        return Stats(artists.take(MAX_ARTISTS), genres.take(MAX_GENRES), decades.take(MAX_DECADES))
    }

    private fun clean(name: String): String? = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_CHARS).takeIf { it.isNotEmpty() }

    /** Same name in another case counts once; positive counts only; most first, then by name. */
    private fun merged(items: List<Count>): List<Count> = items
        .filter { it.count > 0 }
        .groupBy { it.name.lowercase() }
        .map { (_, same) -> Count(same.first().name, same.sumOf { it.count }) }
        .sortedWith(compareByDescending<Count> { it.count }.thenBy { it.name.lowercase() })

    enum class ChipKind { GENRE, DECADE, ARTIST }

    data class Chip(val kind: ChipKind, val label: String, val decade: Int? = null)

    /** The summary with no AI: a few lines the screen words, and the chips. */
    data class Local(val genres: List<String>, val artists: List<String>, val decade: Int?, val chips: List<Chip>)

    fun local(stats: Stats): Local {
        val genres = stats.genres.take(3).map { it.name }
        val artists = stats.artists.take(3).map { it.name }
        val decade = stats.decades.firstOrNull()?.decade
        val chips = genres.map { Chip(ChipKind.GENRE, it) } +
            stats.decades.take(2).map { Chip(ChipKind.DECADE, it.decade.toString(), it.decade) } +
            artists.take(2).map { Chip(ChipKind.ARTIST, it) }
        return Local(genres, artists, decade, chips)
    }

    /** The app language as a bare ISO 639 code ("es", "en"…): letters only, at most three. */
    fun language(code: String): String = code.filter { it in 'a'..'z' || it in 'A'..'Z' }.take(3).lowercase().ifEmpty { "en" }

    /** The WHOLE request body that may reach the AI. Nothing else is ever added to it. */
    fun payload(stats: Stats, language: String): String = buildJsonObject {
        put("artists", buildJsonArray {
            stats.artists.take(MAX_ARTISTS).forEach { add(buildJsonObject { put("name", it.name); put("plays", it.count) }) }
        })
        put("genres", buildJsonArray {
            stats.genres.take(MAX_GENRES).forEach { add(buildJsonObject { put("name", it.name); put("count", it.count) }) }
        })
        put("decades", buildJsonArray {
            stats.decades.take(MAX_DECADES).forEach { add(buildJsonObject { put("decade", it.decade); put("songs", it.count) }) }
        })
        put("lang", language(language))
    }.toString()

    /** The instruction that goes with [payload]. Fixed text: no user data in it. */
    fun systemPrompt(language: String): String =
        "You describe a person's music taste to them, warmly and briefly. You receive ONLY aggregated counts from their own " +
            "music player: their most played artists with play counts, genres with counts, and the decades of their most played " +
            "songs. Use only that data; never invent artists, facts or numbers. Write in the language whose ISO 639 code is " +
            "'${language(language)}', in the second person. Answer ONLY with JSON: {\"summary\": \"one paragraph, at most 70 words\", " +
            "\"chips\": [\"3 to 6 short tags: genres, moods or eras, at most 3 words each\"]}."

    data class AiTaste(val summary: String, val chips: List<String>)

    /** The AI answer → what may be shown, or null when it is not that JSON (the local summary stays on screen). */
    fun parseAnswer(text: String): AiTaste? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = try {
            Json.parseToJsonElement(text.substring(start, end + 1)).jsonObject
        } catch (_: Exception) {
            return null
        }
        val summary = (obj["summary"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            ?.take(MAX_SUMMARY_CHARS)?.takeIf { it.isNotEmpty() } ?: return null
        val chips = (obj["chips"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_CHIP_CHARS }
            .distinctBy { it.lowercase() }
            .take(MAX_CHIPS)
        return AiTaste(summary, chips)
    }
}

/**
 * Registry row 906: the consent gate of «Describir con IA». Off until the user accepted the consent screen of [CONSENT_VERSION]
 * (stored in `TasteAiConsentKey`); a new version of what is sent needs a new acceptance. [ask] calls [send] only past the gate.
 */
object TasteAiGate {
    /** Bump when [TasteSummary.payload] starts sending anything new: the stored acceptance then no longer counts. */
    const val CONSENT_VERSION = 1

    fun accepted(stored: Int?): Boolean = stored == CONSENT_VERSION

    sealed interface Outcome {
        data object NotAccepted : Outcome
        data object NothingToSend : Outcome
        data object Failed : Outcome
        data class Done(val taste: TasteSummary.AiTaste) : Outcome
    }

    /**
     * [stored] is the acceptance read from the settings right before asking. [send] gets the system prompt and the [TasteSummary.payload]
     * and returns the AI's raw answer (null on any failure). Nothing is sent before acceptance, nor with no listening to describe.
     */
    suspend fun ask(
        stored: Int?,
        stats: TasteSummary.Stats,
        language: String,
        send: suspend (system: String, user: String) -> String?,
    ): Outcome {
        if (!accepted(stored)) return Outcome.NotAccepted
        if (stats.artists.isEmpty() && stats.genres.isEmpty()) return Outcome.NothingToSend
        val answer = try {
            send(TasteSummary.systemPrompt(language), TasteSummary.payload(stats, language))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return answer?.let(TasteSummary::parseAnswer)?.let { Outcome.Done(it) } ?: Outcome.Failed
    }
}
