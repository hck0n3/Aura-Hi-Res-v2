package iad1tya.echo.music.reco

import java.text.Normalizer

/**
 * THE LANE of a smart-queue context — language, faith and genre family, derived from the whole CONTEXT (the
 * anchor song, or the finished album/playlist) and kept stable while the continuation runs. Pure: no Android,
 * no network, no player; the gate ([iad1tya.echo.music.playback.exactStyleFilter]) feeds it and acts on it.
 *
 * 🔴 Dueño (2026-10-20): *«estaba escuchando rap cristiano en español y continúa con reggaetón y luego con hip
 * hop en inglés, nada que ver con la cola; solo mantuvo que era cristiano. Necesito que sea más coherente»*.
 *
 * ## Why only «cristiano» survived (traced in the code, row 459)
 *  · The faith lane is a KEYWORD/cache lane ([GenreLane]) that runs BEFORE the exact-style gate and is blind to
 *    language and to the difference between rap and reggaeton («urbano» is one family there).
 *  · The exact-style gate only judged what it had EVIDENCE for. For a Christian rap context that evidence is
 *    nearly always missing: iTunes files the artists under «Christian & Gospel» (an umbrella,
 *    [MusicStyle.fromGenre] = nothing), a title like «Testimonio» names no style, a single or EP has fewer than
 *    the 3 known tracks / 4 decided titles the collection rules require, and [TitleLanguage] needs two
 *    Spanish function words. With styles AND language unknown the target was INACTIVE («style and language
 *    unknown, no filter applied») and the faith lane was the only thing left — which accepts Spanish
 *    reggaeton cristiano and English Christian hip hop alike.
 *  · When the gate DID have a language it only rejected candidates whose OWN title was decided; the vast
 *    majority of titles are undecided, so «unknown» passed (and, with fewer than 2 sure matches, ALL unknowns
 *    were added at once). And after four empty sources the engine switched the whole gate off (relax level 2).
 *
 * ## What this adds
 *  1. [languageOfText]: the title language with more evidence (ñ ¿ ¡ -ción, Portuguese ã õ, non-Latin script,
 *     a second vocabulary of song words) — and [candidateLanguage] / [contextLanguage] fuse it with what the app
 *     LEARNED about the artist ([ArtistLanguageMemory]) and weak hints (Last.fm tags, an iTunes «Latin» genre).
 *     A context that really mixes languages stays mixed ([ContextLanguage.mixed]); nothing is judged then.
 *  2. [tier]: language is a GATE (strong evidence of another language is never accepted), genre family is a
 *     gate when the concrete style is unknown, and everything that is merely UNKNOWN is ranked by how much we
 *     do know — same language and same faith first.
 *  3. [choose]: widens in small steps — exact style + language, then language unproven, then same language /
 *     faith with the style unproven, … — and stops as soon as there are enough. The caller fetches the context's
 *     own artists and their neighbourhood BEFORE it accepts the weaker steps.
 *
 * REGISTRY #39/#41/#116: nothing here turns a MISSING signal into a rejection. Only positive evidence of
 * ANOTHER language / ANOTHER family excludes a candidate; unknown stays eligible (at a lower step).
 */
object ContextLane {

    /** Code for text written in a non-Latin script (CJK, Cyrillic, Arabic, Hangul…): «not es / en / pt». */
    const val OTHER_SCRIPT = "x"

    /** A language needs at least this share of the context's decided tracks to be THE language. */
    const val DOMINANT_SHARE = 0.8

    /** A collection needs at least this many decided tracks (a single song needs one). */
    const val MIN_DECIDED_COLLECTION = 2

    /** A family below this share of the known tracks is noise (kept lanes: every family above it). */
    const val FAMILY_FLOOR = 0.15

    /** The tier numbers [tier] hands out, best first; [MAX_TIER] is the catch-all «unknown» step. */
    const val MAX_TIER = 4

    // ─────────────────────────────── language of a text ───────────────────────────────

