package iad1tya.echo.music.utils.sabr

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import iad1tya.echo.music.utils.cipher.CipherDeobfuscator
import iad1tya.echo.music.utils.cipher.PlayerJsFetcher
import iad1tya.echo.music.utils.cipher.SignatureCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Solves YouTube's two stream challenges, the throttling parameter `n` and the signature `s` of a `signatureCipher`,
 * by running the REAL player.js in a WebView with yt-dlp's EJS solver (https://github.com/yt-dlp/ejs, Unlicense), the
 * same component yt-dlp itself relies on. EJS finds the player's own URL handling by its shape instead of by a function
 * name, so a player rotation does not need a new entry in player_configs.json.
 *
 * Nothing is guessed: the answer is whatever the player's own functions return for the challenge, and the solver refuses
 * to answer when the player's candidates disagree. The assets live in assets/solver (lib + core + page); the
 * preprocessed player is cached per player hash so only the first run after a rotation pays for the parse.
 */
object EjsNTransformSolver {
    private const val TAG = "echomusic_EjsNSolver"

    private var solverWebView: SolverWebView? = null

    // Serializes solver creation AND every solve. Two things are unsafe to run concurrently on
    // the shared singleton: (1) getOrCreateSolver has a check-then-act split by a suspension point
    // (PlayerJsFetcher.getPlayerJs), so unguarded concurrent callers each build a SolverWebView and
    // leak the loser; (2) SolverWebView has a single pending continuation, so a second in-flight
    // solve would clobber the first's continuation and orphan a coroutine. Holding this lock
    // across the whole resolution body guarantees exactly one solver and one solve at a time.
    private val solverMutex = Mutex()

    suspend fun transformNParamInUrl(url: String): String {
        val nMatch = Regex("[?&]n=([^&]+)").find(url)
        if (nMatch == null) {
            Timber.tag(TAG).d("No 'n' parameter in the stream URL")
            return url
        }
        val nValue = Uri.decode(nMatch.groupValues[1])

        // Every failure branch FALLS BACK to the original url (never throws into the caller), so streaming continues
        // on the happy path and degrades gracefully otherwise.
        val transformed = solve("n", nValue) ?: return url
        return url.replaceFirst(
            Regex("([?&])n=[^&]+"),
            "$1n=${Uri.encode(transformed)}"
        )
    }

    /**
     * The final stream URL of a `signatureCipher` (the base URL with the solved signature under the parameter name the
     * cipher asks for), or null when the player could not solve it. The caller then falls through to the older cipher
     * path; nothing here ever returns a URL carrying an unsolved or invented signature.
     */
    suspend fun deobfuscateSignatureCipher(signatureCipher: String): String? {
        val parts = SignatureCipher.parse(signatureCipher) ?: return null
        val solved = solve("sig", parts.s) ?: return null
        return SignatureCipher.buildUrl(parts, solved)
    }

    private suspend fun solve(kind: String, value: String): String? {
        val startedAt = SystemClock.elapsedRealtime()
        // Name and state only (log privacy, AGENTS.md rule 4): never the challenge, never the answer.
        fun report(status: String, hash: String?) {
            Timber.tag(TAG).i(
                "EJS_SOLVE kind=%s status=%s ms=%d hash=%s",
                kind, status, SystemClock.elapsedRealtime() - startedAt, hash,
            )
        }
        return solverMutex.withLock {
            val solver = getOrCreateSolver()
            if (solver == null) {
                report("no_solver", null)
                return@withLock null
            }
            try {
                val answer = solver.solve(kind, value)
                if (answer == null) {
                    // WebView eval never called back within the timeout (wedged/dead renderer).
                    report("timeout", solver.playerHash)
                } else {
                    report("ok", solver.playerHash)
                }
                answer
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w("EJS solve kind=$kind failed: ${e.message}")
                report("failed", solver.playerHash)
                null
            }
        }
    }

