

package iad1tya.echo.music.lyrics

import android.content.Context
import iad1tya.echo.music.api.DeepLService
import iad1tya.echo.music.api.MistralService
import iad1tya.echo.music.api.OpenRouterService
import iad1tya.echo.music.api.OpenRouterStreamingService
import iad1tya.echo.music.constants.LanguageCodeToName
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.LyricsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Parser for the dict-chrome-ex response shapes (both verified live 2026-08-29):
 *   ["line1\nline2"]                  — plain single-string array (sl known)
 *   [["line1\nline2","en"]]           — auto-detect pair (sl=auto)
 *   ["segment1", "segment2", ...]      — one entry per DETECTED SENTENCE (happens with mixed
 *                                        punctuation: each array slot covers part of the batch)
 * Internal for the JVM spec mirror (GoogleTranslatedAlignmentTest); production only calls it
 * from [LyricsTranslationHelper].
 */
internal fun parseGoogleTResponse(body: String): List<String>? = try {
    val arr = org.json.JSONArray(body)
    val text = buildString {
        for (i in 0 until arr.length()) {
            when (val slot = arr.opt(i)) {
                is String -> append(slot)
                is org.json.JSONArray -> append(slot.optString(0))
                else -> return null
            }
        }
    }
    if (text.isBlank()) null else text.split("\n").map { it.trimEnd() }
} catch (_: Exception) {
    null
}

/**
 * Parser for the gtx `single` response: [[[ "seg", "orig", ... ], ...], ..., "src", ...].
 * Segments concatenate; trailing "\n" markers inside segments keep line boundaries.
 * Internal for the JVM spec mirror (GoogleTranslatedAlignmentTest).
 */
internal fun parseGtxSingleResponse(body: String): List<String>? = try {
    val root = org.json.JSONArray(body)
    val segments = root.optJSONArray(0) ?: return null
    val sb = StringBuilder()
    for (i in 0 until segments.length()) {
        val seg = segments.optJSONArray(i) ?: continue
        sb.append(seg.optString(0))
    }
    if (sb.isBlank()) null else sb.toString().split("\n").map { it.trimEnd() }
} catch (_: Exception) {
    null
}

object LyricsTranslationHelper {
    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    // LAST-RESORT keyless step (no user API key): Pollinations' public OpenAI-compatible endpoint.
    // There is NO embedded OpenRouter key in the app; this path sends no Authorization header.
    //
    // Only ONE model, not the old listOf("openai", "mistral", "llama", "deepseek"): probed live against
    // https://text.pollinations.ai/models, the anonymous tier exposes exactly one model (`openai-fast`,
    // aliased as `openai`); the other three return "Model not found" — they were three guaranteed failed
    // round-trips pretending to be a fallback. Since the 2026-08-29 rebuild this is step 4 of 4: Google
    // Translate (see the chain comment below) is the primary keyless translator.
    private const val FREE_KEYLESS_BASE_URL = "https://text.pollinations.ai/openai"
    private val FREE_KEYLESS_MODELS = listOf("openai")

    // FREE, reliable, keyless lyric translation — GOOGLE FIRST (owner directive 2026-08-29:
    // "quiero que las traducciones estén basadas en google translate"). The 2026-08-29 morning
    // probe found the classic gtx GET bot-blocked (Sorry HTML / 429 for both browser and okhttp
    // UAs) and Pollinations' legacy text API deprecated — the evening re-probe found TWO live
    // keyless Google paths (see GOOGLE_T_ENDPOINT_HOSTS / GOOGLE_GTX_POST_URL). Chain:
    //   1. Google Translate (dict-chrome-ex GET × 3 hosts → gtx POST) — standard modes only.
    //   2. SimpMusic community translated API (api-lyrics.simpmusic.org/v1/translated/…) —
    //      keyless, human-made translations, but partial coverage (404 = nobody translated it yet).
    //   3. The owner's Aura Worker /ai (Llama 3.3 70B) — verified live, handles ALL modes
    //      including Romanized/Transcribed.
    //   4. Pollinations legacy (last resort; often 402/429 — never the only hope again).
    private const val SIMPMUSIC_TRANSLATED_URL = "https://api-lyrics.simpmusic.org/v1/translated"
    private const val AURA_WORKER_AI_URL = "https://round-math-d64e.toberto4000.workers.dev/ai"
    private const val AURA_WORKER_AI_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"

