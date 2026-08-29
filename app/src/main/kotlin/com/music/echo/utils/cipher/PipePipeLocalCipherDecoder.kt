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
        runCatching { YoutubeApiDecoder.setLocalDecoder(this) }
    }

    /**
     * PipePipe asks for the player identity + signatureTimestamp BEFORE downloading streams.
     * All-or-nothing rule: a WRONG sts poisons the whole player response, so this returns data
     * ONLY when the owner's player table positively knows the CURRENT player hash. Otherwise it
     * throws and PipePipe proceeds with its own server path (which handles sts itself).
     */
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
        val config = RemotePlayerConfig.configFor(hash)
        val sts = config?.signatureTimestamp
        if (sts == null || sts <= 0) {
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
