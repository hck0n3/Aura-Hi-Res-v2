package iad1tya.echo.music.playlistimport

import java.text.Normalizer

/**
 * Fila 462 — «PEDIR UNA CANCIÓN EN KARAOKE»: la pista para cantarla.
 *
 * 🔴 Dueño (2026-10-19): *«en Pedir música, si necesito una canción en específico en karaoke, que la pueda pedir de ahí
 * para que me ponga la pista para que yo la pueda cantar»*.
 *
 * Hasta ahora «Pedir música» solo sabía EXCLUIR el karaoke ([MusicRequestInstrumental], fila 359) y, cuando la petición lo
 * pedía, lo dejaba pasar por el camino de siempre — pensado para temas, no para UNA canción:
 *  · «ponme la pista de Hotel California» y «letra y pista de…» no se reconocían como petición de karaoke (el filtro
 *    anti-karaoke se llevaba todo y salía «no encontré nada»);
 *  · el título de la canción («Amor Eterno») disparaba la ruta de ánimos («amor» = romántico) y llenaba la lista de
 *    baladas ajenas;
 *  · la pista encontrada arrastraba su «radio» (más karaoke de OTRAS canciones) y los álbumes del canal («Sing King»);
 *  · nada impedía que la lista trajera el original o un cover de otra canción junto a la pista.
 *
 * Esto es el cerebro PURO (sin red, sin Android, probado en `MusicRequestKaraokeTest`):
 *  · [parse] — reconoce la petición («karaoke de X», «la pista de X», «quiero cantar X», «instrumental de X») y saca
 *    QUÉ se quiere cantar, sin muletillas;
 *  · [select] — de lo que devuelva el buscador, deja SOLO pistas, y en el orden que sirve para cantar: la pista exacta
 *    primero, luego sus alternativas (otro canal), nunca el original ni la pista de otra canción.
 *
 * Tres clases de petición ([Kind]), que se deciden con lo que devuelve el buscador:
 *  · SONG — una canción concreta: lista corta y CERRADA (la pista y sus alternativas), sin relleno de otros temas;
 *  · ARTIST — «karaoke de Queen»: varias canciones del artista, en pista;
 *  · STYLE — «karaoke de salsa»: pistas del estilo.
 */
object MusicRequestKaraoke {

    enum class Kind { SONG, ARTIST, STYLE }

    /** What the request wants to sing, and how it was worded. */
    data class Ask(
        /** The words to sing, folded (lowercase, no accents): «bohemian rhapsody queen». */
        val subject: String,
        val words: List<String>,
        /** Said «instrumental» rather than «karaoke»: instrumentals rank first. */
        val instrumental: Boolean,
        /** The subject is only a genre / mood / era («karaoke de salsa», «karaoke de rock en español»). */
        val styleOnly: Boolean,
    ) {
        /** The search words for the track itself. */
        val query: String get() = "$subject ${if (instrumental) "instrumental" else "karaoke"}"

        /** The search words for the other kind of track ([instrumental] ⇄ karaoke). */
        val altQuery: String get() = "$subject ${if (instrumental) "karaoke" else "instrumental"}"

        val backingQuery: String get() = "$subject backing track"
    }

    /** What the search returned, as plain strings, so this stays pure. */
    data class Cand(val title: String, val album: String?, val artists: List<String>)

    class Pick<T>(
        val kind: Kind?,
        val ordered: List<T>,
        /** The candidate that IS the requested track (SONG) — its title names the card. */
        val best: T?,
    )

    // ── request → what to sing ───────────────────────────────────────────────────────────────────────────────────────

    /** Words that on their own say «karaoke» (accents already folded away). */
    private val TRIGGERS = setOf("karaoke", "karaokes", "karaoki", "caraoke", "caraokes", "playback", "playbacks")

    /** «pista de baile» is a dance floor, not a backing track. */
    private val NOT_A_BACKING_TRACK = setOf(
        "baile", "bailar", "aterrizaje", "despegue", "patinaje", "hielo", "atletismo", "carrera", "carreras", "tenis",
        "circo", "esqui", "nieve", "dance", "skate",
    )

