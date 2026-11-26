package iad1tya.echo.music.localmedia

import java.util.Locale

/**
 * Row 880 (plan, phase 4: «separadores de artistas»). Pure Kotlin, so what splits an artist tag into several artists is
 * unit-tested on the JVM with real tags.
 *
 * [key] is what Ajustes ▸ Contenido ▸ Música local saves (never rename it: the saved choice would be lost); [label] is
 * shown as written, it is the separator itself. « & » is off unless the user turns it on: many acts are one name with it.
 */
enum class LocalArtistSeparator(val key: String, val label: String, val onByDefault: Boolean) {
    SEMICOLON("semicolon", ";", true),
    SLASH("slash", " / ", true),
    FEAT("feat", " feat. ", true),
    FT("ft", " ft. ", true),
    AMPERSAND("ampersand", " & ", false),
    COMMA("comma", ", ", true),
    X("x", " x ", true),
}

/**
 * Names that are ONE act and are never split, whatever the separators. The user's own [exceptions] are added to them
 * (Ajustes ▸ Contenido ▸ Música local ▸ «Nombres que no se separan»). Letter case and the spaces around do not matter.
 */
val BuiltInArtistNameExceptions: List<String> = listOf(
    "AC/DC",
    "Simon & Garfunkel",
    "Earth, Wind & Fire",
    "Crosby, Stills, Nash & Young",
    "Crosby, Stills & Nash",
    "Peter, Paul and Mary",
    "Tyler, The Creator",
    "Hall & Oates",
    "Daryl Hall & John Oates",
    "Mumford & Sons",
    "Iron & Wine",
    "Belle & Sebastian",
    "Above & Beyond",
    "Chase & Status",
    "Angus & Julia Stone",
    "Brooks & Dunn",
    "Sonny & Cher",
    "Sam & Dave",
    "Ike & Tina Turner",
    "Eric B. & Rakim",
    "Years & Years",
    "Chloe x Halle",
    "Wisin & Yandel",
    "Zion & Lennox",
    "Jesse & Joy",
    "Alexis & Fido",
    "Héctor & Tito",
    "Ángel & Khriz",
    "Jowell & Randy",
    "Andy & Lucas",
)