    private val CREDITS_PAREN = Regex("\\((?:feat|ft|with|con)[^)]*\\)")
    private val CREDITS_BRACKET = Regex("\\[(?:feat|ft|with|con)[^\\]]*\\]")
    private val MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^a-z']+")

    /**
     * Words that name ONE of the languages and no other (so no word two of them share: «paz», «luz», «guerra»,
     * «real», «animal», «angel»… are left out on purpose). Folded: lowercase, no accents. This is only the
     * SECOND vocabulary — [TitleLanguage] speaks first.
     */
    private val ES_EXTRA = setOf(
        "ya", "pa", "pal", "pero", "vamos", "tengo", "tienes", "tiene", "hacer", "hago", "dime", "dejame", "deja",
        "vuelve", "vuelvo", "libre", "libres", "guerrero", "guerreros", "fuego", "verdad", "camino", "fuerza",
        "bendicion", "bendecido", "bendito", "perdon", "milagro", "milagros", "esperanza", "testimonio", "sueno",
        "suenos", "tiempo", "calle", "calles", "barrio", "hermano", "hermanos", "hijo", "hija", "madre", "nadie",
        "fue", "fuimos", "noches", "luna", "tierra", "lluvia", "viento", "creo", "creer", "cancion", "canciones",
        "rimas", "barras", "libertad", "nuevo", "nueva", "viejo", "vieja", "primero", "confio", "confia", "sigo",
        "sigue", "alla", "alli", "aca", "asi", "entonces", "despues", "bajo", "hasta", "hacia", "cristiano",
        "cristiana", "cristianos", "cristianas", "escucha", "escuchame", "mirame", "ayuda", "ayudame", "salvame",
        "salvacion", "resucito", "resurreccion", "sanidad", "sanar", "cadenas", "cadena", "sangre", "cordero",
        "promesa", "promesas", "palabra", "tuyo", "tuya", "tuyos", "vivir", "vivire", "mujer",
        "hombre", "hombres", "nino", "ninos", "ojos", "manos", "nombre", "oye", "oyeme", "dale", "manana",
        "ayer", "jamas", "puedo", "puedes", "puede", "podemos", "sabes", "peligro", "miedo", "dolor", "lagrimas",
        "llorar", "sonrisa", "beso", "besos", "piel", "gracias", "bendiciones", "predica", "iglesia", "pueblo",
    )

    private val EN_EXTRA = setOf(
        "gods", "god's", "still", "new", "free", "war", "fire", "light", "truth", "power", "blessed", "miracle",
        "miracles", "hope", "peace", "money", "girl", "boy", "back", "get", "got", "gonna", "wanna", "gotta",
        "ain't", "aint", "yeah", "street", "streets", "hood", "city", "town", "kids", "man", "men", "woman",
        "women", "mother", "father", "brother", "sister", "family", "home", "house", "world", "time", "day",
        "days", "dream", "dreams", "story", "death", "dead", "alive", "believe", "faith", "fear", "pain", "tears",
        "cry", "sing", "song", "songs", "rise", "fall", "fallen", "stand", "run", "fly", "higher", "highest",
        "savior", "saviour", "cross", "blood", "lamb", "throne", "kingdom", "promise", "promises", "word",
        "name", "heaven", "hell", "devil", "sins", "saved", "save", "redeemed", "chains", "broken", "healing",
        "healer", "mighty", "glory", "thank", "thanks", "thankful", "testimony", "anthem", "wake", "sleep",
        "walk", "walking", "talk", "talking", "church", "people", "freedom", "victory", "warrior", "warriors",
    )

    private fun foldedWords(raw: String): List<String> =
        Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(CREDITS_PAREN, " ")
            .replace(CREDITS_BRACKET, " ")
            .split(NON_WORD)
            .filter { it.length > 1 }