    /**
     * What names the kind of track, removed ANYWHERE in the request, longest first («karaoke de», «la pista de la canción
     * de», «instrumental de», «sin voz»…).
     */
    private val MARKERS: List<List<String>> = listOf(
        "letra y pista de", "letra y pista", "la pista de la cancion de", "la pista de la cancion", "pista de la cancion de",
        "pista de la cancion", "la pista karaoke de", "una pista karaoke de", "la pista de karaoke de", "pista de karaoke de",
        "pista karaoke de", "pista karaoke", "la pista musical de", "una pista musical de", "pista musical de",
        "pista musical", "pistas musicales de", "pistas musicales", "pista instrumental de", "pista instrumental",
        "pista sin voz de", "pista sin voz", "pista con letra de", "pista con letra", "pista para cantar",
        "pistas para cantar", "la pista de", "una pista de", "las pistas de", "unas pistas de", "pistas de", "pista de",
        "pista del", "la pista del", "la pista", "una pista", "pistas", "pista",
        "version karaoke de", "version karaoke", "version instrumental de", "version instrumental", "el karaoke de",
        "un karaoke de", "los karaokes de", "unos karaokes de", "karaoke de", "karaokes de", "karaoke del", "el karaoke",
        "un karaoke", "en karaoke", "instrumentales de", "instrumental de", "instrumental del", "instrumentales",
        "instrumental", "backing tracks", "backing track", "minus one", "sin voz", "sin letra", "con la letra",
        "para cantarla", "para cantarlo", "para cantar", "a cantar", "cantarla", "cantarlo", "cantar", "cantando",
        "to sing", "for singing", "sing along",
    ).map { it.split(' ') }.sortedByDescending { it.size }

    /**
     * Request verbs and «la canción»: removed only at the START of a clause («pon …», «quiero cantar …»), so a title that
     * HAS those words keeps them («Te Quiero», «La Canción del Mariachi»).
     */
    private val LEADS: List<List<String>> = listOf(
        "voy a cantar", "vamos a cantar", "quiero cantar", "quisiera cantar", "necesito cantar", "me gustaria cantar",
        "i want to sing", "por favor", "can you play", "me gustaria", "put on", "show me", "give me", "play me", "i want",
        "ponme", "pon", "poner", "ponga", "dame", "pasame", "busca", "buscame", "reproduce", "reproducir", "hazme",
        "muestrame", "porfa", "please", "play", "sing",
        "de la cancion", "la cancion", "una cancion", "el tema", "un tema", "la version", "el", "la", "una", "un",
    ).map { it.split(' ') }.sortedByDescending { it.size }

    /** «quiero / necesito / busco» open a request only when followed by one of these (else «Quiero Bailar» is a title). */
    private val DESIRE = setOf("quiero", "necesito", "busco", "queria", "quisiera")
    private val DESIRE_NEXT = setOf(
        "cantar", "el", "la", "un", "una", "unos", "unas", "los", "las", "karaoke", "karaokes", "pista", "pistas",
        "instrumental", "escuchar", "oir", "tener", "ver", "que", "algo", "poner", "ponerme",
    )

    /** Connectors that cannot open or close the subject. */
    private val EDGE = setOf("de", "del", "y", "e", "o", "en", "con", "para", "por", "pero", "que", "a", "al", "ni")

    private val FRAMING = setOf("cancion", "canciones", "musica", "temas", "tema", "songs", "song", "music", "playlist", "lista", "algo", "alguna")

    private val COUNT_NOUN = Regex("""\b\d{1,3}\s+(karaokes|pistas|canciones|temas|cantos)\b""")
    private val COUNT_AFTER_VERB = Regex("""\b(pon|ponme|dame|quiero|busca)\s+\d{1,3}\b""")

