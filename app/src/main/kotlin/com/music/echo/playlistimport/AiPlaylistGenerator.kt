package iad1tya.echo.music.playlistimport

import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.ChartsPage
import iad1tya.echo.music.api.AiPlaylistConstraints
import iad1tya.echo.music.api.AiPlaylistService
import iad1tya.echo.music.api.TrackQuery
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates the AI text-to-playlist flow: ask the AI for a track list ([AiPlaylistService]),
 * resolve each track against the catalog ([SongResolver], shared with the JR importer), then persist
 * a new local playlist in a single transaction. Network + DB, so the DB/network integration is
 * manual-APK tested like the rest of the project — but the resolve orchestration itself
 * ([resolveBounded]) is pure/injected and unit-tested (AiPlaylistResolveBoundedTest).
 *
 * Tracks that do not resolve (invented titles, no catalog match) are omitted. If the AI is
 * unavailable or every track misses, this returns [EmptyResultException] — it does NOT invent a
 * playlist from a raw YouTube search of the prompt.
 */
object AiPlaylistGenerator {

    private const val MAX_NAME_LENGTH = 40

    /**
     * Hard ceiling on the whole AI phase. Bounds the pathological all-timeouts case so the dialog
     * can't spin for minutes holding the modem awake (battery/heat rule); on timeout we simply
     * treat AI as unavailable and build the non-AI playlist.
     *
     * Shared with [AiPlaylistPlaylistModifier], which bounds its own AI phase with the same budget.
     * Owner directive 2026-08-31 ("AI answers are VERY slow"): this budget is now measured from the
     * FIRST AI call of the whole flow — the ask, the resolve loop and the top-up all run inside the
     * SAME [AI_BUDGET_MS] window, so the worst case is 60s once, never 60s per phase stacked.
     */
    internal const val AI_BUDGET_MS = 90_000L

    /**
     * Concurrent resolves against YouTube Music. The resolve loop used to walk the AI proposals
     * one-by-one (each miss = a search round-trip ≈1s), which dominated the wait after the AI reply.
     * 4 keeps the burst bounded (battery/heat rule) while cutting a 12-track resolve from ~12s to
     * ~3s. Not higher: each resolve can fire up to 2 searches (song filter + video filter).
     */
    internal const val RESOLVE_CONCURRENCY = 4

    /**
     * Thin padding over the user's target (owner directive 2026-08-31: "AI answers are VERY slow").
     * The old 1.5× pad made Llama 70B generate ~50% more tracks — more output tokens, directly more
     * seconds on the slowest leg of the chain — to pre-absorb resolver misses that the row-198
     * anti-hallucination prompt + soloPrimaryMatch filter no longer produce in bulk: post-198 the
     * model returns fewer but REAL tracks, and the structured top-up exists for the rare shortfall.
     * +2 absorbs the odd resolver miss without paying 50% more latency on every single request.
     */
    internal const val PAD_OVER_TARGET = 2

    /**
     * Per-ask cap for ONE Worker round trip, inside the shared [AI_BUDGET_MS] window. Live probe
     * (2026-08-30, row 198): the Aura Worker answered 12 tracks in 17.5s ≈ fixed ~3s + ~1.2s/track.
     * A FLAT cap cannot serve both a 10-track ask (~18s) and a 30-track ask (~40s): flat-30s would
     * kill every 30-track playlist mid-JSON — a PRECISION regression the owner's directive forbids
     * ("not one gram of precision"). So the keyless cap SCALES with the ask ([keylessAskCapMs]):
     * base + per-track, clamped so at least [RESOLVE_RESERVE_MS] of the budget survives for the
     * resolve phase. Without any cap, a hung Worker burned the 90s client readTimeout against a
     * 60s budget: the user watched a dead spinner for the full minute. On cap the ask yields null
     * and the instant, honest "Generada sin IA" fallback runs (owner directive 2026-08-31).
     * The 50-track option exceeds the whole budget by physics (ask ~65s alone) — it failed under
     * the old code too (60s window), now it just fails 15s sooner.
     */
    internal const val AI_ASK_CAP_MS = 60_000L

    /** Fixed part of the keyless ask cap: Worker queue + KV + model load, measured ≈3s, padded. */
    internal const val ASK_BASE_MS = 25_000L

    /** Variable part: ~1.2s/track measured (17.5s for 12), padded to 1.5s/track. */
    internal const val ASK_PER_TRACK_MS = 2_000L

    /**
     * Budget share the resolve phase may always rely on. A 4-lane resolve of ~36 proposals runs in
     * ~9 waves ≈ 12s worst case; 15s leaves margin. The ask cap clamps to `AI_BUDGET_MS - this`.
     */
    internal const val RESOLVE_RESERVE_MS = 20_000L

    // 2026-09-14 (gpt-oss-120b): the top-up round only runs with at least this much budget left, so a
    // slow refill can never cancel the whole AI flow and throw a good first pass into "sin IA".
    internal const val MIN_TOP_UP_MS = 20_000L

    /**
     * Correa de la IA en "pedir música". No es el presupuesto de [AI_BUDGET_MS] (90 s): aquí la
     * búsqueda corre en paralelo y responde en uno o dos segundos, así que la IA solo vale la pena
     * mientras pueda llegar antes de que él se canse. 25 s cubre de sobra a un proveedor con clave
     * propia (2-6 s) y corta en seco al Worker atascado, que es lo que le arruinaba la función.
     */
    internal const val MUSIC_REQUEST_AI_LEASH_MS = 25_000L

    /**
     * Techo de la búsqueda en "pedir música". Son 1-3 peticiones a YouTube Music; si en 20 s no han
     * vuelto, la red está para pocas alegrías y es mejor decirlo que seguir girando.
     */
    internal const val MUSIC_REQUEST_SEARCH_BUDGET_MS = 20_000L

    /**
     * Cuántas listas se miran por filtro antes de elegir. Seis: más allá de eso el buscador ya está
     * devolviendo cosas que solo comparten una palabra con la petición, y mirarlas solo aumenta la
     * probabilidad de aceptar una que no toca.
     */
    internal const val PLAYLIST_CANDIDATES = 6

    /**
     * Cuántas candidatas se juntan antes de elegir. Sesenta salen de dos páginas de lista, que es lo
     * que ya se descarga de todas formas: el montón no cuesta red, cuesta memoria durante un segundo.
     * Más allá de esto ya no mejora la elección — solo se añaden candidatas que el propio origen
     * colocó al final por algo.
     */
    internal const val POOL_TARGET = 60

    /**
     * Cuántas listas se usan. Dos, no una: dos curadores distintos dan más variedad que uno, y las dos
     * han pasado la misma prueba. Tres empieza a mezclar criterios y el resultado deja de parecerse a
     * lo que pidió.
     */
    internal const val PLAYLISTS_USED = 2

    /**
     * Fila 361 — how long a request may wait for the per-song check's lookups (Last.fm tags and listeners,
     * iTunes genre) of artists never seen before. Known artists cost nothing (cached); the lookups that miss
     * this window still land in the caches for the next request.
     */
    internal const val GATE_WAIT_MS = 2_500L

    /**
     * Fila 364 — the network steps of the search stop here (well inside [MUSIC_REQUEST_SEARCH_BUDGET_MS]), so
     * the per-song check and the ranking always run on what was found instead of a timeout discarding it all.
     */
    internal const val SEARCH_STEPS_BUDGET_MS = 11_000L

    /** Doce horas. La taxonomía de estados de ánimo y géneros cambia como mucho cada varios meses. */
    internal const val MOODS_TTL_MS = 12 * 60 * 60 * 1000L

    @Volatile private var cachedMoods: List<com.music.innertube.pages.MoodAndGenres>? = null
    @Volatile private var cachedMoodsAt = 0L

    /** Scaled per-ask cap for the KEYLESS chain — see [AI_ASK_CAP_MS] for the rationale. */
    internal fun keylessAskCapMs(requestCount: Int): Long =
        (ASK_BASE_MS + ASK_PER_TRACK_MS * requestCount)
            .coerceAtMost(minOf(AI_ASK_CAP_MS, AI_BUDGET_MS - RESOLVE_RESERVE_MS))

    data class Result(
        val playlistId: String,
        val name: String,
        val total: Int,
        val resolved: Int,
        /** True when the AI chain failed and the playlist was built from search/radio, not AI. */
        val generatedWithoutAi: Boolean = false,
    )

    class EmptyResultException : Exception("No tracks could be resolved")

    /**
     * Lo que una petición produjo ANTES de decidir qué se hace con ello.
     *
     * Existe porque hay dos consumidores con necesidades distintas y una sola cadena que vale la pena
     * mantener: [generate] guarda una playlist en la biblioteca (y ahí el origen SÍ se etiqueta), y
     * [produce] solo quiere canciones para ponerlas a sonar.
     *
     * [fromAi] es para el LOG, no para la pantalla. Ver [produce].
     */
    data class Produced(
        val name: String,
        val songs: List<MediaMetadata>,
        val fromAi: Boolean,
    )

