package iad1tya.echo.music.lyrics

/**
 * What the lyrics providers are actually asked.
 *
 * 🔴 OWNER REPORT (2026-09-16): *"cada vez que busco letras de alguna canción la mayoría de las veces dice
 * que no hay letras, y con 10 proveedores… no lo veo correcto"*, and then: *"si hay 10 proveedores, que la
 * canción busque su letra dentro de los 10"*.
 *
 * It already does. On a total miss every provider is queried — the early exit in [LyricsHelper] only
 * triggers once something HAS been found. The problem was never how many were asked; it was what they were
 * asked with. `mediaMetadata.title` is the YouTube title, verbatim:
 *
 *     "Artist - Song (Official Music Video) [4K]"
 *     "Song (Official Audio)"
 *     "Song (Lyrics)"
 *
 * LrcLib, KuGou, BetterLyrics and the rest index a track NAME. None of them has that string, so ten
 * providers produce ten misses. Nothing in the helper or in any of the six provider modules cleaned it —
 * verified by grep across all of them.
 *
 * The artist had the same problem: every credited name joined with commas ("A, B, C") where the databases
 * key on the lead artist.
 *
 * ## Deliberately conservative
 * Only the tags that are never part of a song's real name are removed. A title is not "cleaned" of
 * anything a human might have meant: "(Live)", "(Acoustic)", "(Remix)" and named versions all stay,
 * because they identify a DIFFERENT recording with different lyrics. Getting that wrong would show the
 * studio lyrics over a live cut, which is worse than showing none.
 *
 * And the caller runs a SECOND pass with the raw strings when the cleaned pass finds nothing, so this can
 * only ever add matches, never remove one that used to work.
 */
object LyricsQuery {
    /**
     * Bracketed noise: upload tags that describe the VIDEO, not the song. Anchored to the whole bracketed
     * group so "(Official Music Video)" goes and "(Live at Wembley)" stays.
     */
    private val BRACKETED_NOISE = Regex(
        """\s*[(\[]\s*(?:
            official(?:\s+(?:music|lyric|lyrics|video|audio|visualizer|visualiser))*\s*(?:video|audio|visualizer|visualiser)?
            |lyrics?(?:\s+video)?
            |audio
            |visuali[sz]er
            |m/?v
            |music\s+video
            |video\s+oficial
            |audio\s+oficial
            |letra
            |hd|hq|4k|8k|1080p|720p
            |remaster(?:ed)?(?:\s+\d{4})?
            |explicit|clean
            |full\s+song
        )\s*[)\]]""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS),
    )

    /** The same noise when it trails without brackets: "Song - Official Video". */
    private val TRAILING_NOISE = Regex(
        """\s*[-–—|]\s*(?:official\s+(?:music\s+)?video|official\s+audio|lyrics?\s+video|visuali[sz]er|m/?v)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Leftover empty brackets and doubled separators after a removal. */
    private val EMPTY_BRACKETS = Regex("""\s*[(\[]\s*[)\]]""")
    private val COLLAPSE_SPACES = Regex("""\s{2,}""")

    /**
     * The song name to search for, or the input unchanged when there was nothing to remove.
     *
     * Never returns blank: if stripping would empty the title (a song literally named "(Audio)"), the
     * original is kept — an empty query matches nothing at every provider.
     */
    fun cleanTitle(raw: String): String {
        val stripped = raw
            .replace(BRACKETED_NOISE, " ")
            .replace(TRAILING_NOISE, "")
            .replace(EMPTY_BRACKETS, " ")
            .replace(COLLAPSE_SPACES, " ")
            .trim()
            .trim('-', '–', '—', '|', ',')
            .trim()
        return stripped.ifBlank { raw }
    }

    /**
     * Where a credit list stops being the lead artist. The word boundary must come BEFORE the optional
     * period, not after it: in "feat." there is no word boundary after the dot, so anchoring there made
     * the whole alternative unmatchable and "Eminem feat. Rihanna" was sent to the providers whole.
     */
    private val FEATURE_SEPARATOR = Regex(
        """\s*(?:&|\bfeat\b\.?|\bft\b\.?|\bfeaturing\b|\bwith\b|\bx\b|\bvs\b\.?)\s+""",
        RegexOption.IGNORE_CASE,
    )

    /** Pieces that follow a comma inside ONE artist's name. "Tyler, The Creator" is not two people. */
    private val KNOWN_SUFFIXES = listOf("the creator", "jr", "sr", "ii", "iii")

    /**
     * The lead credited artist.
     *
     * Splits on "&", "feat", "ft", "featuring", "with", "x" and the comma list the player joins with —
     * but NEVER on a comma that belongs to one name. "Tyler, The Creator" is one artist; splitting it
     * gives "Tyler", who is someone else. The same care is taken in
     * [iad1tya.echo.music.ui.screens.search.suggestions.SuggestionMatch.primaryArtistName], for the same
     * reason.
     */
    fun primaryArtist(credited: String): String {
        val featSplit = credited.split(FEATURE_SEPARATOR)
            .firstOrNull()?.trim().orEmpty().ifBlank { credited }

        // The player joins several artists with ", ". Treat that as a list ONLY when what follows the
        // comma starts a new credited name — never for a single name that contains a comma.
        val parts = featSplit.split(", ")
        if (parts.size <= 1) return featSplit
        val suffix = parts[1].trim().trimEnd('.').lowercase()
        val lead = if (KNOWN_SUFFIXES.any { it == suffix }) "${parts[0].trim()}, ${parts[1].trim()}" else parts[0].trim()
        return lead.ifBlank { credited }
    }

    /** True when cleaning actually changed the query, i.e. a raw retry could find something different. */
    fun isWorthRetryingRaw(
        rawTitle: String,
        rawArtist: String,
        cleanedTitle: String,
        cleanedArtist: String,
    ): Boolean = rawTitle != cleanedTitle || rawArtist != cleanedArtist
}