    /**
     * The karaoke request in [prompt], or null when it is not one. A bare «karaoke», «música instrumental para estudiar»
     * or «canciones para cantar en el carro» are NOT a request for a track to sing: they keep their old route.
     */
    fun parse(prompt: String): Ask? {
        val folded = fold(prompt)
        if (folded.isBlank()) return null
        val tokens = folded.split(' ').filter { it.isNotEmpty() }
        val hasTrigger = tokens.any { it in TRIGGERS } || hasPhrase(tokens, "backing track") ||
            hasPhrase(tokens, "backing tracks") || hasPhrase(tokens, "minus one") || hasPhrase(tokens, "sin voz")
        val hasPista = backingPista(tokens)
        val hasInstrumental = instrumentalOf(tokens)
        val hasSing = tokens.any { it == "cantar" || it == "cantarla" || it == "cantarlo" || it == "cantando" } ||
            hasPhrase(tokens, "to sing") || hasPhrase(tokens, "sing along")
        if (!hasTrigger && !hasPista && !hasInstrumental && !hasSing) return null

        // Clauses first («quiero cantar Despacito, pon el karaoke»): the request verbs are removed at the start of EACH.
        val kept = ArrayList<String>()
        for (clause in prompt.split(Regex("""[,;.!?\n]+"""))) {
            var text = fold(clause)
            if (text.isBlank()) continue
            text = COUNT_NOUN.replace(text) { m -> m.groupValues[1] }
            text = COUNT_AFTER_VERB.replace(text) { m -> m.groupValues[1] }
            val work = text.split(' ').filter { it.isNotEmpty() }
            val clauseKept = ArrayList<String>()
            var i = 0
            while (i < work.size) {
                val marker = MARKERS.firstOrNull { p -> i + p.size <= work.size && p.indices.all { work[i + it] == p[it] } }
                if (marker != null) {
                    i += marker.size
                    continue
                }
                if (work[i] in TRIGGERS) {
                    i++
                    continue
                }
                if (clauseKept.isEmpty()) {
                    if (work[i] in DESIRE && work.getOrNull(i + 1) in DESIRE_NEXT) {
                        i++
                        continue
                    }
                    val lead = LEADS.firstOrNull { p -> i + p.size <= work.size && p.indices.all { work[i + it] == p[it] } }
                    // An article only goes when it opens a request («pon el karaoke»), never a title («La Bamba»): after a
                    // verb or a marker the clause is still empty, but a bare article at the very start is left alone.
                    if (lead != null && (lead.size > 1 || lead[0] !in setOf("el", "la", "una", "un") || i > 0)) {
                        i += lead.size
                        continue
                    }
                }
                clauseKept += work[i]
                i++
            }
            kept += clauseKept
        }
        // Edge connectors left behind by the removal («de» of «instrumental de», «en» of «en karaoke»).
        var from = 0
        var to = kept.size
        while (from < to && kept[from] in EDGE) from++
        while (to > from && kept[to - 1] in EDGE) to--
        val subject = kept.subList(from, to)
        if (subject.isEmpty()) return null
        // A request that only SAYS «para cantar» (no karaoke word) needs a real subject: «canciones para cantar en el
        // carro» is a mood, not a track.
        if (!hasTrigger && !hasPista && !hasInstrumental && subject.any { it in FRAMING }) return null
        val subjectText = subject.joinToString(" ")
        // «música instrumental / karaoke» with nothing else left is the old generic route.
        if (subject.all { it in FRAMING }) return null
        val themeOnly = MusicRequestQuery.isThemeOnly(subjectText)
        return Ask(
            subject = subjectText,
            words = subject,
            instrumental = hasInstrumental && !hasTrigger && !hasPista,
            styleOnly = themeOnly,
        )
    }

    private fun hasPhrase(tokens: List<String>, phrase: String): Boolean {
        val p = phrase.split(' ')
        return (0..tokens.size - p.size).any { s -> p.indices.all { tokens[s + it] == p[it] } }
    }

    /** «pista» as a backing track: not «pista de baile», and either followed by a track word or asked as «la pista de X». */
    private fun backingPista(tokens: List<String>): Boolean {
        for ((idx, t) in tokens.withIndex()) {
            if (t != "pista" && t != "pistas") continue
            val next = tokens.getOrNull(idx + 1)
            val after = tokens.getOrNull(idx + 2)
            if (next in NOT_A_BACKING_TRACK || (next == "de" && after in NOT_A_BACKING_TRACK)) continue
            if (next == null) continue
            // «pista de Hotel California», «pista karaoke», «pista para cantar», «pista musical de», «pista con letra».
            if (next in setOf("de", "del", "para", "karaoke", "musical", "musicales", "instrumental", "con", "sin")) return true
            // «Hotel California pista» is rare; «la pista» at the start before the subject.
        }
        return false
    }

    /** «instrumental de X» / «instrumentales de X» — but not «música instrumental» / «jazz instrumental». */
    private fun instrumentalOf(tokens: List<String>): Boolean {
        for ((idx, t) in tokens.withIndex()) {
            if (t != "instrumental" && t != "instrumentales") continue
            val next = tokens.getOrNull(idx + 1)
            if (next == "de" || next == "del" || next == "of") return true
        }
        return false
    }

    // ── what the search returned → the right tracks, in the right order ─────────────────────────────────────────────