    // GOOGLE TRANSLATE, keyless, verified live 2026-08-29 (owner directive: "quiero que las
    // traducciones estén basadas en google translate"). The classic gtx GET is bot-blocked per-IP
    // (Sorry HTML / 429 — the reason translation "never translated" on the owner's carrier IP),
    // but TWO Google paths are alive and verified with 200 JSON from THIS network:
    //   · clients5.google.com/translate_a/t?client=dict-chrome-ex — the Chrome Google Dictionary
    //     extension endpoint. GET-only, no key, no special UA, preserves "\n" (line-aligned output),
    //     45-line lyrics verified in one request, no visible rate limit at burst-of-5.
    //   · translate.googleapis.com/translate_a/single?client=gtx via POST form-urlencoded — the
    //     pattern of MorpheApp's TextTranslator (the most robust 2026 open-source reference found);
    //     the POST body passes per-IP filters that block the GET.
    // Google is step 1 of the keyless chain (standard translation modes only — it cannot Romanize
    // or Transcribe; those modes skip straight to the Worker).
    private val GOOGLE_T_ENDPOINT_HOSTS = listOf(
        "https://clients5.google.com",
        "https://translate.googleapis.com",
        "https://translate.google.com",
    )
    private const val GOOGLE_GTX_POST_URL = "https://translate.googleapis.com/translate_a/single"
    // Keep each request well under URL/payload ceilings: a full lyric is ~1-3 KB, but batching by
    // lines bounds the worst case. 4000 chars matches MorpheApp's MAXIMUM_BATCH_CHARACTERS.
    private const val GOOGLE_BATCH_MAX_CHARS = 4000

    /**
     * Keyless step 1 (owner directive 2026-08-29: Google Translate is the primary translator).
     * Line-aligned machine translation via Google's keyless endpoints, both verified live:
     * first the dict-chrome-ex `translate_a/t` (GET, preserves "\n" so a whole lyric translates in
     * ONE request per batch), then the gtx `translate_a/single` via POST (the MorpheApp pattern
     * that passes per-IP GET blocks). Batches are built by whole lines up to
     * [GOOGLE_BATCH_MAX_CHARS]; results are re-joined 1:1 with the input line count — a batch
     * whose translated line count does not match is rejected (never a shifted mis-alignment).
     * Returns null on any failure so the chain can fall through; logs carry NO user data.
     */
    private fun googleTranslatedLines(
        nonEmptyLines: List<String>,
        targetLang: String,
    ): List<String>? {
        if (nonEmptyLines.isEmpty()) return null
        val tl = targetLang.substringBefore('-').lowercase()
        if (tl.isBlank()) return null
        val batches = buildList {
            var current = StringBuilder()
            var currentLines = 0
            for (line in nonEmptyLines) {
                if (currentLines > 0 && current.length + line.length + 1 > GOOGLE_BATCH_MAX_CHARS) {
                    add(current.toString())
                    current = StringBuilder()
                    currentLines = 0
                }
                if (currentLines > 0) current.append('\n')
                current.append(line)
                currentLines++
            }
            if (currentLines > 0) add(current.toString())
        }
        val translated = mutableListOf<String>()
        for (batch in batches) {
            val one = googleTranslateBatch(batch, tl) ?: return null
            // Merge multi-line batches: lines arriving inside segments stay line-aligned.
            val pieces = one.map { seg -> seg.split('\n') }
            translated.addAll(pieces.flatten().map { it.trimEnd() })
        }
        return if (translated.size == nonEmptyLines.size && translated.any { it.isNotBlank() }) {
            translated
        } else {
            null
        }
    }