    /**
     * The language of a track's own text (title + album): «es», «en», «pt», [OTHER_SCRIPT] or null when it does
     * not say. Strong evidence only — a hit here may REJECT a candidate, so it never guesses from a name.
     */
    fun languageOfText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        scriptLanguage(raw)?.let { return it }
        TitleLanguage.detect(raw)?.let { return it }
        // What the folded vocabulary throws away: ñ, ¿ ¡ and -ción/-sión are Spanish; ã õ / -ão are Portuguese.
        val lower = raw.lowercase()
        if (lower.any { it == 'ñ' || it == '¿' || it == '¡' } || lower.contains("ción") || lower.contains("sión")) {
            return TitleLanguage.ES
        }
        if (lower.any { it == 'ã' || it == 'õ' } || lower.contains("ção") || lower.contains("ões")) {
            return TitleLanguage.PT
        }
        var es = 0
        var en = 0
        for (w in foldedWords(raw)) {
            if (w in ES_EXTRA) es++ else if (w in EN_EXTRA) en++
        }
        return when {
            es >= 1 && en == 0 -> TitleLanguage.ES
            en >= 1 && es == 0 -> TitleLanguage.EN
            else -> null
        }
    }

    /** [OTHER_SCRIPT] when at least half of the letters (and two or more) are not Latin script. */
    private fun scriptLanguage(raw: String): String? {
        var letters = 0
        var foreign = 0
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            i += Character.charCount(cp)
            if (!Character.isLetter(cp)) continue
            letters++
            val script = Character.UnicodeScript.of(cp)
            if (script != Character.UnicodeScript.LATIN &&
                script != Character.UnicodeScript.COMMON &&
                script != Character.UnicodeScript.INHERITED
            ) {
                foreign++
            }
        }
        return if (foreign >= 2 && foreign * 2 >= letters) OTHER_SCRIPT else null
    }

    // ─────────────────────────── evidence about a track's language ───────────────────────────

    /**
     * What we know about the language of ONE track. [strong] evidence (its own words, or what the app learned
     * about the artist from many titles) may exclude it; a hint (Last.fm tags, an iTunes «Latin» genre) never does.
     */
    data class LangEvidence(val code: String?, val strong: Boolean) {
        companion object {
            val NONE = LangEvidence(null, false)
        }
    }

    /** The best evidence: the track's own words, else the artist's learned language, else a Spanish hint. */
    fun candidateLanguage(titleLanguage: String?, artistLanguage: String?, spanishHint: Boolean): LangEvidence = when {
        titleLanguage != null -> LangEvidence(titleLanguage, strong = true)
        artistLanguage != null -> LangEvidence(artistLanguage, strong = true)
        spanishHint -> LangEvidence(TitleLanguage.ES, strong = false)
        else -> LangEvidence.NONE
    }

    /**
     * The language of a whole context. [code] is null when it is unknown OR [mixed]; a mixed context (no language
     * holds [DOMINANT_SHARE] of the decided tracks) is never judged on language. [weak] = decided by hints only.
     */
    data class ContextLanguage(val code: String?, val mixed: Boolean, val weak: Boolean = false)

    /**
     * [evidence] has one entry per context track (the anchor song is a context of one). Strong evidence votes
     * first; with none at all, Spanish hints on at least half of the tracks give a WEAK Spanish. [OTHER_SCRIPT]
     * never becomes the context's language (a K-pop list legitimately mixes scripts and English titles).
     */
    fun contextLanguage(evidence: List<LangEvidence>, minDecided: Int): ContextLanguage {
        val decided = evidence.filter { it.strong && it.code != null }.map { it.code!! }
        if (decided.size >= minDecided && decided.isNotEmpty()) {
            val counts = decided.groupingBy { it }.eachCount()
            val top = counts.maxByOrNull { it.value } ?: return ContextLanguage(null, false)
            if (top.key == OTHER_SCRIPT) return ContextLanguage(null, false)
            return if (top.value.toDouble() / decided.size >= DOMINANT_SHARE) {
                ContextLanguage(top.key, mixed = false)
            } else {
                ContextLanguage(null, mixed = true)
            }
        }
        val hints = evidence.count { !it.strong && it.code == TitleLanguage.ES }
        if (decided.isEmpty() && hints >= maxOf(minDecided, 1) && hints * 2 >= evidence.size) {
            return ContextLanguage(TitleLanguage.ES, mixed = false, weak = true)
        }
        return ContextLanguage(null, false)
    }

    // ───────────────────────────────── style and family ─────────────────────────────────

    /**
     * The styles of a SHORT collection (a single, an EP): [StyleContinuity.collectionStyles] needs 3 known
     * tracks, so a 2-track context never had a style. Here one style that is the only one known, on at least
     * half of the tracks, is the context's. Null otherwise (the regular rule handles the rest).
     */
    fun fewKnownStyles(trackStyles: List<String?>): Set<String>? {
        val known = trackStyles.filterNotNull()
        if (known.isEmpty() || known.size >= StyleContinuity.COLLECTION_MIN_KNOWN) return null
        if (known.size * 2 < trackStyles.size) return null
        val distinct = known.distinct()
        return if (distinct.size == 1) setOf(distinct.first()) else null
    }

    /**
     * The genre FAMILIES of a context whose concrete style is unknown («rap cristiano» filed by iTunes as
     * «Christian & Gospel» or «Latin Urban»): every family holding at least [FAMILY_FLOOR] of the tracks whose
     * family is known, with at least half of the tracks known. Empty when the styles ARE known (they rule) or
     * nothing is known. A mixed list keeps ALL its families.
     */
    fun familyHint(styles: Set<String>?, trackFamilies: List<String?>): Set<String> {
        if (!styles.isNullOrEmpty()) return emptySet()
        val known = trackFamilies.filterNotNull()
        if (known.isEmpty() || known.size * 2 < trackFamilies.size) return emptySet()
        return known.groupingBy { it }.eachCount()
            .filter { (_, n) -> n.toDouble() / known.size >= FAMILY_FLOOR }
            .keys
    }

    // ───────────────────────────────── the lane and the steps ─────────────────────────────────

    /**
     * What the continuation must keep. [language] null = no language requirement (unknown or mixed); [families]
     * non-empty = the concrete style is unknown but the genre family is not (urbano, rock, tropical…).
     */
    data class Lane(
        val language: String?,
        val mixed: Boolean,
        val christian: Boolean,
        val families: Set<String>,
    ) {
        /** True when the lane asks anything of a candidate that [StyleContinuity.Target] does not already. */
        val extra: Boolean get() = language != null || families.isNotEmpty()
    }

    /**
     * Row 527: the family that leads [known] (the families of a batch's known songs, in batch order) when there are at least
     * [minKnown] of them and one holds [share] of them; otherwise none. Pure.
     */
    fun consensusFamilies(known: List<String>, minKnown: Int, share: Double): Set<String> {
        if (known.size < minKnown) return emptySet()
        val top = known.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return emptySet()
        return if (top.value.toDouble() / known.size >= share) setOf(top.key) else emptySet()
    }

    /**
     * Row 580 — which families lead a context whose own style is unknown. The FIRST answer stands for the whole context: a later
     * batch, however it votes, never replaces it, so one drifted batch cannot drag the next ones along (owner 2026-10-31: «se
     * desvía de verdad»). A new queue starts without one. Pure.
     */
    fun lockedConsensus(locked: Set<String>?, fresh: Set<String>): Set<String> =
        locked?.takeIf { it.isNotEmpty() } ?: fresh

    /**
     * Row 599 — the styles that lead a queue. [locked] (decided earlier) always wins; else the [detected] ones from the context, now locked;
     * else, when the songs [heardToTheEnd] agree on exactly ONE style, that one; else null (still unknown). Pure.
     */
    fun stableStyles(locked: Set<String>?, detected: Set<String>?, heardToTheEnd: Set<String>): Set<String>? =
        locked?.takeIf { it.isNotEmpty() }
            ?: detected?.takeIf { it.isNotEmpty() }
            ?: heardToTheEnd.takeIf { it.size == 1 }

    /** One candidate as the lane sees it: the style verdict plus language, family and faith evidence. */
    data class Candidate(
        val verdict: StyleContinuity.Verdict,
        val language: LangEvidence,
        /** The family of its known style / genre / Last.fm tags, or null when nothing says. */
        val family: String?,
        /** POSITIVE evidence that it is Christian (keyword, iTunes, Last.fm). False means «not shown», never «not». */
        val christian: Boolean,
    )

    /**
     * The step of [c] in [lane]: 0 is best, null is «never».
     *
     *  - 0 — the style (or, with it unknown, the genre family) matches and the language is the lane's;
     *  - 1 — the same, with the language unproven;
     *  - 2 — style unproven, language proven;
     *  - 3 — style unproven, language unproven;
     *  - [MAX_TIER] (4) is no longer handed out (row 503): it was «not provably Christian in a Christian lane».
     *
     * Excluded: an OFF style verdict; strong evidence of another language (unless the lane has none, i.e. the
     * context is unknown or MIXED); a known family outside the lane's families; in a Christian lane, anything not
     * provably Christian (row 503).
     */
    fun tier(lane: Lane, c: Candidate): Int? {
        if (c.verdict == StyleContinuity.Verdict.OFF) return null
        val wanted = lane.language
        val code = c.language.code
        if (wanted != null && code != null && code != wanted && c.language.strong) return null
        if (lane.families.isNotEmpty() && c.family != null && c.family !in lane.families) return null
        val familySame = lane.families.isNotEmpty() && c.family != null
        val exact = c.verdict == StyleContinuity.Verdict.MATCH || familySame
        // «Proven» = the lane asks no language, or the candidate's evidence says it. A hint of ANOTHER language
        // proves nothing either way (it never excludes), so it only keeps the candidate out of the best steps.
        val languageProven = wanted == null || code == wanted
        // Row 503 (owner 2026-10-29: «rap cristiano… a las tres o cinco canciones empezó a poner música secular, mantuvo
        // el género pero no la religión»): in a Christian lane FAITH IS A GATE, not a ranking. Until now a candidate
        // that was not provably Christian fell to the catch-all step 4, and the widening ([choose]) took that whole step
        // whenever the better ones were fewer than `enough` — so a secular Spanish rap song with the right style was
        // admitted at step 0/1 and the rest at step 4. The context's own artists and what a Christian search brought
        // are proven by the caller ([iad1tya.echo.music.playback.exactStyleFilter]); anything else needs evidence.
        if (lane.christian && !c.christian) return null
        return when {
            exact && languageProven -> 0
            exact -> 1
            languageProven -> 2
            else -> 3
        }
    }

    /**
     * The widest step needed: tiers 0..k are taken, k the smallest with at least [enough] candidates (every
     * non-excluded candidate when there are fewer). Best step first; inside a step the caller's order is kept,
     * except that in a Christian lane provably Christian candidates lead their step.
     */
    /**
     * Row 588 — the widening of [choose] takes the WHOLE next step, so a handful of proven songs plus a big step of style-unproven ones
     * (tiers 2 and 3) let in every one of them (15% of what the queue admitted in the owner's logs). Unproven songs are only the FILLER
     * that brings the total up to [enough]: the proven ones (tiers 0 and 1) all stay, and as many unproven as are still missing, best
     * first. [kept] must already be in tier order ([choose] returns it that way). Pure.
     */
    fun <T> capUnproven(kept: List<T>, tierOf: (T) -> Int?, enough: Int): List<T> {
        val proven = kept.count { (tierOf(it) ?: 0) <= 1 }
        var room = (enough - proven).coerceAtLeast(0)
        return kept.filter { item ->
            if ((tierOf(item) ?: 0) <= 1) true
            else if (room > 0) { room--; true }
            else false
        }
    }

    fun <T> choose(items: List<T>, tiers: List<Int?>, christianFirst: List<Boolean>, enough: Int): List<T> {
        require(items.size == tiers.size && items.size == christianFirst.size)
        var cutoff = 0
        var count = 0
        for (step in 0..MAX_TIER) {
            count += tiers.count { it == step }
            cutoff = step
            if (count >= enough) break
        }
        return items.indices
            .filter { val t = tiers[it]; t != null && t <= cutoff }
            .sortedWith(compareBy({ tiers[it]!! }, { if (christianFirst[it]) 0 else 1 }))
            .map { items[it] }
    }
}