    /**
     * HÍBRIDO SILENCIOSO — la cadena completa, sin guardar nada y sin decir por dónde vino.
     *
     * 🔴 Orden del dueño (2026-09-17): *"puedes hacer un híbrido que lleve la IA y cuando no detecte
     * que la IA está disponible lo haga de manera gratuita sin IA, pero que no haga referencia si lo
     * hizo o no lo hizo con IA; cuando dé la respuesta a la hora de pedir música que solo entregue el
     * resultado"*.
     *
     * Y tiene razón para ESTE caso, aunque sea lo contrario de lo que hace [generate]. La etiqueta
     * "(sin IA)" nació de una queja concreta suya de 2026-09-03: una playlist GUARDADA que no se
     * parecía a lo que pidió quedaba en la biblioteca indistinguible de una curada, y la etiqueta es
     * lo que convierte esa sorpresa en información. Pedir música no guarda nada: suena y ya. Ahí la
     * etiqueta no informa de nada accionable — solo interrumpe con detalle de implementación a alguien
     * que pidió canciones.
     *
     * Lo que NO se pierde: el origen se registra en el log (`MUSIC_REQUEST source=…`). Sin esa línea,
     * si su Worker de Cloudflare se cayera, la función seguiría "funcionando" con la ruta de búsqueda
     * y **nadie se enteraría nunca** de que la IA lleva semanas muerta — que es exactamente cómo murió
     * Pollinations sin que nadie lo notara hasta sondearlo en vivo. Silencioso en pantalla no puede
     * significar invisible en el diagnóstico.
     *
     * No persiste NADA: ni playlist ni canciones. El reproductor guarda lo que hace falta al sonar.
     */
    suspend fun produce(
        database: MusicDatabase,
        prompt: String,
        count: Int,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        onResolveProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        /**
         * El perfil de gusto del usuario, o null. Lo construye el llamante **en paralelo** con esta
         * llamada (ver `MusicRequestViewModel`), así que no cuesta tiempo de espera; aquí solo se usa
         * para ELEGIR entre las candidatas que ya se han traído. Null = sin personalizar, y entonces
         * el resultado es exactamente el orden de origen.
         */
        taste: iad1tya.echo.music.reco.TasteProfile? = null,
        /**
         * Fila 361 — needed to CHECK each song (Last.fm tags/listeners, iTunes genres, learned styles). Null =
         * no per-song check, exactly as before.
         */
        context: android.content.Context? = null,
    ): Produced? {
        // Fila 361: what was really asked (styles, faith, language, year, count, "sin…", "parecido a…").
        val intent = MusicRequestIntent.parse(prompt)
        timber.log.Timber.i(
            "MUSIC_REQUEST intent: styles=%s christian=%b lang=%s year=%b era=%s best=%b count=%s excluded=%d like=%b",
            intent.styles.sorted().joinToString("+").ifEmpty { "none" },
            intent.christian,
            intent.language ?: "none",
            intent.year != null,
            intent.era ?: "none",
            intent.best,
            intent.count ?: "none",
            intent.excludedStyles.size + intent.excludedTerms.size,
            intent.likeArtist != null,
        )
        val soloArtist = AiPlaylistConstraints.extractSoloArtist(intent.cleanPrompt)
        val startedAt = System.currentTimeMillis()
        val racedRaw = produceRacing(
            database, prompt, count, soloArtist, provider, apiKey, baseUrl, model, onResolveProgress, taste,
            context, intent,
        )
        // Fila 361: the AI's songs are checked like the search's (the search checks its own inside).
        val raced = if (racedRaw != null && racedRaw.fromAi && context != null) {
            val k = MusicRequestStyleGate.learn(
                context, racedRaw.songs.mapNotNull { it.artists.firstOrNull()?.name }, GATE_WAIT_MS,
            )
            val o = MusicRequestStyleGate.judgeWithFallback(
                intent,
                racedRaw.songs.map { mm ->
                    MusicRequestStyleGate.Cand(mm.id, mm.title, mm.album?.title, mm.artists.map { it.name })
                },
                verified = emptySet(),
                protectedIds = if (soloArtist != null) racedRaw.songs.map { it.id }.toSet() else emptySet(),
                k = k,
                target = count,
            )
            MusicRequestStyleGate.log("ai", intent, o)
            val keep = o.keptIds.toHashSet()
            racedRaw.copy(songs = racedRaw.songs.filter { it.id in keep })
        } else {
            racedRaw
        }
        // Fila 359 (dueño 2026-10-08): no instrumentals or karaoke tracks unless the request asks for them.
        // Here, on the way out, so the AI path, the search path and the background top-up all pass it.
        val produced = raced?.let { r ->
            val kept = MusicRequestInstrumental.filter(prompt, r.songs)
            if (kept.size != r.songs.size) {
                timber.log.Timber.i("MUSIC_REQUEST dropped %d instrumental/karaoke tracks", r.songs.size - kept.size)
            }
            if (kept.isEmpty()) null else r.copy(songs = kept)
        }
        timber.log.Timber.i(
            "MUSIC_REQUEST source=%s tracks=%d tookMs=%d",
            when {
                produced == null -> "none"
                produced.fromAi -> "ai"
                else -> "search"
            },
            produced?.songs?.size ?: 0,
            System.currentTimeMillis() - startedAt,
        )
        return produced
    }