    /**
     * One Google request for one batch, trying both endpoint shapes in order. Returns the
     * translated segments (one per input line) or null. Parsers are the top-level internal
     * functions above (shared with the JVM spec test).
     */
    private fun googleTranslateBatch(batch: String, tl: String): List<String>? {
        // Path A: translate_a/t?client=dict-chrome-ex (GET). Verified: "a\nb\nc" in → one string
        // "a'\nb'\nc'" out (shape ["..."]); with sl=auto the shape is [["text","lang"]] instead.
        for (host in GOOGLE_T_ENDPOINT_HOSTS) {
            try {
                val url = "$host/translate_a/t?client=dict-chrome-ex&sl=auto&tl=$tl&q=" +
                    java.net.URLEncoder.encode(batch, "UTF-8")
                val request = Request.Builder().url(url).get().build()
                googleTranslateClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    if (body.isBlank() || body.startsWith("<")) return@use
                    parseGoogleTResponse(body)?.let { return it }
                }
            } catch (_: Exception) {
                // next host
            }
        }
        // Path B: translate_a/single?client=gtx (POST form-urlencoded) — the GET variant of this
        // endpoint is per-IP bot-blocked (429/Sorry HTML); the POST passes, per MorpheApp.
        try {
            val form = java.net.URLEncoder.encode(batch, "UTF-8")
            val body = "q=$form".toRequestBody(
                "application/x-www-form-urlencoded".toMediaType()
            )
            val request = Request.Builder()
                .url("$GOOGLE_GTX_POST_URL?client=gtx&sl=auto&dt=t&tl=$tl")
                .header("User-Agent", "Mozilla/5.0")
                .post(body)
                .build()
            googleTranslateClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                if (body.isBlank() || body.startsWith("<")) return null
                return parseGtxSingleResponse(body)
            }
        } catch (e: Exception) {
            Timber.w(e, "Google gtx POST translation failed")
            return null
        }
    }

    /**
     * Keyless step 2: the SimpMusic community translated-lyrics cache. A 200 with data is a
     * human-made translation for THIS videoId+language; a 404 is a clean miss (nobody translated it
     * yet) and any other outcome is treated as a miss — this step can only help, never block.
     * Returns the plain translated lines (one per non-empty input line) or null.
     */
    private fun simpmusicTranslatedLines(
        songId: String,
        nonEmptyCount: Int,
        targetLang: String,
    ): List<String>? = try {
        if (songId.isBlank()) return null
        val tl = targetLang.substringBefore('-').lowercase()
        if (tl.isBlank()) return null
        val request = Request.Builder()
            .url("$SIMPMUSIC_TRANSLATED_URL/$songId/$tl")
            .header("User-Agent", "okhttp/5.4.0")
            .get()
            .build()
        googleTranslateClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val data = JSONObject(body).optJSONArray("data") ?: return null
            val lyric = data.optJSONObject(0)?.optString("translatedLyric")?.takeIf { it.isNotBlank() }
                ?: data.optJSONObject(0)?.optString("lyrics")?.takeIf { it.isNotBlank() }
                ?: return null
            val lines = lyric.split("\n").map { it.trim() }
            // Accept only a shape we can align 1:1 with the requested non-empty lines: synced
            // translations carry timestamps ([00:12.34] text) which we strip before counting. An
            // empty/blank payload (split yields [""]) must never be accepted as a translation.
            val clean = lines.map { l -> l.replace("""^\[\d{2}:\d{2}\.\d{2}\]\s*""".toRegex(), "") }
            if (clean.size == nonEmptyCount && clean.any { it.isNotBlank() }) clean else null
        }
    } catch (e: Exception) {
        Timber.w(e, "SimpMusic translated-lyrics lookup failed")
        null
    }

    // Overall budget for the whole keyless path so the UI can never hang on "Translating" forever.
    private const val KEYLESS_TRANSLATE_TIMEOUT_MS = 30_000L

    // Short-timeout client for the keyless HTTP lookups (SimpMusic translated API); must be fast,
    // never block the UI. Reused from the old Google path (whose endpoint is now bot-blocked).
    private val googleTranslateClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }


    
    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearTranslationsTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

    private val _translationSaved = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val translationSaved: SharedFlow<Unit> = _translationSaved.asSharedFlow()

    private var translationJob: Job? = null
    @Volatile
    private var isCompositionActive = true


    private val translationCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    private fun getCacheKey(lyricsText: String, mode: String, language: String): String =
        "${lyricsText.hashCode()}_${mode}_$language"

    
    private fun tryParsePartialTranslation(content: String, expectedCount: Int): List<String> {
        val startIdx = content.indexOf('[')
        if (startIdx == -1) return emptyList()

        val result = mutableListOf<String>()
        var pos = startIdx + 1
        var inString = false
        var escaping = false
        val currentString = StringBuilder()

        while (pos < content.length && result.size < expectedCount) {
            val char = content[pos]

            when {
                escaping -> {
                    currentString.append(char)
                    escaping = false
                }
                char == '\\' && inString -> {
                    currentString.append(char)
                    escaping = true
                }
                char == '"' -> {
                    if (inString) {
                        result.add(currentString.toString())
                        currentString.clear()
                        inString = false
                    } else {
                        inString = true
                    }
                }
                inString -> {
                    currentString.append(char)
                }
                char == ']' -> {
                    break
                }
            }
            pos++
        }

        return result
    }

    fun getCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): List<String>? {
        val lyricsText = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        val key = getCacheKey(lyricsText, mode, language)
        return translationCache[key]
    }

    fun applyCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): Boolean {
        val cached = getCachedTranslations(lyrics, mode, language) ?: return false
        val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }

        if (cached.size >= nonEmptyEntries.size) {
            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                lyrics[originalIndex].translatedTextFlow.value = cached[idx]
            }
            return true
        }
        return false
    }

    fun triggerManualTranslation() {
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearTranslations() {
        _hasActiveTranslations.value = false
        _clearTranslationsTrigger.tryEmit(Unit)
    }

    fun hasTranslations(lyricsEntity: LyricsEntity?): Boolean = !lyricsEntity?.translatedLyrics.isNullOrBlank()

    fun clearTranslations(lyricsEntity: LyricsEntity): LyricsEntity =
        lyricsEntity.copy(
            translatedLyrics = "",
            translationLanguage = "",
            translationMode = "",
        )

    fun resetStatus() {
        _status.value = TranslationStatus.Idle
    }

    fun clearCache() {
        translationCache.clear()
    }

    fun setCompositionActive(active: Boolean) {
        isCompositionActive = active
    }

    fun cancelTranslation() {
        isCompositionActive = false
        translationJob?.cancel()
        translationJob = null
        _status.value = TranslationStatus.Idle
    }

    
    fun loadTranslationsFromDatabase(
        lyrics: List<LyricsEntry>,
        lyricsEntity: LyricsEntity?,
        targetLanguage: String,
        mode: String,
    ) {
        
        lyrics.forEach { it.translatedTextFlow.value = null }

        
        if (lyricsEntity?.translatedLyrics.isNullOrBlank()) {
            _hasActiveTranslations.value = false
            return
        }
        if (lyricsEntity.translationLanguage != targetLanguage) {
            _hasActiveTranslations.value = false
            return
        }
        if (lyricsEntity.translationMode != mode) {
            _hasActiveTranslations.value = false
            return
        }

        val translatedLines = lyricsEntity.translatedLyrics.lines()
        val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }

        nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
            if (idx < translatedLines.size) {
                lyrics[originalIndex].translatedTextFlow.value = translatedLines[idx]
            }
        }

        
        
        
        val lyricsText = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        val cacheKey = getCacheKey(lyricsText, mode, targetLanguage)
        translationCache[cacheKey] = translatedLines
        _hasActiveTranslations.value = true
    }

    fun translateLyrics(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        scope: CoroutineScope,
        context: Context,
        provider: String = "OpenRouter",
        deeplApiKey: String = "",
        deeplFormality: String = "default",
        useStreaming: Boolean = true,
        songId: String = "",
        database: MusicDatabase? = null,
        keyless: Boolean = false,
    ) {
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating

        
        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob = scope.launch(Dispatchers.IO) {
            try {
                
                val effectiveApiKey = if (provider == "DeepL") deeplApiKey else apiKey
                // keyless = FREE built-in AI (no user key needed): skip the "key required" abort and
                // route through the keyless endpoint/model cascade below. DeepL never runs keyless.
                if (!keyless && effectiveApiKey.isBlank()) {
                    _status.value = TranslationStatus.Error(context.getString(iad1tya.echo.music.R.string.ai_error_api_key_required))
                    return@launch
                }

                if (lyrics.isEmpty()) {
                    _status.value = TranslationStatus.Error(context.getString(iad1tya.echo.music.R.string.ai_error_no_lyrics))
                    return@launch
                }

                
                val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
                    if (entry.text.isNotBlank()) index to entry else null
                }

                if (nonEmptyEntries.isEmpty()) {
                    _status.value = TranslationStatus.Error(context.getString(iad1tya.echo.music.R.string.ai_error_lyrics_empty))
                    return@launch
                }

                
                val fullText = nonEmptyEntries.joinToString("\n") { it.second.text }

                
                val cacheKey = getCacheKey(fullText, mode, targetLanguage)
                val cachedTranslations = translationCache[cacheKey]
                if (cachedTranslations != null && cachedTranslations.size >= nonEmptyEntries.size) {
                    
                    nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                        if (idx < cachedTranslations.size) {
                            lyrics[originalIndex].translatedTextFlow.value = cachedTranslations[idx]
                        }
                    }
                    _hasActiveTranslations.value = true
                    _status.value = TranslationStatus.Success

                    
                    
                    if (songId.isNotBlank() && database != null) {
                        try {
                            val currentLyrics = database.lyrics(songId).first()
                            if (currentLyrics != null && currentLyrics.translatedLyrics.isNullOrBlank()) {
                                database.query {
                                    upsert(
                                        currentLyrics.copy(
                                            translatedLyrics = cachedTranslations.joinToString("\n"),
                                            translationLanguage = targetLanguage,
                                            translationMode = mode,
                                        ),
                                    )
                                }
                                _translationSaved.tryEmit(Unit)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to persist cached translations to database")
                        }
                    }

                    delay(3000)
                    if (_status.value is TranslationStatus.Success) {
                        _status.value = TranslationStatus.Idle
                    }
                    return@launch
                }

                
                if (targetLanguage.isBlank()) {
                    _status.value = TranslationStatus.Error(context.getString(iad1tya.echo.music.R.string.ai_error_language_required))
                    return@launch
                }

                
                val fullLanguageName = LanguageCodeToName[targetLanguage]
                    ?: try {
                        Locale.forLanguageTag(targetLanguage).displayLanguage.takeIf { it.isNotBlank() && it != targetLanguage }
                    } catch (e: Exception) { null }
                    ?: targetLanguage

                val result = if (provider == "DeepL") {
                    Timber.d("Using DeepL for translation")
                    DeepLService.translate(
                        text = fullText,
                        targetLanguage = targetLanguage,
                        apiKey = deeplApiKey,
                        formality = deeplFormality,
                    )
                } else if (provider == "Mistral") {
                    Timber.d("Using Mistral for translation")
                    MistralService.translate(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        model = model,
                        mode = mode,
                    )
                } else if (keyless) {
                    // FREE keyless path (owner directive 2026-08-29: GOOGLE FIRST). Verified live:
                    //   1. Google Translate — dict-chrome-ex GET (3 hosts) then gtx POST; standard
                    //      modes only (Romanized/Transcribed cannot machine-translate).
                    //   2. SimpMusic community translated API (human translations, standard modes).
                    //   3. Aura Worker (Llama 3.3) — handles every mode.
                    //   4. Pollinations — legacy last resort.
                    Timber.d("Using FREE keyless translation (Google → SimpMusic → Aura Worker → Pollinations)")
                    val standardTranslate = mode != "Romanized" && mode != "Transcribed"
                    withTimeoutOrNull(KEYLESS_TRANSLATE_TIMEOUT_MS) {
                        var freeResult: Result<List<String>> =
                            Result.failure(Exception("No free translation available"))

                        // 1) Google Translate (owner directive: the primary). One batched,
                        // line-aligned request; falls through on any failure.
                        if (standardTranslate) {
                            val google = googleTranslatedLines(
                                nonEmptyLines = nonEmptyEntries.map { it.second.text },
                                targetLang = targetLanguage,
                            )
                            if (google != null) {
                                Timber.d("Google Translate returned ${google.size} lines")
                                freeResult = Result.success(google)
                            }
                        }

                        // 2) SimpMusic community cache (no key, human translations, standard modes only).
                        if (freeResult.isFailure && standardTranslate) {
                            val community = simpmusicTranslatedLines(
                                songId = songId,
                                nonEmptyCount = nonEmptyEntries.size,
                                targetLang = targetLanguage,
                            )
                            if (community != null) {
                                Timber.d("SimpMusic translated API returned ${community.size} lines")
                                freeResult = Result.success(community)
                            }
                        }

                        // 2) Aura Worker (owner-hosted keyless LLM relay). Works for every mode.
                        if (freeResult.isFailure) {
                            Timber.d("Falling back to Aura Worker translation")
                            freeResult = OpenRouterService.translate(
                                text = fullText,
                                targetLanguage = fullLanguageName,
                                apiKey = "",
                                baseUrl = AURA_WORKER_AI_URL,
                                model = AURA_WORKER_AI_MODEL,
                                mode = mode,
                            )
                        }

                        // 3) Legacy Pollinations last resort (often 402/429 since its deprecation).
                        if (freeResult.isFailure) {
                            Timber.d("Falling back to legacy Pollinations cascade")
                            for (freeModel in FREE_KEYLESS_MODELS) {
                                freeResult = OpenRouterService.translate(
                                    text = fullText,
                                    targetLanguage = fullLanguageName,
                                    apiKey = "",
                                    baseUrl = FREE_KEYLESS_BASE_URL,
                                    model = freeModel,
                                    mode = mode,
                                )
                                if (freeResult.isSuccess) break
                            }
                        }
                        freeResult
                    } ?: Result.failure(
                        Exception(context.getString(iad1tya.echo.music.R.string.ai_error_translation_failed)),
                    )
                } else if (useStreaming && provider != "Custom") {
                    Timber.d("Using streaming for translation with provider: $provider")
                    var translatedLines: List<String>? = null
                    var hasError = false
                    var errorMessage = ""
                    val contentAccumulator = StringBuilder()

                    OpenRouterStreamingService.streamTranslation(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        mode = mode,
                    ).collect { chunk ->
                        Timber.v("Received streaming chunk: $chunk")
                        when (chunk) {
                            is OpenRouterStreamingService.StreamChunk.Content -> {
                                contentAccumulator.append(chunk.text)

                                val partialContent = contentAccumulator.toString()
                                val partialResult = tryParsePartialTranslation(partialContent, nonEmptyEntries.size)
                                if (partialResult.isNotEmpty()) {
                                    partialResult.forEachIndexed { idx, translation ->
                                        if (idx < nonEmptyEntries.size && translation.isNotBlank()) {
                                            val originalIndex = nonEmptyEntries[idx].first
                                            lyrics[originalIndex].translatedTextFlow.value = translation
                                        }
                                    }
                                    _status.value = TranslationStatus.Translating
                                }
                            }
                            is OpenRouterStreamingService.StreamChunk.Complete -> {
                                Timber.d("Streaming complete with ${chunk.translatedLines.size} lines")
                                translatedLines = chunk.translatedLines
                            }
                            is OpenRouterStreamingService.StreamChunk.Error -> {
                                Timber.e("Streaming error: ${chunk.message}")
                                hasError = true
                                errorMessage = chunk.message
                            }
                        }
                    }

                    Timber.d("Streaming collection complete. hasError=$hasError, translatedLines=${translatedLines?.size}")
                    if (hasError) {
                        Result.failure(Exception(errorMessage))
                    } else if (translatedLines != null) {
                        Result.success(translatedLines)
                    } else {
                        Result.failure(Exception("No translation received"))
                    }
                } else {
                    Timber.d("Using non-streaming for translation")
                    OpenRouterService.translate(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        mode = mode,
                    )
                }

                result.onSuccess { translatedLines ->
                    if (!isCompositionActive) {
                        return@onSuccess
                    }

                    
                    val cacheKey2 = getCacheKey(fullText, mode, targetLanguage)
                    translationCache[cacheKey2] = translatedLines

                    
                    if (songId.isNotBlank() && database != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val currentLyrics = database.lyrics(songId).first()
                                if (currentLyrics != null) {
                                    database.query {
                                        upsert(
                                            currentLyrics.copy(
                                                translatedLyrics = translatedLines.joinToString("\n"),
                                                translationLanguage = targetLanguage,
                                                translationMode = mode,
                                            ),
                                        )
                                    }
                                    
                                    _translationSaved.tryEmit(Unit)
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to save translated lyrics to database")
                            }
                        }
                    }

                    
                    val expectedCount = nonEmptyEntries.size

                    when {
                        translatedLines.size >= expectedCount -> {
                            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                                lyrics[originalIndex].translatedTextFlow.value = translatedLines[idx]
                            }
                            _hasActiveTranslations.value = true
                            _status.value = TranslationStatus.Success
                        }
                        translatedLines.size < expectedCount -> {
                            translatedLines.forEachIndexed { idx, translation ->
                                if (idx < nonEmptyEntries.size) {
                                    val originalIndex = nonEmptyEntries[idx].first
                                    lyrics[originalIndex].translatedTextFlow.value = translation
                                }
                            }
                            _hasActiveTranslations.value = true
                            _status.value = TranslationStatus.Success
                        }
                        else -> {
                            _status.value = TranslationStatus.Error(context.getString(iad1tya.echo.music.R.string.ai_error_unexpected))
                        }
                    }

                    
                    delay(3000)
                    if (_status.value is TranslationStatus.Success) {
                        _status.value = TranslationStatus.Idle
                    }
                }.onFailure { error ->
                    if (!isCompositionActive) {
                        return@onFailure
                    }

                    val errorMessage = error.message ?: context.getString(iad1tya.echo.music.R.string.ai_error_unknown)
                    _status.value = TranslationStatus.Error(errorMessage)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException && isCompositionActive) {
                    val errorMessage = e.message ?: context.getString(iad1tya.echo.music.R.string.ai_error_translation_failed)
                    _status.value = TranslationStatus.Error(errorMessage)
                }
            }
        }
    }

    sealed class TranslationStatus {
        data object Idle : TranslationStatus()
        data object Translating : TranslationStatus()
        data object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }
}
