package iad1tya.echo.music.playlistimport

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.api.AiPlaylistConstraints
import iad1tya.echo.music.api.AiPlaylistService
import iad1tya.echo.music.api.TrackQuery
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
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

        // ONE shared budget window for the WHOLE AI phase (ask + resolve + top-up), owner directive
        // 2026-08-31 ("AI answers are VERY slow"). The old code opened a FRESH 60s window per phase,
        // so ask + top-up could legally stack to ~120s while its own comment claimed "the SAME 60s
        // budget" — the comment was a placebo. A single deadline makes the worst case 60s ONCE and
        // gives every phase a real view of how much time is left. On timeout the AI flow yields
        // nothing and the honest non-AI fallback runs — never a spinner watching a dead endpoint.
        val aiOutcome = withTimeoutOrNull(AI_BUDGET_MS) {
            aiFlow(database, prompt, target, soloArtist, provider, apiKey, baseUrl, model, onResolveProgress)
        }
        if (aiOutcome != null) return aiOutcome

        // NON-AI SAFETY NET (owner directive 2026-08-29: "AI must never dead-end on an error").
        // Reached when the budget expired, the AI chain is unavailable (Aura Worker rate-limited,
        // Pollinations gone, no user key) or its tracks didn't resolve. Build the playlist from REAL
        // YouTube Music search results for the user's description — the same approach
        // InnerTune/OuterTune/Metrolist use for their auto-playlists (Innertune search/radio, no LLM)
        // — honestly labeled "generated without AI" via [Result.generatedWithoutAi], instead of the
        // old dead-end "No songs were found for that idea".
        return generateWithoutAi(database, prompt, target, soloArtist)
    }

    /**
     * The AI-backed path, in the caller's [AI_BUDGET_MS] window: ask → parallel resolve →
     * conditional top-up. Returns null ONLY when nothing AI-usable survived; the caller then runs
     * the honest non-AI fallback.
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
    ): kotlin.Result<Result>? {
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
        val playlist = PlaylistEntity(
            name = name,
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
            isLocal = true,
        )
        // Single transaction: create the playlist, persist songs, map them in order (atomic).
        database.transaction {
            insert(playlist)
            ordered.forEachIndexed { index, metadata ->
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
                resolved = ordered.size,
            ),
        )
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
     * The honest non-AI safety net, shared by the budget-expired and chain-unavailable paths: REAL
     * songs from YouTube Music search for the user's description (searchFallbackPlaylist), labeled
     * "generated without AI" via [Result.generatedWithoutAi].
     */
    private suspend fun generateWithoutAi(
        database: MusicDatabase,
        prompt: String,
        target: Int,
        soloArtist: String?,
    ): kotlin.Result<Result> {
        val fallback = withTimeoutOrNull(AI_BUDGET_MS) {
            searchFallbackPlaylist(prompt, soloArtist, target)
        }
        if (fallback.isNullOrEmpty()) {
            return kotlin.Result.failure(EmptyResultException())
        }
        val ordered = fallback
        // PERSISTENT honest label (owner directive 2026-09-03: "cuando las crea no se basa en lo que
        // pido"): the old name-only flow labeled nothing in the library — the "(sin IA)" playlist looked
        // exactly like a curated one, which is precisely why un-curated results read as "the AI ignored
        // me". Truncate the PROMPT to MAX_NAME_LENGTH minus the label's 9 chars so "(sin IA)" always
        // survives the cut (adversarial audit finding #4: appending first and then taking 40 dropped
        // the label exactly on long prompts — the case where the fallback is most likely).
        val name = (prompt.trim().ifBlank { prompt }.take(MAX_NAME_LENGTH - 9) + " (sin IA)")
        val playlist = PlaylistEntity(
            name = name,
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
            isLocal = true,
        )
        // Single transaction: create the playlist, persist songs, map them in order (atomic).
        database.transaction {
            insert(playlist)
            ordered.forEachIndexed { index, metadata ->
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
                resolved = ordered.size,
                generatedWithoutAi = true,
            ),
        )
    }

    /**
     * The non-AI safety net behind the AI chain: REAL songs from YouTube Music search for the user's
     * description, so "AI playlists" never dead-end on "servicio ocupado" (owner directive
     * 2026-08-29). This is the no-LLM approach InnerTune/OuterTune/Metrolist use for auto playlists:
     * the search itself IS the recommender.
     *
     * Query ladder: the raw prompt (truncated to YouTube's practical query length) → song filter,
     * then progressively looser passes (no filter, video filter) → finally a top-up from YouTube
     * MUSIC search filtered to the solo artist when the user asked for one. De-duplicated by video id;
     * every result is a real, playable [SongItem] straight from the catalog the app already plays.
     */
    private suspend fun searchFallbackPlaylist(
        prompt: String,
        soloArtist: String?,
        target: Int,
    ): List<MediaMetadata> {
        val seen = HashSet<String>()
        val out = ArrayList<MediaMetadata>()
        suspend fun absorb(items: List<SongItem>) {
            for (item in items) {
                if (out.size >= target) return
                if (seen.add(item.id)) {
                    if (soloPrimaryMatch(item.artists.map { it.name }, soloArtist)) {
                        out += item.toMediaMetadata()
                    }
                }
            }
        }
        val query = prompt.trim().take(80)
        if (query.isBlank()) return out

        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>()?.let { absorb(it) }
        if (out.size < target) {
            YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.let { absorb(it) }
        }
        return out
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
