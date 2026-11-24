package com.aura.migration.resolver

import com.aura.migration.model.SourceTrack
import com.aura.migration.model.YtmCandidate
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Whether a search result is the SAME RECORDING as the wanted track (registry rows 842-843).
 *
 * Adapted from ahnafnafee/songmirror@e5bad7a (MIT, Copyright (c) 2026 Ahnaf An Nafee), `songmirror/engine/matching.py`
 * (`score_candidate`, `recording_versions_compatible`, `recording_version_signature`, `creative_version_markers`,
 * `catalog_name`, `featured_artists`, `_artist_similarity`) and the RapidFuzz `token_sort_ratio` / `token_set_ratio` it calls,
 * rewritten in plain Kotlin (no RapidFuzz, no anyascii). See THIRD-PARTY-NOTICES.md.
 *
 * The rules, as in SongMirror:
 *  - a different creative version (live, acoustic, remix, mix, edit, instrumental, karaoke, piano, demo, sped up / slowed,
 *    cover, a cappella, orchestral, re-recorded, 8D, solo, alternate…) is a different recording, in either direction, and two
 *    named variants of the same family (two DJs' remixes, two concerts) are too;
 *  - a remaster year, «clean» / «explicit», «bonus track» or «from "<release>"» is the same audio (catalog variant);
 *  - the performer must match (0.92) as a complete credit set: a shared guest cannot hide a different primary artist, and a
 *    «tribute» / «karaoke» / «covers» performer is never the original;
 *  - durations further apart than 5 s are different recordings; a title accepted only on token-subset similarity needs the
 *    duration within 2.5 s.
 *
 * Differences from SongMirror, all deliberate:
 *  - A guest credited only by the CANDIDATE («Song (with X)» for «Song») is not a rejection here: registry row 832 pinned that
 *    case as accepted for the Spotify import. A guest credited by the WANTED track must still be in the candidate's credits.
 *  - Romanization uses `android.icu.text.Transliterator("Any-Latin; Latin-ASCII")` (API 29+) instead of anyascii; below API 29
 *    and on the JVM it only strips accents. The raw Unicode text is always compared too (row 832), so an exact non-Latin match
 *    never depends on the transliterator.
 *  - Combining marks are kept as part of a word (Python's `\W` would split a word at them); a duration ≤ 0 counts as unknown;
 *    a trailing «- Topic» is removed from an artist name (YouTube's auto-generated channels, as Aura's TextNormalizer does).
 *
 * Pure Kotlin and stateless apart from a small romanization memo, for JVM tests.
 */
object RecordingMatcher {

    const val FUZZY_THRESHOLD = 0.92
    const val DURATION_TOLERANCE_MS = 2_500L
    const val CATALOG_DURATION_TOLERANCE_MS = 5_000L

    /** [score] ranks candidates (0..1); [acceptable] says whether this candidate may be taken as the wanted recording at all. */
    data class Verdict(val score: Double, val acceptable: Boolean)

    fun accepts(source: SourceTrack, candidate: YtmCandidate): Boolean =
        score(source.title, source.artists, source.durationMs, candidate.title, candidate.artists, candidate.durationMs).acceptable

    fun score(
        title: String,
        artists: List<String>,
        durationMs: Long?,
        candidateTitle: String,
        candidateArtists: List<String>,
        candidateDurationMs: Long?,
    ): Verdict {
        val duration = durationMs?.takeIf { it > 0 }
        val candidateDuration = candidateDurationMs?.takeIf { it > 0 }
        val names = artistNames(artists)
        val candidateNames = artistNames(candidateArtists)
        if (!recordingVersionsCompatible(title, artists, candidateTitle, candidateArtists)) return Verdict(0.0, false)
        val artistSim = artistSimilarity(names, candidateNames)
        if (names.isNotEmpty() && candidateNames.isNotEmpty() && artistSim < FUZZY_THRESHOLD) {
            return Verdict(0.0, false)
        }
        if (duration != null && candidateDuration != null && abs(duration - candidateDuration) > CATALOG_DURATION_TOLERANCE_MS) {
            return Verdict(0.0, false)
        }

        val wantedVariants = nameVariants(title)
        val candidateVariants = nameVariants(candidateTitle)
        val nameStrict = best(::simStrict, wantedVariants, candidateVariants)
        val nameLoose = best(::simLoose, wantedVariants, candidateVariants)

        val durationScore: Double
        val durationClose: Boolean
        if (duration != null && candidateDuration != null) {
            val delta = abs(duration - candidateDuration)
            durationScore = max(0.0, 1.0 - delta.toDouble() / (DURATION_TOLERANCE_MS * 4))
            durationClose = delta <= DURATION_TOLERANCE_MS
        } else {
            durationScore = 0.5
            durationClose = false
        }

        val nameSim = if (durationClose) max(nameStrict, nameLoose) else nameStrict
        val score = 0.45 * nameSim + 0.35 * artistSim + 0.20 * durationScore
        val strong = durationClose && nameSim >= 0.78 && artistSim >= FUZZY_THRESHOLD
        val fuzzy = nameStrict >= 0.88 && artistSim >= FUZZY_THRESHOLD
        // As SongMirror's same_catalog_recording: durations further apart than 5 s were rejected above, and a source without one
        // (a CSV, an M3U without #EXTINF) still has the same audio when only a catalog decoration differs.
        val catalog = artistSim >= FUZZY_THRESHOLD &&
            catalogName(title).let { it.isNotEmpty() && it == catalogName(candidateTitle) }
        return Verdict(score, strong || fuzzy || catalog)
    }

    // ---- recording versions ------------------------------------------------------------------------------------------------

    /** Python's `\b`, spelled out: the JVM and Android's ICU regex engine disagree on what `\b` and `\w` mean outside ASCII. */
    private const val WORD_CHAR = "[\\p{L}\\p{N}_]"

    private fun word(core: String) = "(?<!$WORD_CHAR)(?:$core)(?!$WORD_CHAR)"

    private fun ci(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private val PAREN_FEAT = ci("""[(\[]\s*(?:feat|featuring|ft|with)(?!$WORD_CHAR).*?[)\]]""")
    private val TRAILING_FEAT = ci("""\s+(?:feat|featuring|ft)\.?\s+.*?(?=\s*[(\[{]|\s+[-–—]\s+|$)""")
    private val FEAT_PREFIX = ci("""^[\s(\[]*(?:feat|featuring|ft|with)(?!$WORD_CHAR)\.?\s*""")
    private val GUEST_SEPARATOR = Regex("""\s*(?:,|&|${word("and")})\s*""")

    private const val CATALOG_TAG = """(?:(?:\d{4}\s+)?re-?master(?:ed)?(?:\s+\d{4})?|clean|explicit)"""
    private val BRACKETED_CATALOG_TAG = ci("""[(\[]\s*$CATALOG_TAG\s*[)\]]""")
    private val TRAILING_CATALOG_TAG = ci("""\s*[-–—]\s*$CATALOG_TAG\s*$""")
    private val FROM_RELEASE = ci("""\s*(?:[-–—]\s*from|[(\[]\s*from)\s+(?:"[^"]+"|'[^']+'|“[^”]+”|‘[^’]+’)\s*[)\]]?\s*$""")
    private val BONUS_TRACK_SUFFIX = ci("""(?:\s+[-–—/]\s*bonus\s+track|\s*[(\[]\s*bonus\s+track\s*[)\]])\s*$""")
    private val VERSION_PART_SPLIT = Regex("""\s+[-–—]\s+|[()\[\]{}]""")
    private val VERSION_WORD = ci(word("version"))
    private val VER_WORD = ci(word("ver"))

    private val CREATIVE_VERSION_CORES: List<Pair<String, String>> = listOf(
        "live" to "live|canli|ao vivo|en vivo",
        "acoustic" to "acoustic|akustik|acustic[oa]|acoustique|unplugged|stripped",
        "remix" to "remix|rework|mashup",
        "dub" to "dub|dubbed",
        "mix" to "mix",
        "edit" to "edit",
        "vip" to "vip",
        "instrumental" to "instrumental",
        "karaoke" to "karaoke",
        "piano" to "piano",
        "demo" to "demo",
        "session" to "session",
        "alternate-speed" to "sped up|slowed(?: down)?|nightcore",
        "mix-format" to "mono|stereo",
        "cover" to "cover",
        "a-cappella" to "a ?ca?pp?ella",
        "orchestral" to "orchestral|orchestra|symphonic",
        "re-recording" to "re ?record(?:ed|ing)",
        "reverb" to "reverb",
        "spatial" to "8d|binaural",
        "solo" to "solo",
        "alternate" to "alternate|alternative",
    )
    private val CREATIVE_VERSION_PATTERNS: List<Pair<String, Regex>> =
        CREATIVE_VERSION_CORES.map { (marker, core) -> marker to ci(word(core)) }
    private val CREATIVE_VERSION_SUFFIX: List<Regex> =
        CREATIVE_VERSION_CORES.map { (_, core) -> ci("""(${word(core)})\s+version(?!$WORD_CHAR)""") }

    private const val ALTERNATE_VERSION = "alternate-version"

    private fun withoutFeatureCredits(name: String): String = TRAILING_FEAT.replace(PAREN_FEAT.replace(name, " "), "")

    /** Title without feat-clauses; version qualifiers stay, and «ver» reads as «version». Never empty for a non-blank title. */
    internal fun looseName(name: String): String {
        val raw = name.trim()
        val cleaned = VER_WORD.replace(normalizeText(withoutFeatureCredits(raw)), "version")
        return cleaned.ifEmpty { normalizeText(raw).ifEmpty { raw.lowercase(Locale.ROOT) } }
    }

    internal fun creativeVersionMarkers(name: String): Set<String> {
        val normalized = romanized(looseName(name))
        val markers = CREATIVE_VERSION_PATTERNS.filter { (_, pattern) -> pattern.containsMatchIn(normalized) }
            .map { it.first }.toMutableSet()
        if (markers.isEmpty() && VERSION_WORD.containsMatchIn(normalized)) markers += ALTERNATE_VERSION
        return markers
    }

    /** Title identity that ignores catalog decorations (remaster, clean/explicit, bonus track, from "<release>") only. */
    internal fun catalogName(name: String): String {
        var cleaned = BONUS_TRACK_SUFFIX.replace(name, "")
        while (true) {
            val previous = cleaned
            cleaned = BRACKETED_CATALOG_TAG.replace(cleaned, " ")
            cleaned = TRAILING_CATALOG_TAG.replace(cleaned, " ")
            cleaned = FROM_RELEASE.replace(cleaned, " ")
            if (cleaned == previous) break
        }
        var normalized = looseName(cleaned)
        val markers = creativeVersionMarkers(cleaned)
        if (markers.isNotEmpty() && markers != setOf(ALTERNATE_VERSION)) {
            for (suffix in CREATIVE_VERSION_SUFFIX) normalized = suffix.replace(normalized, "$1")
        }
        return normalized.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Recording family plus the named mix, venue or version-number details («First DJ Remix», «Live at Wembley»). */
    internal fun recordingVersionSignature(name: String): Pair<Set<String>, Set<String>> {
        val stripped = BONUS_TRACK_SUFFIX.replace(name, "")
        val markers = creativeVersionMarkers(stripped)
        val markerKey = markers.sorted().joinToString(" ")
        val details = mutableSetOf<String>()
        for (part in VERSION_PART_SPLIT.split(withoutFeatureCredits(stripped)).drop(1)) {
            if (creativeVersionMarkers(part).isEmpty()) continue
            var normalized = romanized(looseName(part))
            for ((marker, pattern) in CREATIVE_VERSION_PATTERNS) normalized = pattern.replace(normalized, marker)
            if (markers != setOf(ALTERNATE_VERSION)) normalized = VERSION_WORD.replace(normalized, "")
            val detail = normalized.split(' ').filter { it.isNotEmpty() }.sorted().joinToString(" ")
            if (detail != markerKey) details += detail
        }
        return markers to details
    }

    /** Explicit guest credits in a title: «Song (feat. A & B)» → [A, B]. */
    internal fun featuredArtists(name: String): List<String> {
        val credits = mutableListOf<String>()
        for (pattern in listOf(PAREN_FEAT, TRAILING_FEAT)) {
            for (match in pattern.findAll(name)) {
                val credit = FEAT_PREFIX.replace(match.value, "").trimEnd(')', ']', ' ')
                credits += GUEST_SEPARATOR.split(credit).map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        return credits
    }

    private fun hasCredit(credit: String, artists: String): Boolean = " ${romanized(credit)} " in " ${romanized(artists)} "

    private val DOLLAR_AS_S = Regex("""(?<=[A-Za-z])\$(?=[A-Za-z])""")

    /** «A$AP» is «ASAP»: an internal dollar used as an S in a performer credit. */
    private fun creditSpelling(value: String): String = DOLLAR_AS_S.replace(value, "s")

    private fun recordingVersionsCompatible(
        name: String,
        artists: List<String>,
        candidateName: String,
        candidateArtists: List<String>,
    ): Boolean {
        if (recordingVersionSignature(name) != recordingVersionSignature(candidateName)) return false
        val candidateCredits = creditSpelling((candidateArtists + featuredArtists(candidateName)).joinToString(" "))
        return featuredArtists(name).all { hasCredit(creditSpelling(it), candidateCredits) }
    }

    // ---- performers --------------------------------------------------------------------------------------------------------

    private val UNAVAILABLE_ARTISTS = setOf("", "release", "release topic", "unknown artist", "various artists")
    private val TOPIC_SUFFIX = ci("""\s*-\s*topic\s*$""")
    private val TRIBUTE = Regex(word("tribute|karaoke|covers?|renditions?"))
    private val CREDIT_SEPARATOR = ci(
        """\s*(?:,|;|&|(?<!$WORD_CHAR)(?:feat|ft)\.?\s|(?<!$WORD_CHAR)featuring\s|${word("and")}|${word("with")})\s*""",
    )

    private fun artistNames(artists: List<String>): List<String> =
        artists.map { TOPIC_SUFFIX.replace(it, "").trim() }.filter { romanized(it) !in UNAVAILABLE_ARTISTS }

    private fun artistVariants(value: String): Set<String> {
        val variants = listOf(value, creditSpelling(value))
            .flatMap { listOf(normalizeText(it), romanized(it)) }
            .filter { it.isNotEmpty() }
            .toMutableSet()
        variants += variants.map { it.replace(" ", "") }
        variants += variants.filter { it.startsWith("the ") && it.length > 4 }.map { it.substring(4) }
        return variants
    }

    private fun creditVariants(values: List<String>): Set<String> {
        val out = values.flatMap { artistVariants(it) }.toMutableSet()
        out += out.filter { it.endsWith(" music") && it.split(' ').size >= 4 }.map { it.removeSuffix(" music") }
        return out
    }

    /** Complete performer credits: either side's credited names must all be among the other's (0..1). */
    private fun artistSimilarity(names: List<String>, candidates: List<String>): Double {
        val leftTribute = TRIBUTE.findAll(romanized(names.joinToString(" "))).map { it.value }.toSet()
        val rightTribute = TRIBUTE.findAll(romanized(candidates.joinToString(" "))).map { it.value }.toSet()
        if (leftTribute != rightTribute) return 0.0

        fun splitCredits(values: List<String>) =
            values.flatMap { CREDIT_SEPARATOR.split(it) }.map { it.trim() }.filter { it.isNotEmpty() }

        fun allCredits(left: List<String>, right: List<String>): Double {
            val rightVariants = creditVariants(right)
            return left.minOfOrNull { best(::tokenSortRatio, creditVariants(listOf(it)), rightVariants) } ?: 0.0
        }

        val leftParts = splitCredits(names)
        val rightParts = splitCredits(candidates)
        if (leftParts.isEmpty() || rightParts.isEmpty()) return 0.0
        if (creditVariants(listOf(names.joinToString(" "))).any { it in creditVariants(listOf(candidates.joinToString(" "))) }) {
            return 1.0
        }
        return max(allCredits(leftParts, rightParts), allCredits(rightParts, leftParts))
    }

    // ---- text ----------------------------------------------------------------------------------------------------------

    private val NON_WORD = Regex("""[^\p{L}\p{N}\p{M}]+""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")

    /** Letters, digits and marks of ANY script, lowercase, single-spaced (row 832: never empty a non-Latin title). */
    internal fun normalizeText(value: String): String {
        if (value.isEmpty()) return ""
        val lowered = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val recomposed = Normalizer.normalize(lowered.replace("i̇", "i"), Normalizer.Form.NFKC)
        return NON_WORD.replace(recomposed, " ").trim()
    }

    internal fun romanized(text: String): String = normalizeText(romanize(text))

    private fun nameVariants(text: String): Set<String> = setOf(looseName(text), romanized(text)).filter { it.isNotEmpty() }.toSet()

    private fun romanize(text: String): String {
        if (text.all { it.code < 128 }) return text
        val transliterate = icuTransliterator ?: return COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
        synchronized(romanizedMemo) { romanizedMemo[text]?.let { return it } }
        val out = try {
            transliterate(text)
        } catch (_: Throwable) {
            COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
        }
        synchronized(romanizedMemo) { romanizedMemo[text] = out }
        return out
    }

    private val romanizedMemo = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 512
    }

    /**
     * `android.icu.text.Transliterator` is public API from 29; looked up reflectively so this file stays pure Kotlin for the JVM
     * tests (where it is absent, or a stub that throws) and falls back to accent stripping anywhere it is not usable.
     */
    private val icuTransliterator: ((String) -> String)? by lazy {
        try {
            val sdk = Class.forName("android.os.Build\$VERSION").getField("SDK_INT").getInt(null)
            if (sdk < 29) return@lazy null
            val type = Class.forName("android.icu.text.Transliterator")
            val instance = type.getMethod("getInstance", String::class.java).invoke(null, "Any-Latin; Latin-ASCII")
            val method = type.getMethod("transliterate", String::class.java)
            val probe = method.invoke(instance, "Камин") as String
            if (probe.isEmpty()) return@lazy null
            val transliterate: (String) -> String = { s -> synchronized(instance) { method.invoke(instance, s) as String } }
            transliterate
        } catch (_: Throwable) {
            null
        }
    }

    // ---- similarity (RapidFuzz semantics) -------------------------------------------------------------------------------

    private fun best(sim: (String, String) -> Double, left: Set<String>, right: Set<String>): Double {
        var top = 0.0
        for (a in left) for (b in right) if (a.isNotEmpty() && b.isNotEmpty()) top = max(top, sim(a, b))
        return top
    }

    /** Penalizes extra words; Jaro-Winkler floor for short strings and transliteration near-misses. */
    private fun simStrict(a: String, b: String): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else max(tokenSortRatio(a, b), Similarity.jaroWinkler(a, b).toDouble())

    /** Order-, subset- and decoration-tolerant; trusted for titles only with duration support. */
    private fun simLoose(a: String, b: String): Double = if (a.isEmpty() || b.isEmpty()) 0.0 else tokenSetRatio(a, b)

    private fun tokens(s: String): List<String> = s.split(' ', '\t', '\n').filter { it.isNotEmpty() }

    /** RapidFuzz `fuzz.token_sort_ratio` / 100. */
    internal fun tokenSortRatio(a: String, b: String): Double =
        ratio(tokens(a).sorted().joinToString(" "), tokens(b).sorted().joinToString(" "))

    /** RapidFuzz `fuzz.token_set_ratio` / 100. */
    internal fun tokenSetRatio(a: String, b: String): Double {
        val ta = tokens(a).toSet()
        val tb = tokens(b).toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val intersection = ta intersect tb
        val diffAb = ta - tb
        val diffBa = tb - ta
        if (intersection.isNotEmpty() && (diffAb.isEmpty() || diffBa.isEmpty())) return 1.0
        val abJoined = diffAb.sorted().joinToString(" ")
        val baJoined = diffBa.sorted().joinToString(" ")
        val abLen = codePointLength(abJoined)
        val baLen = codePointLength(baJoined)
        val sectLen = codePointLength(intersection.joinToString(" "))
        val sep = if (sectLen != 0) 1 else 0
        val sectAbLen = sectLen + sep + abLen
        val sectBaLen = sectLen + sep + baLen
        val result = normalizedSimilarity(indelDistance(abJoined, baJoined), sectAbLen + sectBaLen)
        if (sectLen == 0) return result
        val sectAbRatio = normalizedSimilarity(sep + abLen, sectLen + sectAbLen)
        val sectBaRatio = normalizedSimilarity(sep + baLen, sectLen + sectBaLen)
        return maxOf(result, sectAbRatio, sectBaRatio)
    }

    /** RapidFuzz `fuzz.ratio` / 100: normalized Indel similarity over code points. */
    internal fun ratio(a: String, b: String): Double =
        normalizedSimilarity(indelDistance(a, b), codePointLength(a) + codePointLength(b))

    private fun normalizedSimilarity(distance: Int, lengthSum: Int): Double =
        if (lengthSum == 0) 1.0 else 1.0 - distance.toDouble() / lengthSum

    private fun codePointLength(s: String): Int = s.codePointCount(0, s.length)

    private fun indelDistance(a: String, b: String): Int {
        val x = a.codePoints().toArray()
        val y = b.codePoints().toArray()
        if (x.isEmpty() || y.isEmpty()) return x.size + y.size
        var previous = IntArray(y.size + 1)
        var current = IntArray(y.size + 1)
        for (i in 1..x.size) {
            for (j in 1..y.size) {
                current[j] = if (x[i - 1] == y[j - 1]) previous[j - 1] + 1 else max(previous[j], current[j - 1])
            }
            val swap = previous; previous = current; current = swap
        }
        val lcs = previous[y.size]
        return x.size + y.size - 2 * lcs
    }
}