    /** How much of a track for singing it is: karaoke / backing > instrumental > piano, lullaby, 8-bit… */
    internal enum class Tier { KARAOKE, INSTRUMENTAL, OTHER }

    private val KARAOKE_MARKERS = listOf(
        "karaoke", "karaokes", "karaoki", "caraoke", "backing track", "backing tracks", "minus one", "pista karaoke",
        "pista con letra", "pista para cantar", "pistas para cantar", "in the style of", "originally performed by",
        "made famous by", "originalmente interpretada por", "sing king", "karafun", "playback",
    )
    private val INSTRUMENTAL_MARKERS = listOf(
        "instrumental", "instrumentales", "sin voz", "sin letra", "pista musical", "pistas musicales", "pista instrumental",
        "acompanamiento", "beat instrumental", "type beat",
    )
    private val OTHER_MARKERS = listOf(
        "piano version", "version piano", "piano cover", "orchestral version", "8 bit version", "8bit version",
        "lullaby version", "version de cuna", "multitrack", "multitracks",
    )

    /** Spanish/English function words that do not identify a song. */
    private val STOP = setOf(
        "de", "del", "la", "el", "los", "las", "y", "e", "o", "a", "al", "en", "un", "una", "the", "an", "of", "and", "to",
        "in", "on", "for", "by", "with", "con", "por", "para", "mi", "tu", "su", "my", "your",
    )

    /** Words that mark the credit part of a karaoke title («In the Style of Queen», «Originally Performed By Queen»). */
    private val CREDIT_LEAD = listOf(
        listOf("in", "the", "style", "of"), listOf("originally", "performed", "by"), listOf("made", "famous", "by"),
        listOf("originalmente", "interpretada", "por"), listOf("al", "estilo", "de"), listOf("tipo", "de"),
        listOf("as", "made", "famous", "by"), listOf("popularized", "by"),
    )

    /** Words that are part of the karaoke wrapper, never of the song. */
    private val WRAPPER_WORDS = setOf(
        "karaoke", "karaokes", "karaoki", "caraoke", "instrumental", "instrumentales", "version", "versions", "lyrics",
        "lyric", "letra", "backing", "track", "tracks", "minus", "one", "playback", "pista", "pistas", "sing", "along",
        "audio", "video", "oficial", "official", "hd", "hq", "con", "sin", "voz", "karafun", "king", "lead", "vocals", "vocal",
        "piano", "acoustic", "acustica", "cover", "remastered", "remaster", "feat", "ft", "featuring",
    )

    internal fun tierOf(title: String, album: String?, artists: List<String>): Tier? {
        val text = " ${fold(listOfNotNull(title, album).joinToString(" "))} "
        val by = " ${fold(artists.joinToString(" "))} "
        val loneKaraoke = MusicRequestInstrumental.isInstrumental(title, album, artists)
        fun any(markers: List<String>) = markers.any { m -> text.contains(" $m ") || by.contains(" $m ") }
        return when {
            any(KARAOKE_MARKERS) -> Tier.KARAOKE
            any(INSTRUMENTAL_MARKERS) -> Tier.INSTRUMENTAL
            any(OTHER_MARKERS) -> Tier.OTHER
            loneKaraoke -> Tier.INSTRUMENTAL // «(Pista)»
            else -> null
        }
    }

    /** The song's own words in a karaoke title, and the credit words that go with it. */
    internal class Parts(val song: List<String>, val credit: Set<String>)

    internal fun partsOf(title: String, album: String?, artists: List<String>): Parts {
        val bracketText = Regex("""[(\[]([^)\]]*)[)\]]""").findAll(title).joinToString(" ") { it.groupValues[1] }
        val noBrackets = title.replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
        val segments = noBrackets.split(Regex("""\s[-–—|:]\s""")).map { fold(it) }.filter { it.isNotBlank() }
        val credit = LinkedHashSet<String>()
        val songWords = ArrayList<String>()
        for (segment in segments) {
            val words = segment.split(' ').filter { it.isNotEmpty() }
            var cut = words.size
            for (lead in CREDIT_LEAD) {
                val at = (0..words.size - lead.size).firstOrNull { s -> lead.indices.all { words[s + it] == lead[it] } }
                if (at != null && at < cut) {
                    cut = at
                    credit += words.drop(at + lead.size)
                }
            }
            val head = words.take(cut).filter { it !in WRAPPER_WORDS }
            songWords += head
        }
        // Brackets can carry the credit too: «(Karaoke Version In the Style of Queen)».
        val bracketWords = fold(bracketText).split(' ').filter { it.isNotEmpty() }
        for (lead in CREDIT_LEAD) {
            val at = (0..bracketWords.size - lead.size).firstOrNull { s -> lead.indices.all { bracketWords[s + it] == lead[it] } }
            if (at != null) credit += bracketWords.drop(at + lead.size)
        }
        credit += fold(listOfNotNull(album).joinToString(" ")).split(' ').filter { it.isNotEmpty() }
        credit += fold(artists.joinToString(" ")).split(' ').filter { it.isNotEmpty() }
        credit += bracketWords
        return Parts(songWords, credit)
    }

