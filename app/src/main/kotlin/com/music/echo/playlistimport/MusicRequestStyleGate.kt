package iad1tya.echo.music.playlistimport

import android.content.Context
import iad1tya.echo.music.reco.ArtistPopularity
import iad1tya.echo.music.reco.ArtistStyleMemory
import iad1tya.echo.music.reco.ArtistTagStyles
import iad1tya.echo.music.reco.GenreCache
import iad1tya.echo.music.reco.GenreLane
import iad1tya.echo.music.reco.MusicStyle
import iad1tya.echo.music.reco.StyleContinuity
import iad1tya.echo.music.reco.TitleLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fila 361 — cada canción de "Pedir música" se COMPRUEBA contra lo pedido antes de sonar.
 *
 * 🔴 Dueño (2026-10-09): *"pedí lo mejor del merengue cristiano 2026 y me sale con canciones de Jesús Adrián
 * Romero"* y *"reggaeton cristiano de artistas que no son conocidos, generados por IA"*. La búsqueda confiaba en
 * el buscador y en el título de la lista; nada miraba la canción. Ahora, con lo mismo que usa la cola inteligente:
 *
 *  · ESTILO — el título/álbum de la canción, las etiquetas de Last.fm del artista, lo aprendido de él y su género
 *    de iTunes ([StyleContinuity.verdict]). Otro estilo conocido = fuera (Jesús Adrián Romero es alabanza, no
 *    merengue). Las canciones de una lista que DEMUESTRA el estilo por su título cuentan como del estilo.
 *    Con suficientes canciones confirmadas, las dudosas tampoco entran ([StyleContinuity.select]).
 *  · IDIOMA — si se pidió, una canción claramente en otro idioma queda fuera.
 *  · EXCLUSIONES — "sin reggaeton", "excepto X": fuera lo que sea de ese estilo o lo nombre.
 *  · ARTISTA REAL — un artista que Last.fm conoce con menos de [ArtistPopularity.MIN_LISTENERS] oyentes es un
 *    canal anónimo (las canciones generadas por IA): fuera. Si Last.fm no respondió, no se juzga.
 *
 * Lo que el usuario nombró (la canción fijada, el artista confirmado) nunca se juzga. Solo recuentos al log.
 */
internal object MusicRequestStyleGate {