    // Callers MUST hold [solverMutex]. The double-check + create below is only safe because the lock
    // serializes the suspension point at PlayerJsFetcher.getPlayerJs.
    private suspend fun getOrCreateSolver(): SolverWebView? {
        val existing = solverWebView
        if (existing != null && !existing.isDead) {
            val age = SystemClock.elapsedRealtime() - existing.createdAtMs
            val currentHash = PlayerJsFetcher.cachedPlayerHash()
            val sameHash = currentHash == null || currentHash == existing.playerHash
            val usable = existing.nFunctionAvailable || existing.sigFunctionAvailable
            // A solver that found nothing is only worth keeping for a short while (a rebuild costs seconds); a working
            // one is kept until the player rotates or its cached copy is old enough to be refetched.
            val keep = sameHash && age < (if (usable) SOLVER_MAX_AGE_MS else FAILED_SOLVER_RETRY_MS)
            if (keep) return existing
            Timber.tag(TAG).d("Rebuilding the EJS solver (rotated=${!sameHash}, ageMs=$age, usable=$usable)")
            withContext(NonCancellable + Dispatchers.Main) { existing.close() }
        }
        // A previously cached solver whose renderer died (or that was replaced above) is unusable; drop it and rebuild.
        solverWebView = null

        val result = PlayerJsFetcher.getPlayerJs(forceRefresh = false)
        if (result == null) {
            Timber.tag(TAG).e("Failed to get player JS for EJS solver")
            return null
        }
        val (playerJs, hash) = result
        Timber.tag(TAG).d("Creating EJS solver (player hash=$hash, ${playerJs.length} chars)")

        return try {
            val sv = SolverWebView.create(CipherDeobfuscator.appContext, playerJs, hash)
            if (sv == null) {
                // create() timed out or the renderer died before init completed.
                Timber.tag(TAG).e("EJS solver creation timed out; falling back")
                null
            } else {
                solverWebView = sv
                sv
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create EJS solver: ${e.message}")
            null
        }
    }

    suspend fun close() {
        // Take the same lock so teardown can't null out solverWebView while a solve is mid-flight.
        solverMutex.withLock {
            withContext(Dispatchers.Main) {
                solverWebView?.close()
            }
            solverWebView = null
        }
    }

    // A working solver is rebuilt after this long so a rotated player is picked up (the cached player.js itself has a 6 h TTL).
    private const val SOLVER_MAX_AGE_MS = 6 * 60 * 60 * 1000L

    // A solver that could not solve anything is retried after this long (or at once when the player rotates).
    private const val FAILED_SOLVER_RETRY_MS = 10 * 60 * 1000L

    class SolverWebView private constructor(
        context: Context,
        private val playerJs: String,
        val playerHash: String,
        initContinuation: Continuation<SolverWebView>,
    ) {
        private val webView = WebView(context)

        private var initContinuation: Continuation<SolverWebView>? = initContinuation

        // @Volatile is defense-in-depth: the outer solverMutex already guarantees a single in-flight
        // solve, but this field is set on Main (solve) and cleared/read from the JavaBridge
        // thread via takePending (onSolved/onSolveError), so publish it safely across threads.
        @Volatile
        private var pending: Continuation<String>? = null

        val createdAtMs: Long = SystemClock.elapsedRealtime()

        @Volatile
        var isDead: Boolean = false
            private set

        @Volatile
        private var destroyed = false

        @Volatile
        var nFunctionAvailable: Boolean = false
            private set

        @Volatile
        var sigFunctionAvailable: Boolean = false
            private set

        init {
            val settings = webView.settings
            @Suppress("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            settings.blockNetworkLoads = true

            webView.addJavascriptInterface(this, "EjsBridge")

            // Without this, a renderer death (e.g. OOM-kill) crashes the whole app AND leaves the
            // init/solve continuations forever unresumed. Resume them with an exception and return true
            // so the process survives, mirroring CipherWebView.onRenderProcessGone.
            webView.webViewClient = object : WebViewClient() {
                @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    val didCrash = runCatching { detail.didCrash() }.getOrNull()
                    Timber.tag(TAG).e("=== EJS RENDER PROCESS GONE === didCrash=$didCrash")
                    onRendererGone("EJS WebView render process gone (didCrash=$didCrash)")
                    return true
                }
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    val msg = m.message()
                    if (msg.contains("Uncaught")) {
                        Timber.tag(TAG).e("EJS WebView error: ${msg.take(160)} at ${m.sourceId()?.substringAfterLast('/')}:${m.lineNumber()}")
                    }
                    return super.onConsoleMessage(m)
                }
            }
        }

        /**
         * Renderer process died (OOM-killed or crashed). Marks this instance dead, fails every
         * pending continuation so no caller hangs, and tears the WebView down. The next resolution
         * rebuilds a fresh solver via [getOrCreateSolver].
         */
        private fun onRendererGone(reason: String) {
            isDead = true
            val e = SabrException(reason)
            takeInitContinuation()?.resumeSafely { it.resumeWithException(e) }
            takePending()?.resumeSafely { it.resumeWithException(e) }
            close()
        }

        @Synchronized
        private fun takeInitContinuation(): Continuation<SolverWebView>? =
            initContinuation.also { initContinuation = null }

        @Synchronized
        private fun takePending(): Continuation<String>? =
            pending.also { pending = null }

        private inline fun <T> T.resumeSafely(block: (T) -> Unit) {
            runCatching { block(this) }
        }

        private fun preprocessedDir(): File = File(webView.context.filesDir, "cipher_cache")

        private fun preprocessedFile(): File =
            File(preprocessedDir(), "${PRE_PREFIX}${playerHash.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}.js")

        private fun loadSolverAndPlayer() {
            val dir = File(webView.context.cacheDir, "ejs_solver")
            dir.mkdirs()

            val assetManager = webView.context.assets
            for (file in listOf("yt.solver.lib.js", "yt.solver.core.js")) {
                assetManager.open("solver/$file").use { input ->
                    File(dir, file).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val html = assetManager.open("solver/yt.solver.page.html").bufferedReader().use { it.readText() }

            File(dir, "player.js").writeText(playerJs)
            // The preprocessed copy of THIS player (kept from an earlier run) skips the slow parse; a stale one is removed.
            val pre = preprocessedFile()
            val preTarget = File(dir, "pre.js")
            if (pre.isFile) pre.copyTo(preTarget, overwrite = true) else preTarget.delete()
            Timber.tag(TAG).d("EJS solver assets written (${playerJs.length} chars, preprocessed cached=${pre.isFile})")

            webView.loadDataWithBaseURL(
                "file://${dir.absolutePath}/",
                html, "text/html", "utf-8", null
            )
        }

        @JavascriptInterface
        fun onLog(message: String) {
            Timber.tag(TAG).d(message)
        }

        @JavascriptInterface
        fun onPreprocessed(code: String) {
            runCatching {
                val dir = preprocessedDir()
                dir.mkdirs()
                dir.listFiles { f -> f.name.startsWith(PRE_PREFIX) }?.forEach { it.delete() }
                val tmp = File(dir, "${PRE_PREFIX}tmp")
                tmp.writeText(code)
                if (!tmp.renameTo(preprocessedFile())) tmp.delete()
            }.onFailure { Timber.tag(TAG).w("EJS preprocessed cache not written: ${it.javaClass.simpleName}") }
        }

        @JavascriptInterface
        fun onSolverReady(nAvailable: String, sigAvailable: String) {
            nFunctionAvailable = nAvailable == "true"
            sigFunctionAvailable = sigAvailable == "true"
            // INFO: lands in the log the owner shares. Which solvers the player yielded is the first thing to know
            // when a song does not resolve; the hash is the public player id, not user data.
            Timber.tag(TAG).i(
                "EJS_SOLVER ready hash=%s n=%s sig=%s ms=%d",
                playerHash, nFunctionAvailable, sigFunctionAvailable, SystemClock.elapsedRealtime() - createdAtMs,
            )
            takeInitContinuation()?.resumeSafely { it.resume(this) }
        }

        @JavascriptInterface
        fun onSolverError(error: String) {
            Timber.tag(TAG).e("EJS solver error: ${error.take(120)}")
            takeInitContinuation()?.resumeSafely { it.resume(this) }
        }

        /**
         * Runs one solve inside the WebView. Returns null if the eval never called back within
         * [EVAL_TIMEOUT_MS] (wedged/dead renderer) so the caller can fall back instead of hanging. The
         * suspension is cancellable and clears [pending] on cancel.
         */
        suspend fun solve(kind: String, value: String): String? {
            val available = if (kind == "n") nFunctionAvailable else sigFunctionAvailable
            if (!available || isDead) {
                throw SabrException("EJS $kind solver not available")
            }

            return withTimeoutOrNull(EVAL_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        pending = cont
                        cont.invokeOnCancellation { takePending() }
                        // JSON string literals are valid JS string literals: every quote, backslash and control
                        // character of the challenge is escaped.
                        webView.evaluateJavascript("solve(${JSONObject.quote(kind)}, ${JSONObject.quote(value)})", null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun onSolved(kind: String, result: String) {
            takePending()?.resumeSafely { it.resume(result) }
        }

        @JavascriptInterface
        fun onSolveError(kind: String, error: String) {
            takePending()?.resumeSafely { it.resumeWithException(SabrException("EJS $kind solve failed: ${error.take(60)}")) }
        }

        fun close() {
            // Idempotent: now reachable from onRendererGone, create-timeout cleanup, and the outer
            // close(), so guard against a double webView.destroy().
            if (destroyed) return
            destroyed = true
            runCatching {
                webView.clearHistory()
                webView.clearCache(true)
                webView.loadUrl("about:blank")
                webView.onPause()
                webView.removeAllViews()
                webView.destroy()
            }.onFailure { Timber.tag(TAG).w("EJS WebView teardown threw: $it") }
            Timber.tag(TAG).d("EJS solver WebView closed")
        }

        companion object {
            // Cold-start (WebView spin-up + EJS preprocess of the multi-MB player.js) is a few seconds on a healthy
            // device and longer on a weak one, only on the first run after a player rotation (the preprocessed player
            // is cached afterwards). These ceilings turn a wedged/dead solver into a fast fallback instead of an
            // unbounded hang. A solve itself is sub-second.
            private const val CREATE_TIMEOUT_MS = 25_000L
            private const val EVAL_TIMEOUT_MS = 8_000L

            // The cache key names the solver bundle too, so shipping a new core never reuses an old preprocessed player.
            private const val PRE_PREFIX = "ejs_pre_080a1_"

            /**
             * Returns null if the WebView never fired onSolverReady/onSolverError within
             * [CREATE_TIMEOUT_MS] (page never loaded, JS never hit the bridge, or renderer died).
             * Callers fall back to the older paths in that case.
             */
            suspend fun create(context: Context, playerJs: String, playerHash: String): SolverWebView? {
                var created: SolverWebView? = null
                val result = withTimeoutOrNull(CREATE_TIMEOUT_MS) {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine { cont ->
                            val sv = SolverWebView(context, playerJs, playerHash, cont)
                            created = sv
                            cont.invokeOnCancellation { sv.takeInitContinuation() }
                            sv.loadSolverAndPlayer()
                        }
                    }
                }
                if (result == null) {
                    // Timed out: tear the half-built WebView down so it doesn't leak/linger.
                    created?.let { sv ->
                        withContext(NonCancellable + Dispatchers.Main) {
                            sv.isDead = true
                            sv.takeInitContinuation()
                            sv.close()
                        }
                    }
                }
                return result
            }
        }
    }
}