    /**
     * 🔴 QUIEN ESTÉ LISTO, MANDA — la escalera de "pedir música", que corre en PARALELO en vez de en
     * fila (dueño, 2026-09-17: *"nunca funcionó […] y que el máximo de canciones que busque sean 10
     * para que responda más rápido"*).
     *
     * La escalera en fila de [produceInternal] es correcta para GUARDAR una playlist y es un desastre
     * para pedir música: primero agota hasta [AI_BUDGET_MS] (90 s) esperando a la IA y solo entonces
     * empieza a buscar — así que con el Worker lento o caído él se quedaba mirando un indicador
     * durante minuto y medio antes de ver nada. Eso es lo que vivió como "nunca funcionó".
     *
     * Aquí las dos rutas salen a la vez y gana **la que esté lista**, con el desempate a favor de la
     * IA:
     *  · la búsqueda tarda uno o dos segundos → normalmente responde ella, que es la velocidad que
     *    pidió;
     *  · si la IA contesta ANTES (pasa con clave propia y un proveedor rápido), manda la IA, que es
     *    el híbrido que él encargó — y se sigue sin decir cuál fue;
     *  · si la búsqueda vuelve vacía, entonces sí se espera a la IA hasta [MUSIC_REQUEST_AI_LEASH_MS],
     *    porque ahí esperar es la única opción que queda antes de decir "no encontré nada".
     *
     * [generate] NO usa esto: guardar una playlist de IA es otra promesa, más lenta a propósito, y él
     * no se ha quejado de ella.
     */
    private suspend fun produceRacing(
        database: MusicDatabase,
        prompt: String,
        target: Int,
        soloArtist: String?,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        onResolveProgress: (done: Int, total: Int) -> Unit,
        taste: iad1tya.echo.music.reco.TasteProfile?,
        context: android.content.Context?,
        intent: MusicRequestIntent.Intent,
    ): Produced? = coroutineScope {
        // Fila 361: when the request names something the search can CHECK on every song (a style, Christian
        // content, a language, an exclusion), the search answers and is checked; the AI — whose titles nobody
        // can verify — is only asked if the search finds nothing. Saves a slow Worker call (battery, data).
        val searchFirst = intent.checkable
        val aiJob = if (searchFirst) {
            null
        } else {
            async {
                runCatching {
                    withTimeoutOrNull(MUSIC_REQUEST_AI_LEASH_MS) {
                        aiFlow(database, prompt, target, soloArtist, provider, apiKey, baseUrl, model, onResolveProgress)
                    }
                }.getOrNull()
            }
        }
        val searchOutcome = try {
            withTimeoutOrNull(MUSIC_REQUEST_SEARCH_BUDGET_MS) {
                searchFallbackPlaylist(prompt, soloArtist, target, taste, context, intent)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.w("MUSIC_REQUEST search failed: %s", iad1tya.echo.music.utils.privacySafeSummary(e))
            null
        }
        val search = searchOutcome?.songs.orEmpty()
        if (searchOutcome == null) timber.log.Timber.w("MUSIC_REQUEST search gave no result (timeout or failure)")

        // QUE NO IMPROVISE (dueño, 2026-09-17), y aquí está la regla que lo decide entre las dos
        // rutas: cuando la petición trae algo **comprobable** — una década o un idioma — gana la ruta
        // que lo ha COMPROBADO. La búsqueda llega por una lista cuyo título nombra esa década/idioma
        // ([MusicRequestMatch]); la IA devuelve títulos y artistas sin año ni idioma marcado, así que
        // nadie puede verificar que sean de los 80 o que estén en inglés: con un modelo flojo, "80s"
        // se convierte en "lo que al modelo le suena a antiguo", e "inglés" en lo que el modelo haya
        // entendido. Sigue sin decirse cuál de las dos fue — el híbrido es de dónde salen las
        // canciones, no de qué se le cuenta a él.
        //
        // Auditoría del algoritmo (ronda 9): el tema cristiano/gospel NO se agrega aquí a propósito —
        // a diferencia de década/idioma, esa condición SÍ se puede comprobar sobre el resultado ya
        // resuelto de la propia IA (ver el gate en [resolveBounded]), así que un resultado temprano de
        // la IA ya viene filtrado y no hace falta preferir la búsqueda por esa razón.
        val parsedForRace = MusicRequestQuery.build(prompt)
        // Ronda 11 (dueño: "nunca me pone exactamente lo que pido"): una canción que él NOMBRÓ y la
        // búsqueda encontró tal cual también es comprobable — la IA propone títulos que habría que
        // volver a buscar, y no garantiza que el primero sea esa canción.
        // Ronda 12: igual con un artista que el buscador CONFIRMÓ ("hip hop de eminem"): la IA ve la frase
        // sin ese candado y puede proponer canciones de otros.
        val verifiable = parsedForRace.decade != null || parsedForRace.language != null ||
            searchOutcome?.pinnedSpecific == true || searchOutcome?.artistLocked == true
        if ((verifiable || searchFirst) && search.isNotEmpty()) {
            aiJob?.cancel()
            return@coroutineScope Produced(
                name = prompt.trim().take(MAX_NAME_LENGTH),
                songs = search,
                fromAi = false,
            )
        }

        // Desempate a favor de la IA: solo si YA terminó. Esperarla aquí sería volver a la fila.
        val aiEarly = if (aiJob != null && aiJob.isCompleted) runCatching { aiJob.await() }.getOrNull() else null
        if (aiEarly != null && aiEarly.songs.isNotEmpty()) {
            return@coroutineScope aiEarly
        }
        if (search.isNotEmpty()) {
            aiJob?.cancel()
            return@coroutineScope Produced(
                name = prompt.trim().take(MAX_NAME_LENGTH),
                songs = search,
                fromAi = false,
            )
        }
        // La búsqueda no dio nada: ahora sí merece la pena esperar a la IA hasta su correa.
        val aiLate = if (aiJob != null) {
            runCatching { aiJob.await() }.getOrNull()
        } else {
            runCatching {
                withTimeoutOrNull(MUSIC_REQUEST_AI_LEASH_MS) {
                    aiFlow(database, prompt, target, soloArtist, provider, apiKey, baseUrl, model, onResolveProgress)
                }
            }.getOrNull()
        }
        if (aiLate != null && aiLate.songs.isNotEmpty()) aiLate else null
    }

    /**
     * La cadena en sí: IA primero, búsqueda si la IA no está o no sirvió. Compartida por [generate] y
     * [produce] a propósito — dos copias de esta escalera acabarían divergiendo en el peldaño que
     * menos se prueba, que es justo el de la caída.
     */
    private suspend fun produceInternal(
        database: MusicDatabase,
        prompt: String,
        target: Int,
        soloArtist: String?,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        onResolveProgress: (done: Int, total: Int) -> Unit,
    ): Produced? {
        // UNA ventana de presupuesto para TODA la fase de IA (pedir + resolver + rellenar), directiva
        // del dueño 2026-08-31 ("las respuestas de IA son MUY lentas"). El código viejo abría una
        // ventana FRESCA por fase, así que pedir + rellenar podían sumar ~120 s mientras su propio
        // comentario decía "el MISMO presupuesto de 60 s" — el comentario era un placebo.
        val ai = withTimeoutOrNull(AI_BUDGET_MS) {
            aiFlow(database, prompt, target, soloArtist, provider, apiKey, baseUrl, model, onResolveProgress)
        }
        if (ai != null) return ai

        // RED DE SEGURIDAD SIN IA (directiva 2026-08-29: "la IA nunca puede terminar en un error").
        // Se llega aquí cuando el presupuesto expiró, la cadena de IA no está disponible (Worker
        // limitado, sin clave del usuario) o sus temas no resolvieron. Canciones REALES de la búsqueda
        // de YouTube Music para su descripción — el mismo enfoque sin LLM que usan InnerTune,
        // OuterTune y Metrolist para sus playlists automáticas.
        val fallback = withTimeoutOrNull(AI_BUDGET_MS) {
            searchFallbackPlaylist(prompt, soloArtist, target)
        }?.songs
        if (fallback.isNullOrEmpty()) return null
        return Produced(
            name = prompt.trim().take(MAX_NAME_LENGTH),
            songs = fallback,
            fromAi = false,
        )
    }

    suspend fun generate(
        database: MusicDatabase,
        prompt: String,
        count: Int,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        onResolveProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): kotlin.Result<Result> {
        val target = count
        val soloArtist = AiPlaylistConstraints.extractSoloArtist(prompt)

        // La escalera (IA → búsqueda) vive en [produceInternal], compartida con [produce]. Lo que
        // queda aquí es lo propio de GUARDAR una playlist: la etiqueta honesta del origen y la
        // transacción.
        val produced = produceInternal(
            database, prompt, target, soloArtist, provider, apiKey, baseUrl, model, onResolveProgress,
        ) ?: return kotlin.Result.failure(EmptyResultException())

        // ETIQUETA PERSISTENTE DEL ORIGEN (directiva del dueño 2026-09-03: "cuando las crea no se basa
        // en lo que pido"): una playlist GUARDADA que no se parece a lo que pidió quedaba en la
        // biblioteca indistinguible de una curada, y eso es justo por lo que un resultado flojo se leía
        // como "la IA me ignoró". El prompt se recorta a MAX_NAME_LENGTH menos los 9 caracteres de la
        // etiqueta para que "(sin IA)" sobreviva SIEMPRE al corte (hallazgo #4 de la auditoría
        // adversarial: añadir primero y recortar después la borraba en los prompts largos, que es el
        // caso donde más probable es caer a la búsqueda).
        //
        // Esta etiqueta es de [generate] y NO de [produce] — ver el KDoc de [produce] para el por qué.
        val name = if (produced.fromAi) {
            produced.name
        } else {
            prompt.trim().ifBlank { prompt }.take(MAX_NAME_LENGTH - 9) + " (sin IA)"
        }
        val playlist = PlaylistEntity(
            name = name,
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
            isLocal = true,
        )
        // Single transaction: create the playlist, persist songs, map them in order (atomic).
        database.transaction {
            insert(playlist)
            produced.songs.forEachIndexed { index, metadata ->
                insert(metadata)
                insert(
                    PlaylistSongMap(
                        playlistId = playlist.id,
                        songId = metadata.id,
                        position = index,
                    ),
                )
            }
        }
        return kotlin.Result.success(
            Result(
                playlistId = playlist.id,
                name = name,
                total = target,
                resolved = produced.songs.size,
                generatedWithoutAi = !produced.fromAi,
            ),
        )
    }

    /**
     * The AI-backed path, in the caller's [AI_BUDGET_MS] window: ask → parallel resolve →
     * conditional top-up. Returns null ONLY when nothing AI-usable survived; the caller then runs
     * the honest non-AI fallback.
     *
     * Devuelve [Produced] y **no guarda nada**: quién persiste (o no) es decisión del llamante —
     * [generate] crea la playlist, [produce] solo pone a sonar.
     */
    private suspend fun aiFlow(
        database: MusicDatabase,
        prompt: String,
        target: Int,
        soloArtist: String?,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        onResolveProgress: (done: Int, total: Int) -> Unit,
    ): Produced? {
        // Ask the AI (user key → Aura Worker). getOrNull() so a total
        // failure doesn't dead-end — the caller falls back to a non-AI playlist, not an error.
        // Thin pad (target + PAD_OVER_TARGET) instead of the old 1.5×: the row-198 anti-hallucination
        // prompt returns fewer but REAL tracks, so a big pad only bought extra output tokens
        // (≈ direct seconds on Llama 70B, the slowest leg) and made every answer slower for nothing.
        // The rare shortfall is the structured top-up's job, below.
        val requestCount = target + PAD_OVER_TARGET
        val flowStartedAt = System.currentTimeMillis()
        // Ask cap, BY PATH: the KEYLESS chain (the owner's path) gets the scaled [keylessAskCapMs]
        // — a hung Worker must fail fast into the instant non-AI fallback (2026-08-31: no dead
        // spinners), but the cap must still cover a legit 30-track ask or it would trade precision
        // for speed (see keylessAskCapMs). The USER KEY path keeps the full [AI_BUDGET_MS] window
        // for the ask: their provider may be deliberately slow, and "user key = full control"
        // (row 198 invariant) is not the place to harvest seconds.
        val askCapMs = if (apiKey.isBlank()) keylessAskCapMs(requestCount) else AI_BUDGET_MS
        val spec = withTimeoutOrNull(askCapMs) {
            AiPlaylistService.generate(prompt, requestCount, provider, apiKey, baseUrl, model)
                .onFailure { timber.log.Timber.w(it, "AI playlist: AI request failed (keyless=%b)", apiKey.isBlank()) }
                .getOrNull()
        } ?: run {
            // Owner report 2026-09-14 ("dice que fue hecha sin IA"): the reason was never logged.
            timber.log.Timber.w("AI playlist: no AI answer within %d ms -> non-AI fallback", askCapMs)
            return null
        }

        // Auditoría del algoritmo (ronda 9): el resto del generador trata el tema cristiano/gospel
        // como rechazo duro cuando se pide explícitamente (MusicRequestMoods.requiresChristianContent),
        // pero esta ruta (con IA) nunca lo comprobaba — solo se lo pedía al modelo en el prompt, sin
        // verificar después. Ver el gate en [resolveBounded].
        val requireChristian = MusicRequestMoods.requiresChristianContent(prompt)
        val proposed = filterTracksForSoloArtist(spec.tracks, soloArtist)
        val firstPass = resolveBounded(database, proposed, soloArtist, target, requireChristian, onResolveProgress)
        var ordered = firstPass.distinctBy { it.id }.take(target)

        // Top-up ONLY when the first pass fell short of the target. With the thin pad and the
        // row-198 prompt the first pass reaches the target on most requests, so this second Worker
        // hop (10-17s + its own resolve) is now the exception, not the rule. It still runs INSIDE
        // the same budget window (the caller's single withTimeoutOrNull), never a second 60s.
        // The exclusions travel as a structured list (AiPlaylistPrompt), NOT concatenated into the
        // prompt: a literal "solo X. NO incluyas…: A, B, C" is not re-parseable by
        // extractSoloArtist, so the solo lock silently died on this round (owner wants EXACTNESS).
        val timeLeftMs = AI_BUDGET_MS - (System.currentTimeMillis() - flowStartedAt) - RESOLVE_RESERVE_MS
        if (ordered.size < target && timeLeftMs >= MIN_TOP_UP_MS) {
            val missing = target - ordered.size
            val exclude = ordered.map { it.title }
            // Same thin pad on the refill round: only what's missing plus the same cushion.
            val extra = withTimeoutOrNull(minOf(askCapMs, timeLeftMs)) {
                AiPlaylistService.generate(
                    prompt = prompt,
                    count = missing + PAD_OVER_TARGET,
                    provider = provider,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    excludeTitles = exclude,
                ).getOrNull()
            }
            if (extra != null) {
                val extraTracks = filterTracksForSoloArtist(extra.tracks, soloArtist)
                // Progress offset: the second wave's callback counts only ITS OWN results; add the
                // first pass's distinct count so the UI counter never runs BACKWARDS mid-refill.
                val baseCount = ordered.distinctBy { it.id }.size
                val secondPass = resolveBounded(
                    database = database,
                    proposed = extraTracks,
                    soloArtist = soloArtist,
                    target = missing,
                    requireChristian = requireChristian,
                    onResolveProgress = { done, _ ->
                        onResolveProgress((baseCount + done).coerceAtMost(target), target)
                    },
                )
                ordered = (firstPass + secondPass).distinctBy { it.id }.take(target)
            }
        }

        if (ordered.isEmpty()) {
            timber.log.Timber.w("AI playlist: %d AI tracks, none found in the catalogue -> non-AI fallback", proposed.size)
            return null
        }

        // The AI proposes a short name; fall back to the user's prompt (also used for the non-AI
        // playlist) when the model omitted or blanked it.
        val name = spec.name.ifBlank { prompt }.trim().ifBlank { prompt }.take(MAX_NAME_LENGTH)
        return Produced(name = name, songs = ordered, fromAi = true)
    }

    /**
     * Bounded-concurrency resolve of the AI proposals. The old loop was strictly serial — 12 tracks
     * ≈ 12 sequential YouTube searches ≈ 12s of spinner AFTER the AI reply, the second-slowest leg of
     * the chain. With [RESOLVE_CONCURRENCY] lanes the same work lands in ~¼ of the wall time while
     * the modem burst stays small (battery/heat rule).
     *
     * ORDER IS PRESERVED: results are collected by proposal index, so the playlist keeps the AI's
     * ordering. Parallelism never changes WHICH songs resolve — [SongResolver.resolve] is a pure
     * per-track lookup (local match first, then search), independent of the other tracks.
     *
     * Cancellation semantics are deliberately HONEST: no catch here. If the shared budget window
     * dies mid-wave, the wave is cancelled, `withTimeoutOrNull` in the caller yields null, and the
     * instant non-AI fallback runs — exactly what that fallback exists for. (Swallowing the
     * cancellation to "save a partial list" would mean persisting inside a cancelled scope, which
     * requires NonCancellable surgery for no real gain: the fallback already answers instantly.)
     */
    private suspend fun resolveBounded(
        database: MusicDatabase,
        proposed: List<TrackQuery>,
        soloArtist: String?,
        target: Int,
        // Auditoría del algoritmo (ronda 9): la ruta de IA (aiFlow) no aplicaba NINGUNO de los
        // rechazos duros que sí aplica la ruta de búsqueda (searchFallbackPlaylist) — ni tema
        // cristiano, ni idioma. Un LLM puede "olvidar" una instrucción del prompt igual que el
        // buscador puede devolver ruido; el prompt a la IA (AiPlaylistPrompt) le PIDE que respete
        // tema/idioma, pero pedir no es comprobar. requireChristian sí se puede comprobar de verdad
        // sobre el título/artista YA RESUELTO (real, del catálogo) — mismo mecanismo que
        // [searchFallbackPlaylist]'s christianOnly, aplicado aquí en el gate de aceptación para que
        // el top-up de abajo compense naturalmente lo que se rechace, igual que ya hace con el
        // artista. El idioma NO se agrega aquí: no hay una señal fiable de idioma por canción (a
        // diferencia del tema, que SÍ tiene palabras reconocibles en título/artista) — inventar un
        // detector de idioma sería la misma improvisación que esto existe para evitar.
        requireChristian: Boolean,
        onResolveProgress: (done: Int, total: Int) -> Unit,
    ): List<MediaMetadata> = resolveBoundedOrdered(
        proposed = proposed,
        resolveArtistFor = { track -> soloArtist?.takeIf { it.isNotBlank() } ?: track.artist },
        resolveOne = { title, artist -> SongResolver.resolve(database, title, artist) },
        accept = { mm ->
            acceptsResolvedSoloPrimary(mm, soloArtist) && (!requireChristian || looksChristianResolved(mm))
        },
        target = target,
        concurrency = RESOLVE_CONCURRENCY,
        onResolveProgress = onResolveProgress,
    )

    /** Igual que [MusicRequestMoods.looksChristian] pero sobre un [MediaMetadata] ya resuelto. */
    private fun looksChristianResolved(mm: MediaMetadata): Boolean =
        MusicRequestMoods.looksChristian(mm.title) || mm.artists.any { MusicRequestMoods.looksChristian(it.name) }

    /**
     * Pure orchestration core of [resolveBounded], with every effect injected (the project's
     * [SongResolver.resolveOrdered] seam pattern) so the PARALLEL-RESOLVE CONTRACT is unit-tested
     * (AiPlaylistResolveBoundedTest) instead of trusting "it compiles":
     *  - order follows the AI's proposal order regardless of completion order;
     *  - concurrency is bounded ([concurrency] lanes — battery/heat rule);
     *  - soft short-circuit: once [target] results are accepted, waiting lanes skip their network hit
     *    (the old serial loop's early break, preserved);
     *  - the progress callback fires per completion, monotonically capped at [target].
     *
     * No cancellation swallowing: if the surrounding budget window dies, the wave is cancelled and
     * the caller's withTimeoutOrNull yields null → honest non-AI fallback.
     */
    internal suspend fun resolveBoundedOrdered(
        proposed: List<TrackQuery>,
        resolveArtistFor: (TrackQuery) -> String,
        resolveOne: suspend (title: String, artist: String) -> MediaMetadata?,
        accept: (MediaMetadata) -> Boolean,
        target: Int,
        concurrency: Int = RESOLVE_CONCURRENCY,
        onResolveProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<MediaMetadata> {
        if (proposed.isEmpty() || target <= 0) return emptyList()
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        val byIndex = ConcurrentHashMap<Int, MediaMetadata>()
        // Distinct RESOLVED ids (not accepted entries): two different proposals can resolve to the
        // same video — the old serial loop counted distinct ids, and so does the short-circuit.
        val seenIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        coroutineScope {
            proposed.forEachIndexed { index, track ->
                launch {
                    semaphore.withPermit {
                        // Soft short-circuit (checked under the permit): enough DISTINCT results are
                        // in → this lane skips its network hit. Checked AFTER acquiring so a wave
                        // that already reached target does zero extra searches.
                        if (seenIds.size >= target) return@withPermit
                        val resolveArtist = resolveArtistFor(track)
                        val resolved = resolveOne(track.title, resolveArtist)
                        if (resolved != null && accept(resolved)) {
                            byIndex[index] = resolved
                            seenIds.add(resolved.id)
                        }
                        onResolveProgress(seenIds.size.coerceAtMost(target), target)
                    }
                }
            }
        }
        return proposed.indices.mapNotNull { byIndex[it] }
    }

    /**
     * The non-AI safety net behind the AI chain: REAL songs from YouTube Music search for the user's
     * description, so "AI playlists" never dead-end on "servicio ocupado" (owner directive
     * 2026-08-29). This is the no-LLM approach InnerTune/OuterTune/Metrolist use for auto playlists:
     * the search itself IS the recommender.
     *
     * La escalera, de más fiable a menos, y todo de-duplicado por id: **categoría oficial** de
     * YouTube Music ([MusicRequestMoods] — la categoría ES la prueba) → **listas de la búsqueda que
     * demuestran por título** que corresponden ([MusicRequestMatch], las dos mejores) → canciones
     * sueltas → vídeos, que solo se tocan si no hay nada. Cada peldaño no llena el cupo: llena un
     * MONTÓN ([POOL_TARGET]) del que al final se ELIGE ([MusicRequestRanking]) con el gusto del
     * usuario empujando sobre el orden de origen. Todo lo que sale es un [SongItem] real y reproducible
     * del mismo catálogo que ya suena en la app.
     */
    private suspend fun searchFallbackPlaylist(
        rawPrompt: String,
        soloArtist: String?,
        target: Int,
        taste: iad1tya.echo.music.reco.TasteProfile? = null,
        context: android.content.Context? = null,
        intent: MusicRequestIntent.Intent = MusicRequestIntent.parse(rawPrompt),
    ): SearchOutcome {
        // Fila 361: everything below works on the request WITHOUT its "sin X", count, year and "parecido a X"
        // parts ([MusicRequestIntent.cleanPrompt]) — "rock sin reggaeton" used to search "reggaeton".
        val prompt = intent.cleanPrompt
        // Fila 364 (dueño 2026-10-09: "pedí trap cristiano y dice que no encontró nada"): the whole search runs
        // under MUSIC_REQUEST_SEARCH_BUDGET_MS, and a timeout THREW AWAY everything already found. The network
        // steps now stop at their own deadline, so the check and the ranking below always get the pool they have.
        val searchDeadline = System.currentTimeMillis() + SEARCH_STEPS_BUDGET_MS
        fun inBudget(): Boolean = System.currentTimeMillis() < searchDeadline
        val parsed = MusicRequestQuery.build(prompt)
        val query = parsed.query.ifBlank { prompt.trim().take(80) }
        if (query.isBlank()) return SearchOutcome(emptyList(), pinnedSpecific = false)

        // UN MONTÓN Y LUEGO ELEGIR (dueño, 2026-09-17). Antes se aceptaban canciones por orden hasta
        // llenar el cupo, así que la primera fuente que pasara el filtro decidía el resultado entero.
        // Una sola página de lista ya trae cincuenta y pico, o sea que juntar candidatas NO cuesta ni
        // una llamada más — lo que cambia es que al final se puede elegir. Ver [MusicRequestRanking].
        val pool = ArrayList<SongItem>()
        val seen = HashSet<String>()
        // Ronda 12 (dueño: "hip hop de eminem" → solo la primera era de Eminem). El artista único puede
        // venir de la frase ([AiPlaylistConstraints.extractSoloArtist], "música de X") o confirmarse más
        // abajo con lo que devuelve el buscador ([MusicRequestArtist.confirm], "hip hop de X"). Todo lo
        // que filtra por artista lee ESTA variable, no el parámetro.
        var effectiveSolo: String? = soloArtist
        // Ronda 12 (dueño: "afro gospel"): palabras que califican al género y que la categoría o la lista
        // elegida tienen que nombrar también (ver [MusicRequestQuery.residualWords]).
        var qualifiers: List<String> = emptyList()
        // Ronda 10 (dueño: "cuando pida un nombre de artista con el nombre de la canción" debe
        // reproducir ESO específicamente cada vez, a diferencia de un pedido temático). Id de la
        // canción exacta que el peldaño -1 (bestSpecificMatch) confirma que el usuario nombró — si
        // existe, [MusicRequestRecents] nunca debe poder excluirla más abajo solo porque ya sonó en
        // un pedido anterior: es precisamente lo que está pidiendo otra vez.
        var specificMatchId: String? = null
        // Ronda 9 (dueño): pedir una canción o artista específico devolvía 20-25 copias/variantes de
        // LA MISMA canción — distintas subidas ("Official Video", "Lyrics", "Audio")— porque el
        // dedup solo miraba el id de YouTube, y cada subida tiene el suyo propio. Deduplicar TAMBIÉN
        // por título normalizado + artista principal hace que dos subidas de la misma canción cuenten
        // como una sola candidata, dejando sitio real para el resto de la cola.
        val seenTitles = HashSet<String>()
        fun absorb(items: List<SongItem>) {
            for (item in items) {
                if (pool.size >= POOL_TARGET) return
                val titleKey = dedupKey(item.title, item.artists.firstOrNull()?.name)
                if (seen.add(item.id) &&
                    seenTitles.add(titleKey) &&
                    soloPrimaryMatch(item.artists.map { it.name }, effectiveSolo)
                ) {
                    pool += item
                }
            }
        }

        // Ronda 9 (dueño): "pedí reggae cristiano y me salió un artista que no es cristiano... si al
        // final va la palabra cristiano, tiene que respetar eso sí o sí". Las listas de los peldaños
        // 1-2 ya se verifican por TÍTULO DE LISTA ([MusicRequestMatch]/[MusicRequestMoods]) — la lista
        // entera es la prueba, así que filtrar cada canción suya otra vez por su propio título sería
        // incorrecto (una canción cristiana real casi nunca dice "cristiano" en su propio título).
        // Solo los peldaños 3-4 (canciones/vídeos sueltos del buscador) no pasan por ninguna
        // verificación hoy — ahí sí hace falta este filtro. Solo se activa si la petición lo
        // menciona: sin la palabra, sigue sin haber ningún filtro extra ("puede poner lo que
        // considere mejor").
        val requireChristian = MusicRequestMoods.requiresChristianContent(prompt) || intent.christian
        // Fila 361: the words alone dropped most real Christian songs (Redimi2, Alex Campos say neither "Dios"
        // nor "Jesús" in their titles). An artist iTunes files as Christian & Gospel, or whose Last.fm tags say
        // worship/gospel, counts too. Snapshots read once, lazily (memory reads, no network).
        val christianGenres by lazy {
            context?.let { runCatching { iad1tya.echo.music.reco.GenreCache.snapshot(it) }.getOrNull() }.orEmpty()
        }
        val christianTags by lazy {
            context?.let { runCatching { iad1tya.echo.music.reco.ArtistTagStyles.snapshot(it) }.getOrNull() }.orEmpty()
        }
        fun christianArtist(name: String): Boolean {
            if (iad1tya.echo.music.reco.GenreLane.laneOfTrack(christianGenres, name, null, null) ==
                iad1tya.echo.music.reco.GenreLane.CHRISTIAN
            ) {
                return true
            }
            val tag = iad1tya.echo.music.reco.ArtistStyleMemory.key(name)?.let { christianTags[it] }
            return tag == iad1tya.echo.music.reco.MusicStyle.WORSHIP || tag == "gospel"
        }
        fun christianOnly(items: List<SongItem>): List<SongItem> =
            if (!requireChristian) {
                items
            } else {
                items.filter { item ->
                    MusicRequestMoods.looksChristian(item.title) ||
                        item.artists.any { MusicRequestMoods.looksChristian(it.name) || christianArtist(it.name) }
                }
            }

        // Peldaño -1 — LO ESPECÍFICO GANA AL GÉNERO (ronda 9, dueño: "pedí death metal más el nombre
        // de una canción y de un artista, y reprodujo lo que quiso, no lo que pedí — quiero que
        // entienda cuando soy específico, con cualquier género"). Sin este paso, cuando la petición
        // también dispara [Parsed.preferPlaylists] (un género/momento reconocido), los peldaños 1-2
        // de abajo (categoría oficial / listas genéricas) llenaban el pool ANTES de que el peldaño 3
        // (búsqueda literal — el único que de verdad busca lo específico) llegara siquiera a correr,
        // así que lo concreto que pidió junto al género nunca aparecía. [residualBeyondCategory] es
        // lo que sobra de la petición tras quitar género/momento/década/idioma — si sobra algo
        // sustancial, es la canción/artista que mencionó, y se busca y antepone antes que nada.
        //
        // NO se exige soloArtist == null (ronda 9, siguiente reporte del dueño: seguía sin pasar
        // "cuando pido canciones en específico"). Nombrar un artista explícito ("de Queen") es
        // justamente la forma más común de ser específico, y [AiPlaylistConstraints.extractSoloArtist]
        // reconociéndolo NO debe apagar este peldaño — al revés, hacía que este mismo arreglo nunca
        // corriera para el caso que más lo necesita: género + artista + canción, los tres juntos.
        // residualBeyondCategory no quita nombres de artista, así que el residuo sigue teniendo la
        // canción/artista con la que bestSpecificMatch compara.
        //
        // Ronda 11 (dueño: "pido Redimi2 Flipando y me reproduce Blindao de Redimi2 ... y nunca me
        // pone exactamente lo que pido — ya lo he pedido más de 10 veces"). Tres huecos cerrados:
        //  · este peldaño solo corría con `preferPlaylists` (un género/momento en la frase). "redimi2
        //    flipando" no nombra ninguno, así que la canción exacta NUNCA se reconocía como pedida;
        //  · sin reconocerla, [MusicRequestRecents] la EXCLUÍA en cada pedido repetido (TTL 45 min) —
        //    justo lo que vivió al pedirla una y otra vez: salía cualquier otra de Redimi2;
        //  · aun reconocida, entraba al montón y [MusicRequestRanking] podía moverla (gusto, separación
        //    por artista). Ahora va FIJA en el primer puesto, fuera del ranking, y detrás su propia radio
        //    — "más como esto", que es lo que YouTube Music hace al tocar una canción.
        val residual = MusicRequestQuery.residualBeyondCategory(prompt)
        var pinned: SongItem? = null
        // La misma búsqueda de canciones que el peldaño 3 haría con `query`: se reutiliza, no se repite.
        var songSearchCache: List<SongItem>? = null
        // Ronda 12: true cuando el resto de la petición resultó ser un ARTISTA (ver [MusicRequestArtist]).
        var artistLocked = false
        if (residual.length >= 3) {
            // Con género/momento se busca la frase entera (como antes); sin ellos, la consulta limpia
            // de muletillas — "ponme" no ayuda al buscador a encontrar una canción.
            val specificQuery = if (parsed.preferPlaylists) prompt.trim().take(80) else query
            val candidates = YouTube.search(specificQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>()
                ?.also { if (specificQuery == query) songSearchCache = it }
                .orEmpty()
            // Ronda 12 (dueño: "pedí hip hop de Eminem y solo la primera fue de Eminem; lo demás era de
            // otro cantante"). Antes de buscar una CANCIÓN con ese nombre, ¿el resto es un ARTISTA? Lo
            // decide el propio buscador: el resto tiene que ser exactamente el artista principal de
            // varias de las canciones que devolvió ([MusicRequestArtist.confirm]), o el artista único
            // que la frase ya nombró ("música de Eminem" deja "eminem" de resto, y fijar una canción con
            // "Eminem" en el título era el mismo fallo). Entonces no se fija nada: TODO lo que suena es
            // de ese artista (filtro de artista principal, el mismo de "solo X"), y las categorías
            // genéricas del género no se tocan — hip hop de cualquiera no es lo que pidió.
            val confirmedArtist = MusicRequestArtist.confirm(
                residual,
                candidates.map { it.artists.firstOrNull()?.name },
            ) ?: soloArtist?.takeIf { MusicRequestArtist.sameName(it, residual) }
            val residualWords = MusicRequestQuery.residualWords(residual)
            when {
                confirmedArtist != null -> {
                    artistLocked = true
                    if (effectiveSolo == null) effectiveSolo = confirmedArtist
                    // Lo que el buscador devolvió para la frase entera ("hip hop de eminem") va primero: es
                    // lo más cercano a lo que pidió dentro del catálogo de ese artista.
                    absorb(christianOnly(candidates))
                }
                // Ronda 12 (dueño: "afro gospel" → una canción con "Afro" en el título y luego gospel de
                // cualquiera). Una sola palabra junto a un género o momento casi nunca es una canción: es
                // lo que CALIFICA al género. Solo se fija si el título de una canción ES esa palabra
                // ("reggaeton gasolina" → "Gasolina"); si no, pasa a ser obligatoria para la categoría y
                // la lista (más abajo), en vez de fijar cualquier título que la contenga.
                parsed.preferPlaylists && residualWords.size == 1 -> {
                    val exact = exactTitleMatch(residual, candidates)
                    if (exact != null) {
                        pinned = exact
                    } else {
                        qualifiers = residualWords
                    }
                }
                else -> {
                    pinned = bestSpecificMatch(residual, candidates)
                        ?.takeIf { soloPrimaryMatch(it.artists.map { a -> a.name }, effectiveSolo) }
                    // Varias palabras junto a un género que no nombran ninguna canción ("salsa de puerto
                    // rico"): también califican al género.
                    if (pinned == null && parsed.preferPlaylists) qualifiers = residualWords
                }
            }
            pinned?.let { match ->
                specificMatchId = match.id
                // Marcada como vista para que ningún peldaño la vuelva a meter en el montón.
                seen.add(match.id)
                seenTitles.add(dedupKey(match.title, match.artists.firstOrNull()?.name))
                YouTube.next(WatchEndpoint(videoId = match.id, playlistId = "RDAMVM${match.id}"))
                    .getOrNull()?.items
                    ?.filter { it.id != match.id }
                    ?.let { absorb(christianOnly(it)) }
            }
        }
        // Otras versiones de la canción fijada (remix, en vivo, otra subida, a nombre de otro artista)
        // no pueden seguirla — salvo que él haya pedido justo una versión ("flipando remix").
        val pinnedBase = pinned?.let { baseTitleKey(it.title) }
        val wantsVersion = residual.split(Regex("[^\\p{L}\\p{N}]+")).any { it in VERSION_WORDS }

        // Peldaño S (fila 361) — LISTAS HECHAS POR PERSONAS QUE DEMUESTRAN EL ESTILO PEDIDO. "Merengue cristiano
        // 2026" busca listas con esas palabras y solo usa las que su TÍTULO prueba que son de ese estilo (y
        // cristianas, si se pidió); sus canciones cuentan como del estilo en la comprobación por canción. Va
        // antes que las categorías genéricas: es lo más exacto que hay para un estilo concreto.
        val styleVerified = HashSet<String>()
        if (intent.styles.isNotEmpty() && effectiveSolo == null && pinned == null) {
            for (style in intent.styles.take(2)) {
                if (pool.size >= POOL_TARGET || !inBudget()) break
                val q = MusicRequestIntent.styleQuery(intent, style) ?: continue
                val found = ArrayList<PlaylistItem>()
                YouTube.search(q, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                    ?.items?.filterIsInstance<PlaylistItem>()?.take(PLAYLIST_CANDIDATES)?.let { found += it }
                YouTube.search(q, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()
                    ?.items?.filterIsInstance<PlaylistItem>()?.take(PLAYLIST_CANDIDATES)?.let { found += it }
                val year = intent.year?.toString()
                found
                    .filter { MusicRequestIntent.playlistMatches(it.title, style, intent.christian) }
                    .distinctBy { it.id }
                    .sortedByDescending { year != null && it.title.contains(year) }
                    .take(PLAYLISTS_USED + 1)
                    .forEach { pl ->
                        if (pool.size >= POOL_TARGET) return@forEach
                        YouTube.playlist(pl.id).getOrNull()?.songs?.let { songs ->
                            val before = pool.size
                            absorb(songs.shuffled())
                            for (idx in before until pool.size) styleVerified.add(pool[idx].id)
                        }
                    }
            }
        }
        // Peldaño P (fila 361) — "PARECIDO A X": la radio de una canción de X, con X solo una o dos veces (pidió
        // algo como X, no a X).
        intent.likeArtist?.let { like ->
            if (pool.size < POOL_TARGET && inBudget()) {
                val seedSong = YouTube.search(like, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
                    ?.filterIsInstance<SongItem>()
                    ?.firstOrNull { song -> song.artists.any { MusicRequestArtist.sameName(it.name, like) } }
                if (seedSong != null) {
                    val radio = YouTube.next(WatchEndpoint(videoId = seedSong.id, playlistId = "RDAMVM${seedSong.id}"))
                        .getOrNull()?.items.orEmpty()
                    val byLike = radio.filter { song -> song.artists.any { MusicRequestArtist.sameName(it.name, like) } }
                    absorb(christianOnly(radio.filterNot { it in byLike } + byLike.take(1)))
                }
            }
        }

        if (parsed.preferPlaylists && effectiveSolo == null && inBudget()) {
            // Peldaño 0 — TENDENCIAS REALES (ronda 6, dueño: "lo que suena ahora"). Mismo espíritu que
            // el peldaño 1: no se le pide a la búsqueda ni a un LLM que ADIVINE qué está de moda —
            // se piden los charts reales de YouTube Music, que es la única fuente que de verdad lo
            // sabe. Si el usuario no pidió tendencias, esto no hace ninguna llamada de más.
            //
            // christianOnly aquí también (auditoría del algoritmo, ronda 9): el chart de tendencias
            // no tiene forma de saber si pediste tema cristiano — a diferencia de una lista curada
            // por título (peldaños 1-2), donde SÍ se confía en que el título es la prueba, un chart
            // general no demuestra nada sobre tema. Sin la palabra clave, sigue sin filtrar nada.
            if (parsed.trending && pool.size < POOL_TARGET) {
                absorb(christianOnly(trendingSongs()))
            }

            // Peldaño 1 — EL CATÁLOGO PROPIO DE YOUTUBE MUSIC. Sus categorías ("Años 80",
            // "Concentración") entregan listas editoriales suyas, y ahí la categoría ES la prueba para
            // década/género/momento — pickCategory ya exige que el título de la categoría los
            // demuestre. Pero el TEMA CRISTIANO no forma parte de esa prueba salvo que exista una
            // categoría dedicada ("Bachata Cristiana"): una categoría genérica que matcheó por
            // género/década (p. ej. "Bachata") no garantiza que cada canción suya sea cristiana, así
            // que christianOnly se aplica igual que en el resto de peldaños sin verificación de tema.
            moodCategoryPlaylists(prompt, parsed, qualifiers).take(PLAYLISTS_USED).forEach { pl ->
                if (pool.size < POOL_TARGET) {
                    YouTube.playlist(pl.id).getOrNull()?.songs?.let { absorb(christianOnly(it)) }
                }
            }

            // Peldaño 2 — listas de la búsqueda, y solo las que DEMUESTRAN por título que
            // corresponden ([MusicRequestMatch]). Ahora se usan las DOS mejores en vez de una: dos
            // curadores distintos dan más variedad que uno, y las dos han pasado la misma prueba.
            if (pool.size < POOL_TARGET) {
                val candidates = ArrayList<PlaylistItem>()
                YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                    ?.items?.filterIsInstance<PlaylistItem>()?.take(PLAYLIST_CANDIDATES)
                    ?.let { candidates += it }
                YouTube.search(query, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()
                    ?.items?.filterIsInstance<PlaylistItem>()?.take(PLAYLIST_CANDIDATES)
                    ?.let { candidates += it }
                val conceptGroups = MusicRequestMoods.conceptGroupsFor(prompt, parsed, qualifiers)
                MusicRequestMatch.rankedIndices(candidates.map { it.title }, parsed, conceptGroups)
                    .take(PLAYLISTS_USED)
                    .forEach { index ->
                        if (pool.size < POOL_TARGET) {
                            YouTube.playlist(candidates[index].id).getOrNull()?.songs?.let { absorb(it) }
                        }
                    }
            }
        }

        // Peldaño 3 — canciones sueltas del buscador (siempre, si todavía no hay nada).
        if (pool.size < POOL_TARGET && (inBudget() || pool.isEmpty())) {
            (songSearchCache ?: YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>())?.let { absorb(christianOnly(it)) }
        }
        // Peldaño 3b — EL CATÁLOGO DEL ARTISTA (ronda 12). Con el artista confirmado, una búsqueda por su
        // nombre trae el resto de sus canciones: más donde elegir y otra lista distinta si vuelve a pedir
        // lo mismo ([MusicRequestRecents]). Una sola petición más, y solo en este caso.
        if (artistLocked && pool.size < POOL_TARGET && inBudget()) {
            effectiveSolo?.let { artist ->
                YouTube.search(artist, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    ?.items?.filterIsInstance<SongItem>()?.let { absorb(christianOnly(it)) }
            }
        }
        // Peldaño 4 — los VÍDEOS son lo menos fiable (recopilaciones de una hora, versiones de
        // aficionado): en una petición de época o momento solo se tocan si no hay NADA.
        val videosAllowed = (if (parsed.preferPlaylists) pool.isEmpty() else pool.size < target) &&
            (inBudget() || pool.isEmpty())
        if (videosAllowed) {
            YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.let { absorb(christianOnly(it)) }
        }
        // Red de seguridad: si la consulta construida no dio nada, se prueba su petición TAL CUAL.
        if (pool.isEmpty() && query != prompt.trim().take(80)) {
            YouTube.search(prompt.trim().take(80), YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.let { absorb(christianOnly(it)) }
        }

        // Peldaño 5 — MÁS DEL MISMO ARTISTA (ronda 9, dueño: "si pido una canción o artista
        // específico, que la cola que sigue no sean 20-25 copias con el mismo nombre — que sea más
        // del mismo artista o género relacionado, para que se sienta inteligente"). Una búsqueda de
        // UNA canción/artista concretos no arrastra tantos resultados distintos como un género — el
        // pool queda corto — y lo poco que sí aparece es casi todo del MISMO artista: exactamente la
        // señal de que era eso lo que pidió. En vez de dejar que [MusicRequestRanking] rellene
        // REPITIENDO lo poco que hay, se completa con más canciones REALES de ese mismo artista.
        if (pool.size < target && inBudget()) {
            dominantArtist(pool)?.let { artist ->
                YouTube.search(artist, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    ?.items?.filterIsInstance<SongItem>()?.let { absorb(christianOnly(it)) }
            }
        }

        // Ronda 2, punto 1 del dueño: pedir el mismo prompt dos veces por separado no puede devolver la
        // MISMA lista — MusicRequestRanking.pick es determinístico a propósito dentro de una llamada
        // (eso es correcto), así que lo que cambia es el POOL que le llega: se excluye lo servido
        // recientemente por esta misma función antes de elegir. La canción específica del peldaño -1
        // ([specificMatchId]) NUNCA se excluye por esto — es literalmente lo que el usuario nombró,
        // y debe poder pedirla otra vez y recibirla otra vez (ronda 10, dueño: "cuando pida un nombre
        // de artista con el nombre de la canción" es la excepción a esta regla de variedad).
        var versionsDropped = 0
        if (pinnedBase != null && pinnedBase.isNotBlank() && !wantsVersion) {
            val before = pool.size
            pool.removeAll { baseTitleKey(it.title) == pinnedBase }
            versionsDropped = before - pool.size
        }
        // Solo contadores (regla 4 de AGENTS.md): prueba en el log si la canción nombrada quedó fija, si
        // el resto era un artista, y cuántas palabras calificaron al género (nunca cuáles).
        timber.log.Timber.i(
            "MUSIC_REQUEST specific=%b artist=%b qualifiers=%d versionsDropped=%d pool=%d",
            pinned != null, artistLocked, qualifiers.size, versionsDropped, pool.size,
        )
        // Fila 361 — LA COMPROBACIÓN POR CANCIÓN ([MusicRequestStyleGate]): estilo, idioma, exclusiones y artista
        // real. Lo que él nombró (la canción fijada, el artista confirmado) nunca se juzga.
        if (context != null && pool.isNotEmpty()) {
            val protectedIds = buildSet {
                specificMatchId?.let { add(it) }
                if (artistLocked || soloArtist != null) pool.forEach { add(it.id) }
            }
            val knowledge = MusicRequestStyleGate.learn(
                context,
                pool.mapNotNull { it.artists.firstOrNull()?.name },
                if (intent.checkable) GATE_WAIT_MS else GATE_WAIT_MS / 2,
            )
            val outcome = MusicRequestStyleGate.judgeWithFallback(
                intent,
                pool.map { item ->
                    MusicRequestStyleGate.Cand(item.id, item.title, item.album?.name, item.artists.map { it.name })
                },
                styleVerified,
                protectedIds,
                knowledge,
                target = target,
            )
            MusicRequestStyleGate.log("search", intent, outcome)
            val keep = outcome.keptIds.toHashSet()
            pool.retainAll { it.id in keep }
        }
        val fresh = pool.filter { it.id == specificMatchId || !MusicRequestRecents.isRecent(it.id) }
        // Ronda 10 (dueño: "sin importar cuántas veces lo pida, la lista debe ser diferente" para un
        // TEMA en palabras naturales — sueño, ejercicio, género, década). Antes, si excluir lo
        // reciente dejaba menos candidatas que target, se descartaba la exclusión ENTERA y volvía la
        // lista completa IDÉNTICA a la anterior — el "nunca vacío" de pick() se cumplía, pero a costa
        // de repetir exactamente lo que el dueño pidió que dejara de repetirse. Ahora se prefiere una
        // lista MÁS CORTA pero distinta sobre una completa pero idéntica; el pool original solo vuelve
        // como último recurso si de verdad no queda NADA fresco (p. ej. un pedido tan específico que
        // ya se sirvió todo su catálogo disponible dentro del TTL).
        val poolForRanking = if (fresh.isNotEmpty()) fresh else pool

        // Y ahora se elige: orden de origen como esqueleto, el gusto empuja unos puestos, lo marcado
        // con "No me gusta" se cae y no hay dos seguidas del mismo artista. Sin perfil ([taste] null)
        // el empujón es cero y esto devuelve exactamente el orden de origen.
        val head = pinned
        val picked = MusicRequestRanking.pick(
            candidates = poolForRanking,
            target = if (head != null) target - 1 else target,
            artistOf = { it.artists.firstOrNull()?.name },
            tasteOf = { item ->
                taste?.scoreNames(item.artists.map { a -> a.name }, item.title) ?: 0.0
            },
            avoidScore = iad1tya.echo.music.reco.TasteProfile.AVOID,
        )
        // Lo que nombró, primero y siempre — ni el gusto, ni "No me gusta", ni la variedad entre
        // pedidos pueden moverlo: es literalmente lo que pidió.
        val songs = listOfNotNull(head) + picked
        MusicRequestRecents.markServed(songs.map { it.id })
        return SearchOutcome(
            songs.map { it.toMediaMetadata() },
            pinnedSpecific = head != null,
            artistLocked = artistLocked,
        )
    }

    /**
     * Lo que da la búsqueda, si su primer puesto es la canción exacta que él nombró, y si todo es del
     * artista que su petición nombró (ronda 12).
     */
    private data class SearchOutcome(
        val songs: List<MediaMetadata>,
        val pinnedSpecific: Boolean,
        val artistLocked: Boolean = false,
    )

    /**
     * Las listas editoriales de la categoría de YouTube Music que corresponde a su petición, o vacío
     * si ninguna corresponde claramente — y entonces la escalera sigue por la búsqueda, que es lo
     * honesto: usar una categoría que no es sería inventarse la respuesta.
     *
     * La taxonomía se cachea [MOODS_TTL_MS] porque cambia como mucho cada varios meses y pedirla en
     * cada petición sería una llamada de red a cambio de nada.
     */
    private suspend fun moodCategoryPlaylists(
        prompt: String,
        parsed: MusicRequestQuery.Parsed,
        qualifiers: List<String> = emptyList(),
    ): List<PlaylistItem> {
        val cached = cachedMoods
        val now = System.currentTimeMillis()
        val sections = if (cached != null && now - cachedMoodsAt < MOODS_TTL_MS) {
            cached
        } else {
            YouTube.moodAndGenres().getOrNull()?.also {
                cachedMoods = it
                cachedMoodsAt = now
            } ?: return emptyList()
        }
        val items = sections.flatMap { it.items }
        if (items.isEmpty()) return emptyList()
        val index = MusicRequestMoods.pickCategory(items.map { it.title }, prompt, parsed, qualifiers)
            ?: return emptyList()
        val endpoint = items[index].endpoint
        val browse = YouTube.browse(endpoint.browseId, endpoint.params).getOrNull() ?: return emptyList()
        return browse.items.flatMap { it.items }.filterIsInstance<PlaylistItem>()
    }

    /**
     * Las canciones de las secciones TRENDING/TOP de los charts reales de YouTube Music — ver
     * [MusicRequestQuery.Parsed.trending]. Vacío si la petición no pidió tendencias, si los charts no
     * responden, o si esa sección no trae canciones sueltas (a veces son álbumes/artistas).
     */
    private suspend fun trendingSongs(): List<SongItem> {
        val charts = YouTube.getChartsPage().getOrNull() ?: return emptyList()
        return charts.sections
            .filter { it.chartType == ChartsPage.ChartType.TRENDING || it.chartType == ChartsPage.ChartType.TOP }
            .flatMap { it.items }
            .filterIsInstance<SongItem>()
    }

    private fun filterTracksForSoloArtist(
        tracks: List<TrackQuery>,
        soloArtist: String?,
    ): List<TrackQuery> {
        if (soloArtist.isNullOrBlank()) return tracks
        return tracks.filter { AiPlaylistConstraints.artistAllowed(it.artist, soloArtist) }
    }

    /**
     * STRICTER solo-artist gate for the OWNER'S "no improvisation" directive: when the user asked for
     * ONE artist, only that artist's PRIMARY credits are accepted.
     *
     * The pre-existing gate (this same check without the position rule) already dropped resolved
     * songs whose credit list did not contain the requested artist at all. The gap left there: a
     * song where the artist appears only as a DEEP featured credit ("Jhayco, Bad Bunny" — Bad Bunny
     * last, a guest verse) satisfied `artists.any { … }` and entered a "solo Bad Bunny" playlist,
     * contradicting the primary-credit promise both prompt layers make.
     * [SongResolver.artistMatches] itself was already strict (normalized-name match with word
     * boundaries — verified, no `contains` gap there): the hole was the POSITION of the credit,
     * not the name comparison.
     *
     * Guest-position heuristic, honest about its limits: the requested artist must appear among the
     * FIRST [MAX_PRIMARY_CREDITS] credits of the resolved song. YouTube Music lists the primary
     * artist(s) first and features after; a primary credit is therefore near the front. An artist at
     * position 4+ of 5 is a featuring, and "solo X" must not resolve to someone else's song with a
     * X feature. Edge case kept true on purpose: when the credit list is SHORT (≤ [MAX_PRIMARY_CREDITS]
     * + 1 artists, the common collaboration shape "A & B"), any position still matches — dropping a
     * real "Bad Bunny & Jhayco" duet for ordering noise would sacrifice exactness the user can hear.
     */
    private fun acceptsResolvedSoloPrimary(mm: MediaMetadata, soloArtist: String?): Boolean =
        soloPrimaryMatch(mm.artists.map { it.name }, soloArtist)

    /** Credits searched for the primary artist before a credit is considered a "featuring" position. */
    private const val MAX_PRIMARY_CREDITS = 2

    /**
     * Pure, unit-testable core of the strict solo-artist gate: true when [names] (the resolved song's
     * credit list, in order) carries [soloArtist] as a PRIMARY credit. No Android/network types.
     */
    internal fun soloPrimaryMatch(names: List<String>, soloArtist: String?): Boolean {
        if (soloArtist.isNullOrBlank()) return true
        val primary = names.take(MAX_PRIMARY_CREDITS)
        if (primary.any { SongResolver.artistMatches(it, soloArtist) }) return true
        // Short collaboration shape ("A & B"): the requested artist as the second named credit is
        // still a primary credit, not a guest feature.
        return names.size <= MAX_PRIMARY_CREDITS + 1 &&
            names.any { SongResolver.artistMatches(it, soloArtist) }
    }

    /**
     * Ronda 9 (dueño): pedir una canción o artista específico llenaba la cola con 20-25 copias de LA
     * MISMA canción — distintas subidas de YouTube ("Official Video", "Lyrics", "Audio Oficial"…)
     * cada una con su propio id, así que el dedup por id no las agarraba. Clave de dedup pura:
     * título normalizado (sin paréntesis/corchetes ni puntuación) + artista principal en minúsculas.
     */
    internal fun dedupKey(title: String, primaryArtist: String?): String {
        val normTitle = title.lowercase()
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val normArtist = primaryArtist?.lowercase()?.trim().orEmpty()
        return "$normTitle|$normArtist"
    }

    /**
     * Ronda 9 (dueño): ver el peldaño 5 de [searchFallbackPlaylist]. El artista principal más común
     * en [items], o null si ninguno domina claramente (menos de la mitad) — sin dominancia clara no
     * hay señal fiable de que la petición fuera de un artista concreto, y no se inventa una.
     */
    internal fun dominantArtist(items: List<SongItem>): String? {
        if (items.isEmpty()) return null
        val counts = items.mapNotNull { it.artists.firstOrNull()?.name }
            .groupingBy { it }.eachCount()
        val leader = counts.entries.maxByOrNull { it.value } ?: return null
        return leader.key.takeIf { leader.value.toDouble() / items.size >= 0.5 }
    }

    /**
     * Ronda 9 (dueño): ver el peldaño -1 de [searchFallbackPlaylist]. El primer candidato cuyo
     * título + artista demuestra la MAYORÍA (≥60%, tolera una palabra de ruido suelta) de las
     * palabras de [residual] — lo que pidió más allá del género/momento/década — o null si ninguno
     * la demuestra lo bastante como para confiar en que es justo eso.
     */
    internal fun bestSpecificMatch(residual: String, candidates: List<SongItem>): SongItem? {
        // Auditoría del algoritmo (ronda 9, dueño: "vela que nada sea placebo"): antes se partía SOLO
        // por espacios y se comparaba por subcadena cruda (`hay.contains(it)`), así que "queen,
        // bohemian rhapsody" (la coma pegada al residuo) nunca calzaba palabra por palabra, y una
        // palabra corta del residuo podía matchear dentro de OTRA palabra del candidato sin ser la
        // misma (p. ej. "amor" dentro de "amoroso"). Partir por cualquier separador que no sea letra/
        // dígito limpia la puntuación, y comparar por límite de palabra (mismo patrón que ya usa
        // MusicRequestMoods.containsToken) evita el falso positivo por subcadena.
        val words = fold(residual).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }
        if (words.isEmpty()) return null
        // Ronda 11 (dueño: "pido Redimi2 Flipando y me reproduce Blindao de Redimi2... luego un remix").
        // Dos reglas nuevas:
        //  · al menos una palabra tiene que estar en el TÍTULO. Sin eso, "redimi2 flipando" aceptaba
        //    cualquier canción de Redimi2 (el artista solo ya cubre la mitad de las palabras) — y con
        //    ello "canciones de Queen" fijaba una canción cualquiera de Queen como si la hubiera nombrado;
        //  · entre las que pasan gana la de más cobertura, y una versión (remix, en vivo, cover…) pierde
        //    frente a la original salvo que él pida esa versión. Empate → la que el buscador puso antes.
        val wantsVersion = words.any { it in VERSION_WORDS }
        var best: SongItem? = null
        var bestScore = Double.NEGATIVE_INFINITY
        candidates.forEachIndexed { index, candidate ->
            val title = fold(candidate.title)
            val artists = fold(candidate.artists.joinToString(" ") { it.name })
            val titleHits = words.count { hasWord(title, it) }
            if (titleHits == 0) return@forEachIndexed
            val hits = words.count { hasWord(title, it) || hasWord(artists, it) }
            val coverage = hits.toDouble() / words.size
            if (coverage < 0.6) return@forEachIndexed
            var score = coverage * 10.0 - index * 0.01
            if (!wantsVersion && isVersionTitle(candidate.title)) score -= 5.0
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    /**
     * Ronda 12: la canción cuyo título ES exactamente [residual] (una sola palabra junto a un género:
     * "reggaeton gasolina" → "Gasolina"), o null. A diferencia de [bestSpecificMatch] no basta con que el
     * título la CONTENGA — "afro gospel" fijaba cualquier título con "Afro". La versión original gana a
     * un remix/en vivo salvo que él pidiera esa versión; empate → la que el buscador puso antes.
     */
    internal fun exactTitleMatch(residual: String, candidates: List<SongItem>): SongItem? {
        val wanted = fold(residual).trim()
        if (wanted.isEmpty()) return null
        val wantsVersion = wanted.split(Regex("[^\\p{L}\\p{N}]+")).any { it in VERSION_WORDS }
        val matches = candidates.filter { baseTitleKey(it.title) == wanted }
        if (wantsVersion) return matches.firstOrNull()
        return matches.firstOrNull { !isVersionTitle(it.title) } ?: matches.firstOrNull()
    }

    /**
     * Palabras que marcan una VERSIÓN de una canción y no la canción en sí. Ronda 11 (dueño): tras la
     * canción pedida sonaba "un remix que le hicieron a la canción".
     */
    private val VERSION_WORDS = setOf(
        "remix", "rmx", "live", "vivo", "version", "acustico", "acoustic", "cover", "karaoke",
        "instrumental", "slowed", "reverb", "sped", "mashup", "bootleg", "edit", "8d", "remaster",
        "remasterizado", "remastered", "unplugged", "demo", "tiktok", "nightcore",
    )

    /** true cuando el título se presenta como versión de otra canción (remix, en vivo, cover…). */
    internal fun isVersionTitle(title: String): Boolean {
        val t = fold(title)
        return VERSION_WORDS.any { hasWord(t, it) }
    }

    /**
     * La canción "de base" de un título, sin versión ni créditos ni subida: "Flipando (Remix)",
     * "Flipando - En Vivo", "Flipando ft. X [Official Video]" → "flipando". A propósito SIN artista:
     * un remix suele salir a nombre de quien lo hizo, y sigue siendo la misma canción.
     */
    internal fun baseTitleKey(title: String): String {
        var t = fold(title)
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
        // "Canción - Remix" / "Canción - En Vivo": lo que va tras el guion es la versión.
        t = t.substringBefore(" - ")
        // Solo marcas de crédito inequívocas: "con"/"with" también forman parte de títulos reales.
        t = t.replace(Regex("""\b(feat|ft|featuring)\b.*$"""), " ")
        val words = t.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        val base = words.filter { it !in VERSION_WORDS && it != "en" }.joinToString(" ").trim()
        // Un título hecho solo de esas palabras ("Vivo", "Live") ES la canción: no se vacía.
        return base.ifBlank { words.joinToString(" ").trim() }
    }

    private fun hasWord(folded: String, word: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])").containsMatchIn(folded)

    private fun fold(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