    /** Lookups keep filling the caches after the bounded wait — the next request benefits. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Cand(val id: String, val title: String, val album: String?, val artists: List<String>)

    data class Knowledge(
        val genres: Map<String, String>,
        val tags: Map<String, String>,
        val memory: ArtistStyleMemory.Memory,
        val listeners: Map<String, Long>,
    ) {
        companion object {
            val EMPTY = Knowledge(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        }
    }

    data class Outcome(
        val keptIds: List<String>,
        val match: Int,
        val unknown: Int,
        val off: Int,
        val unpopular: Int,
        val excluded: Int,
        /** 0 = strict; higher = how far [judgeWithFallback] had to relax to find songs. */
        val relaxed: Int = 0,
    )

    /** How the real-artist check judges an artist Last.fm has no entry for. */
    enum class Popularity { STRICT, KNOWN_LOW_ONLY, OFF }

    /**
     * Fila 364 (dueño 2026-10-09: *"pedí trap cristiano y dice que no encontró nada… cualquier género que pida
     * debería poder responderlo"*). The strict check is right for popular styles, but a niche one (Christian
     * trap, regional styles…) is made by small artists Last.fm never catalogued and by songs whose style no
     * title names — strict, nothing was left. So it relaxes step by step, and only as far as needed:
     *  1. strict (artists Last.fm does not know are out; with enough confirmed songs, the doubtful ones too);
     *  2. artists Last.fm does not know are allowed (only KNOWN tiny artists stay out);
     *  3. songs of unknown style are allowed too (another KNOWN style still never plays);
     *  4. nothing about the artist is judged.
     * Excluded styles/words ("sin X") and a known OTHER style never come back at any step.
     */
    fun judgeWithFallback(
        intent: MusicRequestIntent.Intent,
        cands: List<Cand>,
        verified: Set<String>,
        protectedIds: Set<String>,
        k: Knowledge,
        target: Int,
    ): Outcome {
        val enough = minOf(target, MIN_RESULT).coerceAtLeast(1)
        val steps = listOf(
            Popularity.STRICT to target,
            Popularity.KNOWN_LOW_ONLY to target,
            Popularity.KNOWN_LOW_ONLY to Int.MAX_VALUE,
            Popularity.OFF to Int.MAX_VALUE,
        )
        var last: Outcome? = null
        for ((i, step) in steps.withIndex()) {
            val o = judge(intent, cands, verified, protectedIds, k, step.second, step.first).copy(relaxed = i)
            if (o.keptIds.size >= enough) return o
            last = o
        }
        return last ?: Outcome(emptyList(), 0, 0, 0, 0, 0)
    }

    /** A request result this small counts as "found": below it the check relaxes (see [judgeWithFallback]). */
    const val MIN_RESULT = 5

    /** Looks up what is missing about [artists] (Last.fm tags + listeners, iTunes genre), waiting at most [waitMs]. */
    suspend fun learn(context: Context, artists: List<String>, waitMs: Long): Knowledge {
        val names = artists.filter { it.isNotBlank() }.distinctBy { it.trim().lowercase() }
        if (names.isNotEmpty()) {
            val jobs = listOf(
                scope.launch { runCatching { ArtistTagStyles.enrich(context, names) } },
                scope.launch { runCatching { ArtistPopularity.enrich(context, names) } },
                scope.launch { runCatching { GenreCache.enrich(context, names, onlyWifi = true) } },
            )
            withTimeoutOrNull(waitMs) { jobs.joinAll() }
        }
        return Knowledge(
            genres = runCatching { GenreCache.snapshot(context) }.getOrDefault(emptyMap()),
            tags = runCatching { ArtistTagStyles.snapshot(context) }.getOrDefault(emptyMap()),
            memory = runCatching { ArtistStyleMemory.snapshot(context) }.getOrDefault(emptyMap()),
            listeners = runCatching { ArtistPopularity.snapshot(context) }.getOrDefault(emptyMap()),
        )
    }

    /** The artist's style: Last.fm tags first, then what the app learned from its titles. */
    fun artistStyle(k: Knowledge, artist: String?): String? =
        ArtistStyleMemory.key(artist)?.let { k.tags[it] } ?: ArtistStyleMemory.styleOf(k.memory, artist)

    /**
     * Pure: which of [cands] stay, in their order. [verified] = ids that came from a playlist whose title proves
     * the requested style; [protectedIds] = what the user named (never judged). [minMatches]: with at least this
     * many confirmed songs, the doubtful ones stay out.
     */
    fun judge(
        intent: MusicRequestIntent.Intent,
        cands: List<Cand>,
        verified: Set<String>,
        protectedIds: Set<String>,
        k: Knowledge,
        minMatches: Int,
        popularity: Popularity = Popularity.STRICT,
    ): Outcome {
        val target = StyleContinuity.Target(
            styles = intent.styles.ifEmpty { null },
            language = intent.language,
            christian = intent.christian,
        )
        var unpopular = 0
        var excluded = 0
        val kept = ArrayList<Cand>()
        val verdicts = ArrayList<StyleContinuity.Verdict>()
        for (c in cands) {
            if (c.id in protectedIds) {
                kept += c
                verdicts += StyleContinuity.Verdict.MATCH
                continue
            }
            val primary = c.artists.firstOrNull()
            val text = listOfNotNull(c.title, c.album).joinToString(" ")
            val textStyle = MusicStyle.fromText(text)
            val learned = artistStyle(k, primary)
            val genre = MusicStyle.fromGenre(GenreLane.lookupGenre(k.genres, primary))
            val known = textStyle ?: learned ?: genre.style
            if ((known != null && known in intent.excludedStyles) ||
                MusicRequestIntent.mentionsExcluded(intent, (listOf(text) + c.artists).joinToString(" "))
            ) {
                excluded++
                continue
            }
            if (popularity != Popularity.OFF &&
                ArtistPopularity.isUnknownArtist(k.listeners, primary, strict = popularity == Popularity.STRICT)
            ) {
                unpopular++
                continue
            }
            kept += c
            verdicts += StyleContinuity.verdict(
                target,
                StyleContinuity.Candidate(
                    textStyle = textStyle,
                    learnedStyle = learned,
                    genre = genre,
                    language = TitleLanguage.detect(text),
                    ownArtist = false,
                    fromSearch = c.id in verified,
                ),
            )
        }
        val selected = StyleContinuity.select(kept, verdicts, minMatches)
        return Outcome(
            keptIds = selected.map { it.id },
            match = verdicts.count { it == StyleContinuity.Verdict.MATCH },
            unknown = verdicts.count { it == StyleContinuity.Verdict.UNKNOWN },
            off = verdicts.count { it == StyleContinuity.Verdict.OFF },
            unpopular = unpopular,
            excluded = excluded,
        )
    }

    fun log(site: String, intent: MusicRequestIntent.Intent, o: Outcome) {
        // Counts and style ids only — never a title, artist or id (AGENTS.md rule 4).
        timber.log.Timber.i(
            "MUSIC_REQUEST gate %s: styles=%s christian=%b lang=%s match=%d unknown=%d off=%d unpopular=%d excluded=%d kept=%d relaxed=%d",
            site,
            intent.styles.sorted().joinToString("+").ifEmpty { "none" },
            intent.christian,
            intent.language ?: "none",
            o.match, o.unknown, o.off, o.unpopular, o.excluded, o.keptIds.size, o.relaxed,
        )
    }
}
