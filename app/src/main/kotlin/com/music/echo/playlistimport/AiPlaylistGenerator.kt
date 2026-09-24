package iad1tya.echo.music.playlistimport

import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
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
    ): Produced? {
        val soloArtist = AiPlaylistConstraints.extractSoloArtist(prompt)
        val startedAt = System.currentTimeMillis()
        val produced = produceRacing(
            database, prompt, count, soloArtist, provider, apiKey, baseUrl, model, onResolveProgress, taste,
        )
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
    ): Produced? = coroutineScope {
        val aiJob = async {
            runCatching {
                withTimeoutOrNull(MUSIC_REQUEST_AI_LEASH_MS) {
                    aiFlow(database, prompt, target, soloArtist, provider, apiKey, baseUrl, model, onResolveProgress)
                }
            }.getOrNull()
        }
        val search = runCatching {
            withTimeoutOrNull(MUSIC_REQUEST_SEARCH_BUDGET_MS) {
                searchFallbackPlaylist(prompt, soloArtist, target, taste)
            }
        }.getOrNull().orEmpty()

        // QUE NO IMPROVISE (dueño, 2026-09-17), y aquí está la regla que lo decide entre las dos
        // rutas: cuando la petición trae algo **comprobable** — una década — gana la ruta que lo ha
        // COMPROBADO. La búsqueda llega por una lista cuyo título nombra esa década
        // ([MusicRequestMatch]); la IA devuelve títulos y artistas sin año, así que nadie puede
        // verificar que sean de los 80: con un modelo flojo, "80s" se convierte en "lo que al modelo
        // le suena a antiguo". Sigue sin decirse cuál de las dos fue — el híbrido es de dónde salen
        // las canciones, no de qué se le cuenta a él.
        val verifiable = MusicRequestQuery.build(prompt).decade != null
        if (verifiable && search.isNotEmpty()) {
            aiJob.cancel()
            return@coroutineScope Produced(
                name = prompt.trim().take(MAX_NAME_LENGTH),
                songs = search,
                fromAi = false,
            )
        }

        // Desempate a favor de la IA: solo si YA terminó. Esperarla aquí sería volver a la fila.
        val aiEarly = if (aiJob.isCompleted) runCatching { aiJob.await() }.getOrNull() else null
        if (aiEarly != null && aiEarly.songs.isNotEmpty()) {
            return@coroutineScope aiEarly
        }
        if (search.isNotEmpty()) {
            aiJob.cancel()
            return@coroutineScope Produced(
                name = prompt.trim().take(MAX_NAME_LENGTH),
                songs = search,
                fromAi = false,
            )
        }
        // La búsqueda no dio nada: ahora sí merece la pena esperar a la IA hasta su correa.
        val aiLate = runCatching { aiJob.await() }.getOrNull()
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
        }
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

        val proposed = filterTracksForSoloArtist(spec.tracks, soloArtist)
        val firstPass = resolveBounded(database, proposed, soloArtist, target, onResolveProgress)
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
        onResolveProgress: (done: Int, total: Int) -> Unit,
    ): List<MediaMetadata> = resolveBoundedOrdered(
        proposed = proposed,
        resolveArtistFor = { track -> soloArtist?.takeIf { it.isNotBlank() } ?: track.artist },
        resolveOne = { title, artist -> SongResolver.resolve(database, title, artist) },
        accept = { mm -> acceptsResolvedSoloPrimary(mm, soloArtist) },
        target = target,
        concurrency = RESOLVE_CONCURRENCY,
        onResolveProgress = onResolveProgress,
    )

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
        prompt: String,
        soloArtist: String?,
        target: Int,
        taste: iad1tya.echo.music.reco.TasteProfile? = null,
    ): List<MediaMetadata> {
        val parsed = MusicRequestQuery.build(prompt)
        val query = parsed.query.ifBlank { prompt.trim().take(80) }
        if (query.isBlank()) return emptyList()

        // UN MONTÓN Y LUEGO ELEGIR (dueño, 2026-09-17). Antes se aceptaban canciones por orden hasta
        // llenar el cupo, así que la primera fuente que pasara el filtro decidía el resultado entero.
        // Una sola página de lista ya trae cincuenta y pico, o sea que juntar candidatas NO cuesta ni
        // una llamada más — lo que cambia es que al final se puede elegir. Ver [MusicRequestRanking].
        val pool = ArrayList<SongItem>()
        val seen = HashSet<String>()
        fun absorb(items: List<SongItem>) {
            for (item in items) {
                if (pool.size >= POOL_TARGET) return
                if (seen.add(item.id) && soloPrimaryMatch(item.artists.map { it.name }, soloArtist)) {
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
        val requireChristian = MusicRequestMoods.requiresChristianContent(prompt)
        fun christianOnly(items: List<SongItem>): List<SongItem> =
            if (!requireChristian) {
                items
            } else {
                items.filter { item ->
                    MusicRequestMoods.looksChristian(item.title) ||
                        item.artists.any { MusicRequestMoods.looksChristian(it.name) }
                }
            }

        if (parsed.preferPlaylists && soloArtist == null) {
            // Peldaño 0 — TENDENCIAS REALES (ronda 6, dueño: "lo que suena ahora"). Mismo espíritu que
            // el peldaño 1: no se le pide a la búsqueda ni a un LLM que ADIVINE qué está de moda —
            // se piden los charts reales de YouTube Music, que es la única fuente que de verdad lo
            // sabe. Si el usuario no pidió tendencias, esto no hace ninguna llamada de más.
            if (parsed.trending && pool.size < POOL_TARGET) {
                absorb(trendingSongs())
            }

            // Peldaño 1 — EL CATÁLOGO PROPIO DE YOUTUBE MUSIC. Sus categorías ("Años 80",
            // "Concentración") entregan listas editoriales suyas, y ahí la categoría ES la prueba: no
            // hace falta verificar por título porque el contenido lo garantiza la casa. Ver
            // [MusicRequestMoods] para por qué sus palabras no coinciden con los nombres de categoría.
            moodCategoryPlaylists(prompt, parsed).take(PLAYLISTS_USED).forEach { pl ->
                if (pool.size < POOL_TARGET) {
                    YouTube.playlist(pl.id).getOrNull()?.songs?.let { absorb(it) }
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
                val conceptGroups = MusicRequestMoods.conceptGroupsFor(prompt, parsed)
                MusicRequestMatch.rankedIndices(candidates.map { it.title }, parsed, conceptGroups)
                    .take(PLAYLISTS_USED)
                    .forEach { index ->
                        if (pool.size < POOL_TARGET) {
                            YouTube.playlist(candidates[index].id).getOrNull()?.songs?.let { absorb(it) }
                        }
                    }
            }
        }

        // Peldaño 3 — canciones sueltas del buscador.
        if (pool.size < POOL_TARGET) {
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.let { absorb(christianOnly(it)) }
        }
        // Peldaño 4 — los VÍDEOS son lo menos fiable (recopilaciones de una hora, versiones de
        // aficionado): en una petición de época o momento solo se tocan si no hay NADA.
        val videosAllowed = if (parsed.preferPlaylists) pool.isEmpty() else pool.size < target
        if (videosAllowed) {
            YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.let { absorb(christianOnly(it)) }
        }
        // Red de seguridad: si la consulta construida no dio nada, se prueba su petición TAL CUAL.
        if (pool.isEmpty() && query != prompt.trim().take(80)) {
            YouTube.search(prompt.trim().take(80), YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.let { absorb(christianOnly(it)) }
        }

        // Ronda 2, punto 1 del dueño: pedir el mismo prompt dos veces por separado no puede devolver la
        // MISMA lista — MusicRequestRanking.pick es determinístico a propósito dentro de una llamada
        // (eso es correcto), así que lo que cambia es el POOL que le llega: se excluye lo servido
        // recientemente por esta misma función antes de elegir. "Nunca vacío" se respeta igual que en
        // pick(): si excluir dejara menos candidatas que target, se readmite lo necesario — mejor
        // repetir alguna que devolver una lista corta.
        val fresh = pool.filterNot { MusicRequestRecents.isRecent(it.id) }
        val poolForRanking = if (fresh.size >= target) fresh else pool

        // Y ahora se elige: orden de origen como esqueleto, el gusto empuja unos puestos, lo marcado
        // con "No me gusta" se cae y no hay dos seguidas del mismo artista. Sin perfil ([taste] null)
        // el empujón es cero y esto devuelve exactamente el orden de origen.
        val picked = MusicRequestRanking.pick(
            candidates = poolForRanking,
            target = target,
            artistOf = { it.artists.firstOrNull()?.name },
            tasteOf = { item ->
                taste?.scoreNames(item.artists.map { a -> a.name }, item.title) ?: 0.0
            },
            avoidScore = iad1tya.echo.music.reco.TasteProfile.AVOID,
        )
        MusicRequestRecents.markServed(picked.map { it.id })
        return picked.map { it.toMediaMetadata() }
    }

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
        val index = MusicRequestMoods.pickCategory(items.map { it.title }, prompt, parsed)
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
}
