package iad1tya.echo.music.utils.cipher

import android.content.Context
import androidx.media3.common.util.UnstableApi
import dev.maxrave.pipepipe.extractor.exceptions.ParsingException
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeApiDecoder
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeJavaScriptDecoder
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Aura's LOCAL on-device decoder for PipePipeExtractor — the SimpMusic v2.0.0 streaming-reliability
 * tier, adapted to this app's OWN cipher stack instead of theirs.
 *
 * PipePipeExtractor f8982ca9e7 exposes [YoutubeJavaScriptDecoder]: when a local decoder is
 * registered via [YoutubeApiDecoder.setLocalDecoder], every sig/n challenge is solved ON DEVICE
 * first; if the decoder THROWS, PipePipe falls through to its own api.pipepipe.dev server, and if
 * that also fails the existing BravePipe tier keeps the chain alive. Throwing is therefore the way
 * to hand a batch back — a half-filled result would be accepted as final (the missing URLs would
 * 403 later, silently skipping the fallback).
 *
 * Implementation choice (owner rule: never guess cipher values): the solving engine is the SAME
 * CipherWebView this app already ships — it downloads the REAL base.js, splices export wrappers
 * and executes the genuine functions in a real JS engine. The player table is the owner's
 * player_configs.json (RemotePlayerConfig), same source SimpMusic's faraday registry uses for
 * theirs. No QuickJS port was needed: the WebView already does the exact same job on-device.
 *
 * Registration discipline (SimpMusic lesson, Extractor.android.kt): PipePipe's
 * disableLocalDecoder() clears the decoder permanently after a throw and its getter is
 * package-private, so this class re-registers itself BEFORE EVERY batch — "disabled forever"
 * becomes "skipped for one track".
 */
object PipePipeLocalCipherDecoder : YoutubeJavaScriptDecoder {

    private const val TAG = "PipePipeCipher"

    /** App context, set once at startup (App.kt) before any extraction can run. */
    @Volatile
    var appContext: Context? = null
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Registers THIS decoder as PipePipe's local decoder. Idempotent and cheap; call before every
     * extraction batch (see class doc for why re-registering matters).
     */
    fun reRegister() {
        // A2b (row 337, owner approved 2026-10-07): PipePipe's getPlayerMetadata does NOT catch a
        // getPlayerData throw, so registering this decoder for a player hash without a VERIFIED sts made
        // every PipePipe extraction fail and fall through to the next extractor. Register only when the
        // current hash is verified; otherwise clear it so PipePipe takes its own metadata path (what it
        // does with no local decoder at all). Same predicate as getPlayerData and the startup prewarm.
        val hash = currentHash()
        val verified = hash != null && verifiedStsForHash(hash) != null
        runCatching { YoutubeApiDecoder.setLocalDecoder(if (verified) this else null) }
        val state = (if (verified) "on" else "off") + ":" + hash
        if (state != lastLoggedState) {
            lastLoggedState = state
            Timber.tag(TAG).i(
                "PIPEPIPE_LOCAL_DECODER %s hash=%s",
                if (verified) "on" else "off_no_verified_sts", hash,
            )
        }
    }

    @Volatile private var lastLoggedState: String? = null
    @Volatile private var hashReadAt = 0L
    @Volatile private var hashCache: String? = null

    /** The cached player hash, re-read from disk at most every 30 s (called before every extraction). */
    private fun currentHash(): String? {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - hashReadAt > 30_000L || hashCache == null) {
            hashCache = PlayerJsFetcher.cachedPlayerHash()
            hashReadAt = now
        }
        return hashCache
    }

    /**
     * PipePipe asks for the player identity + signatureTimestamp BEFORE downloading streams.
     * All-or-nothing rule: a WRONG sts poisons the whole player response, so this returns data
     * ONLY when the owner's player table positively knows the CURRENT player hash. Otherwise it
     * throws. NOTE (2026-10-07, read in PipePipeExtractor f8982ca9e7): unlike decode/decodeBatch,
     * PipePipe's getPlayerMetadata does NOT catch this throw — the extraction of that song fails. That
     * is why [reRegister] only registers this decoder for a verified hash (row 337); this throw stays as
     * the last guard (a config removed between registration and this call).
     */
    /**
     * The verified signatureTimestamp of [hash] in the owner's table, or null. The ONE predicate both
     * [getPlayerData] and [CipherDeobfuscator.prewarm] use (via [CipherWarmPolicy]).
     */
    internal fun verifiedStsForHash(hash: String): Int? =
        CipherWarmPolicy.verifiedSts(RemotePlayerConfig.configFor(hash)?.signatureTimestamp)

    override fun getPlayerData(videoId: String): YoutubeJavaScriptDecoder.PlayerData {
        val context = appContext ?: throw ParsingException("Aura cipher decoder not initialized")
        val info = runCatching {
            runBlocking { PlayerJsFetcher.getPlayerJs(forceRefresh = false) }
        }.getOrNull()

        if (info == null) {
            Timber.tag(TAG).w("getPlayerData: player JS unavailable — handing back to PipePipe server")
            throw ParsingException("Aura: player JS unavailable")
        }
        val (playerJs, hash) = info
        val sts = verifiedStsForHash(hash)
        if (sts == null) {
            // No verified entry for this hash in the owner's table → do NOT guess (registry #30:
            // a guessed sts poisons the response for every video until the next rotation).
            Timber.tag(TAG).w("getPlayerData: no verified sts for hash=$hash — handing back to PipePipe server")
            throw ParsingException("Aura: no verified sts for player $hash")
        }
        return YoutubeJavaScriptDecoder.PlayerData(hash, sts)
    }

    /**
     * Solves a batch of sig + n challenges on-device by EXECUTING the real functions in the
     * CipherWebView. All-or-nothing: every requested value must come back or the whole batch is
     * handed back to PipePipe's server with a throw (a missing sig would otherwise ship a URL
     * that 403s later, skipping the fallback).
     */
    override fun decodeBatch(
        playerId: String,
        signatures: List<String>?,
        throttlingParameters: List<String>?,
    ): YoutubeApiDecoder.BatchDecodeResult {
        val wantedSigs = signatures.orEmpty().distinct()
        val wantedNs = throttlingParameters.orEmpty().distinct()
        val context = appContext ?: throw ParsingException("Aura cipher decoder not initialized")

        val sigResults = mutableMapOf<String, String>()
        val nResults = mutableMapOf<String, String>()

        if (wantedSigs.isNotEmpty()) {
            val solved = runCatching {
                runBlocking { CipherDeobfuscator.solveSignatures(wantedSigs, context) }
            }.getOrNull()
            if (solved != null) sigResults.putAll(solved)
        }
        if (wantedNs.isNotEmpty()) {
            val solved = runCatching {
                runBlocking { CipherDeobfuscator.solveNParameters(wantedNs, context) }
            }.getOrNull()
            if (solved != null) nResults.putAll(solved)
        }

        val missing =
            wantedSigs.count { it !in sigResults } +
                wantedNs.count { it !in nResults }
        if (missing > 0) {
            Timber.tag(TAG).w(
                "decodeBatch: $missing of ${wantedSigs.size + wantedNs.size} unsolved — handing batch to PipePipe server",
            )
            throw ParsingException("Aura: $missing of ${wantedSigs.size + wantedNs.size} unsolved")
        }
        Timber.tag(TAG).d(
            "decodeBatch: solved ${wantedSigs.size} sig + ${wantedNs.size} n ON DEVICE",
        )
        return YoutubeApiDecoder.BatchDecodeResult(sigResults, nResults)
    }
}