data class LocalArtistSplitRules(
    val separators: Set<LocalArtistSeparator> = DefaultSeparators,
    /** The user's own names that are one act (on top of [BuiltInArtistNameExceptions]). */
    val exceptions: Set<String> = emptySet(),
) {
    /** What the automatic scan compares: another choice here makes it rebuild the local artists (row 881 stamp). */
    fun stampKey(): String =
        separators.map(LocalArtistSeparator::key).sorted().joinToString(",") + "|" +
            exceptions.map(::exceptionKey).filter(String::isNotEmpty).sorted().joinToString("|")

    internal val protectedNames: List<String> by lazy {
        (BuiltInArtistNameExceptions + exceptions)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(::exceptionKey)
            .sortedByDescending(String::length)
    }

    internal val protectedPatterns: List<Regex> by lazy {
        protectedNames.map { name -> Regex("""(?<![\p{L}\p{N}])${Regex.escape(name)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE) }
    }

    internal val featuringSeparator: Regex? by lazy {
        val words = buildList {
            if (LocalArtistSeparator.FEAT in separators) {
                add("""feat\.?""")
                add("featuring")
            }
            if (LocalArtistSeparator.FT in separators) add("""ft\.""")
        }
        if (words.isEmpty()) null else Regex("""(?:\s+|\s*[(\[]\s*)(?:${words.joinToString("|")})\s+""", RegexOption.IGNORE_CASE)
    }

    companion object {
        val DefaultSeparators: Set<LocalArtistSeparator> = LocalArtistSeparator.entries.filterTo(LinkedHashSet()) { it.onByDefault }
        val DefaultSeparatorKeys: Set<String> = DefaultSeparators.mapTo(LinkedHashSet(), LocalArtistSeparator::key)
        val Default = LocalArtistSplitRules()

        /** From the two saved settings; nothing saved = the defaults. An unknown saved key is ignored. */
        fun fromPreferences(separatorKeys: Set<String>?, exceptions: Set<String>?): LocalArtistSplitRules =
            LocalArtistSplitRules(
                separators = separatorKeys?.let { keys -> LocalArtistSeparator.entries.filterTo(LinkedHashSet()) { it.key in keys } }
                    ?: DefaultSeparators,
                exceptions = exceptions.orEmpty().map(String::trim).filterTo(LinkedHashSet(), String::isNotEmpty),
            )

        /** The text of the «Nombres que no se separan» box: one name per line, blank lines and repeats dropped. */
        fun exceptionsFromText(text: String): Set<String> =
            text.lines().map(String::trim).filter(String::isNotEmpty).distinctBy(::exceptionKey).toCollection(LinkedHashSet())

        private fun exceptionKey(name: String): String = name.trim().lowercase(Locale.ROOT)
    }
}

private val SemicolonSeparator = Regex(";")
private val SlashSeparator = Regex("""\s+/\s+""")
private val CollabSeparator = Regex("""\s+x\s+""")
// « & the »: a band («Kool & the Gang», «Echo & the Bunnymen»), never two artists.
private val AmpersandSeparator = Regex("""\s+&\s+(?!the\s)""", RegexOption.IGNORE_CASE)
private val BandJoiner = Regex("""&|\band\b""", RegexOption.IGNORE_CASE)
private val ProtectedToken = Regex("(\\d+)")

/**
 * Row 880: one artist tag -> the artists it names, by [rules]. A protected name (built in or the user's) is set aside
 * first, so «AC/DC», «Simon & Garfunkel» or «Earth, Wind & Fire» stay one artist whatever is switched on.
 */
internal fun splitArtistNames(rawArtist: String, rules: LocalArtistSplitRules = LocalArtistSplitRules.Default): List<String> {
    val kept = ArrayList<String>()
    var masked = rawArtist
    rules.protectedPatterns.forEachIndexed { index, pattern ->
        // A plain search first: most tags hold none of these names, and a scan reads thousands of tags.
        if (!masked.contains(rules.protectedNames[index], ignoreCase = true)) return@forEachIndexed
        masked = pattern.replace(masked) { match ->
            kept += match.value
            "${kept.size - 1}"
        }
    }
    val separators = rules.separators
    fun List<String>.splitEach(enabled: Boolean, separator: Regex?): List<String> =
        if (!enabled || separator == null) this else flatMap { it.split(separator) }
    return listOf(masked)
        .splitEach(LocalArtistSeparator.SEMICOLON in separators, SemicolonSeparator)
        .splitEach(LocalArtistSeparator.SLASH in separators, SlashSeparator)
        .splitEach(true, rules.featuringSeparator)
        .splitEach(LocalArtistSeparator.X in separators, CollabSeparator)
        .splitEach(LocalArtistSeparator.AMPERSAND in separators, AmpersandSeparator)
        .flatMap { if (LocalArtistSeparator.COMMA in separators) splitCommaList(it) else listOf(it) }
        .map(::trimDanglingBracket)
        .map { part -> ProtectedToken.replace(part) { kept[it.groupValues[1].toInt()] } }
        .filter(String::isNotBlank)
        .ifEmpty { listOf(rawArtist) }
}

private fun splitCommaList(part: String): List<String> {
    val pieces = part.split(", ").map(String::trim)
    if (pieces.size < 2 || BandJoiner.containsMatchIn(part)) return listOf(part)
    if (pieces.any { it.startsWith("the ", ignoreCase = true) }) return listOf(part)
    return pieces
}

private fun trimDanglingBracket(part: String): String {
    val trimmed = part.trim()
    return when {
        trimmed.endsWith(")") && !trimmed.contains("(") -> trimmed.dropLast(1).trim()
        trimmed.endsWith("]") && !trimmed.contains("[") -> trimmed.dropLast(1).trim()
        else -> trimmed
    }
}