    private fun sig(words: List<String>): List<String> = words.filter { it !in STOP }.ifEmpty { words }

    /**
     * How well a track's song words say the requested ones: 1 = the same song, 2 = the same song and the rest of what was
     * asked (the artist) is in its credit, 3 = the song is inside the request but the artist is not shown, 0 = another
     * song. The «Amor» karaoke of «Juan Gabriel Amor eterno» is NOT it: a lone word covering a quarter of the request.
     */
    internal fun songTier(askWords: List<String>, parts: Parts): Int {
        val s = sig(askWords)
        val b = sig(parts.song.distinct())
        if (s.isEmpty() || b.isEmpty()) return 0
        val sSet = s.toSet()
        val bSet = b.toSet()
        if (bSet == sSet) return 1
        if (sSet.containsAll(bSet)) {
            val rest = sSet - bSet
            if (rest.all { it in parts.credit }) return 2
            val covers = bSet.size.toDouble() / sSet.size
            return if (bSet.size >= 2 || covers >= 0.6) 3 else 0
        }
        if (bSet.containsAll(sSet)) {
            // The track's title carries the artist too («Queen - Bohemian Rhapsody»): the extra words are credit.
            val extra = bSet - sSet
            return if (extra.size <= 3) 2 else 0
        }
        return 0
    }

    /** All the request's significant words are in the track's credit (artist, album, «in the style of …»). */
    internal fun creditsAll(askWords: List<String>, parts: Parts): Boolean {
        val s = sig(askWords)
        return s.isNotEmpty() && s.all { it in parts.credit }
    }

    /** Cap of a one-song list: the track and its alternatives, not a playlist. */
    const val SONG_LIST_MAX = 8

    private class Scored<T>(
        val item: T,
        val index: Int,
        val tier: Tier,
        val songTier: Int,
        val creditAll: Boolean,
        val baseKey: String,
        val channel: String,
        val parts: Parts,
    )

