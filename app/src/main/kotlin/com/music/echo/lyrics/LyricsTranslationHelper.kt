

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
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

object LyricsTranslationHelper {
    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    // FREE keyless translation (no user API key). There is NO embedded OpenRouter key in the app:
    // AiPlaylistService's "free" path is fully KEYLESS (no Authorization header) against the public
    // OpenAI-compatible Pollinations endpoint. We reuse the same endpoint here so lyric translation works
    // without the user configuring any key.
    //
    // Only ONE model, not the old listOf("openai", "mistral", "llama", "deepseek"): probed live against
    // https://text.pollinations.ai/models, the anonymous tier exposes exactly one model (`openai-fast`,
    // aliased as `openai`); the other three return "Model not found" — they were three guaranteed failed
    // round-trips pretending to be a fallback. This path is only a FALLBACK anyway: Google Translate above
    // is the primary keyless translator (#31).
    private const val FREE_KEYLESS_BASE_URL = "https://text.pollinations.ai/openai"
    private val FREE_KEYLESS_MODELS = listOf("openai")

    // FREE, reliable, keyless lyric translation. 2026-08-29 live probe: Google Translate's public
    // gtx endpoint now returns the "Sorry..." bot HTML (or 429) for BOTH browser and okhttp UAs,
    // and Pollinations' legacy text API is deprecated (402 "budget too low" for anything but the
    // barest request, 429 otherwise). Both former keyless primaries are effectively dead, which is
    // why lyric translation showed "error" with no usable fallback left. New keyless chain:
    //   1. SimpMusic community translated-lyrics API (api-lyrics.simpmusic.org/v1/translated/…) —
    //      keyless, human-made translations, but partial coverage (404 = nobody translated it yet).
    //   2. The owner's Aura Worker /ai (Llama 3.3 70B) — same relay AiPlaylistService uses; verified
    //      live 200 with correct OpenAI-shape completions, works for ALL modes including Romanized.
    //   3. Pollinations legacy (kept as last resort; often 402/429 — never the only hope again).
    private const val SIMPMUSIC_TRANSLATED_URL = "https://api-lyrics.simpmusic.org/v1/translated"
    private const val AURA_WORKER_AI_URL = "https://round-math-d64e.toberto4000.workers.dev/ai"
    private const val AURA_WORKER_AI_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"

    /**
     * Keyless step 1: the SimpMusic community translated-lyrics cache. A 200 with data is a
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
                    // FREE keyless path (2026-08-29 rebuild — see the chain comment at the constants).
                    // Old chain was Google-gtx-first; that endpoint is bot-blocked now, so the whole
                    // keyless path surfaced "error" and this is the fix:
                    //   1. SimpMusic community translated API (only for standard translation; it
                    //      cannot Romanize/Transcribe). Fast, human-made, partial coverage.
                    //   2. Aura Worker (Llama 3.3) — handles every mode.
                    //   3. Pollinations — legacy last resort.
                    Timber.d("Using FREE keyless translation (SimpMusic translated → Aura Worker → Pollinations)")
                    val standardTranslate = mode != "Romanized" && mode != "Transcribed"
                    withTimeoutOrNull(KEYLESS_TRANSLATE_TIMEOUT_MS) {
                        var freeResult: Result<List<String>> =
                            Result.failure(Exception("No free translation available"))

                        // 1) SimpMusic community cache (no key, human translations, standard modes only).
                        if (standardTranslate) {
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