    /**
     * [items] (the search results, best first) → only backing tracks, in singing order.
     *
     * [target] is the size the caller asked for; a one-song list is capped at [SONG_LIST_MAX].
     */
    fun <T> select(ask: Ask, items: List<T>, target: Int, cand: (T) -> Cand): Pick<T> {
        val scored = items.mapIndexedNotNull { index, item ->
            val c = cand(item)
            val tier = tierOf(c.title, c.album, c.artists) ?: return@mapIndexedNotNull null
            val parts = partsOf(c.title, c.album, c.artists)
            Scored(
                item, index, tier,
                songTier = songTier(ask.words, parts),
                creditAll = creditsAll(ask.words, parts),
                baseKey = parts.song.joinToString(" "),
                channel = fold(c.artists.firstOrNull().orEmpty()),
                parts = parts,
            )
        }
        if (scored.isEmpty()) return Pick(null, emptyList(), null)

        val songMatches = scored.filter { it.songTier in 1..3 }
        val kind = when {
            ask.styleOnly -> Kind.STYLE
            songMatches.any { it.songTier <= 2 } -> Kind.SONG
            scored.filter { it.creditAll }.map { it.baseKey }.distinct().size >= 2 -> Kind.ARTIST
            songMatches.isNotEmpty() -> Kind.SONG
            else -> return Pick(null, emptyList(), null)
        }

        fun score(s: Scored<T>): Double {
            val tierScore = when (s.tier) {
                Tier.KARAOKE -> if (ask.instrumental) 3.0 else 4.0
                Tier.INSTRUMENTAL -> if (ask.instrumental) 4.0 else 2.0
                Tier.OTHER -> 0.0
            }
            val songScore = when (s.songTier) { 1 -> 12.0; 2 -> 9.0; 3 -> 3.0; else -> 0.0 }
            val lyrics = if (s.parts.credit.contains("lyrics") || s.parts.credit.contains("letra")) 0.5 else 0.0
            return songScore + tierScore + lyrics - s.index * 0.01
        }

        val accepted: List<Scored<T>> = when (kind) {
            Kind.SONG -> {
                val strong = songMatches.filter { it.songTier <= 2 }
                val pool = strong.ifEmpty { songMatches }
                val real = pool.filter { it.tier != Tier.OTHER }
                // Piano / lullaby / 8-bit versions only fill in when there is almost nothing else.
                (if (real.size >= 2) real else pool).sortedByDescending { score(it) }
            }
            Kind.ARTIST -> scored.filter { it.creditAll && it.tier != Tier.OTHER }
                .sortedByDescending { score(it) }
            Kind.STYLE -> {
                val real = scored.filter { it.tier != Tier.OTHER }
                (if (real.isNotEmpty()) real else scored).sortedByDescending { score(it) }
            }
        }
        // One of each: a SONG list keeps one track per channel first (the alternatives), an ARTIST / STYLE list one per song.
        val seen = HashSet<String>()
        val unique = accepted.filter { s ->
            val key = if (kind == Kind.SONG) "${s.baseKey}|${s.channel}|${s.tier}" else s.baseKey.ifBlank { s.channel }
            seen.add(key)
        }
        val cap = if (kind == Kind.SONG) minOf(target.coerceAtLeast(1), SONG_LIST_MAX) else target.coerceAtLeast(1)
        val head = unique.firstOrNull()
        val spaced = if (kind == Kind.SONG) {
            // The best track stays first; the alternatives come from different channels where possible.
            if (head == null) emptyList() else listOf(head) + MusicRequestRanking.spacedByArtist(unique.drop(1)) { it.channel }
        } else {
            MusicRequestRanking.spacedByArtist(unique) { it.channel }
        }
        val ordered = spaced.take(cap).map { it.item }
        return Pick(kind, ordered, if (kind == Kind.SONG) head?.item else null)
    }

    /** The card's title for [kind]: «Karaoke: Bohemian Rhapsody». [bestTitle] is the title of the best track found. */
    fun label(ask: Ask, kind: Kind?, bestTitle: String?, maxLength: Int = 40): String {
        val name = when {
            kind == Kind.SONG && !bestTitle.isNullOrBlank() -> prettyTitle(bestTitle).ifBlank { titleCase(ask.subject) }
            else -> titleCase(ask.subject)
        }
        val word = if (ask.instrumental) "Instrumental" else "Karaoke"
        return "$word: $name".take(maxLength)
    }

    /** «Bohemian Rhapsody (Karaoke Version) - Queen» → «Bohemian Rhapsody». */
    fun prettyTitle(title: String): String {
        val noBrackets = title.replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
        val segments = noBrackets.split(Regex("""\s[-–—|:]\s""")).map { it.trim() }.filter { it.isNotBlank() }
        val cleaned = segments.map { seg ->
            seg.replace(
                Regex("""(?iu)\b(karaoke|karaok[eé]|instrumental|versi[oó]n|version|with lyrics|lyrics|backing track|minus one|playback|pista|in the style of.*|originally performed by.*|made famous by.*)\b"""),
                " ",
            ).replace(Regex("""\s+"""), " ").trim(' ', '-', ',', '.')
        }.filter { it.isNotBlank() }
        return cleaned.firstOrNull().orEmpty()
    }

    private fun titleCase(text: String): String =
        text.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    internal fun fold(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}

/**
 * Pedir música ids (`MR:…`) whose list is a CLOSED karaoke track ([MusicRequestKaraoke.Kind.SONG]). The smart queue that
 * continues such a list must not bring more karaoke of other songs: one song's backing track is not a taste for backing
 * tracks. In memory, the last few requests only (same shape as [iad1tya.echo.music.reco.RequestStyleTargets]).
 */
object RequestKaraokeContexts {
    private const val MAX = 16
    private val ids = LinkedHashSet<String>()
    private val order = ArrayDeque<String>()

    @Synchronized
    fun markClosed(contextId: String) {
        if (ids.add(contextId)) order.addLast(contextId)
        while (order.size > MAX) ids.remove(order.removeFirst())
    }

    @Synchronized
    fun isClosed(contextId: String?): Boolean = contextId != null && contextId in ids
}
