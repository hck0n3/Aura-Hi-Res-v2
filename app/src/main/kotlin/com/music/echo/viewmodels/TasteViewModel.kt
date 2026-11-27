package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.api.AiPlaylistService
import iad1tya.echo.music.constants.AiProviderKey
import iad1tya.echo.music.constants.DefaultOpenRouterModel
import iad1tya.echo.music.constants.OpenRouterApiKey
import iad1tya.echo.music.constants.OpenRouterBaseUrlKey
import iad1tya.echo.music.constants.OpenRouterModelKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.reco.GenreCache
import iad1tya.echo.music.reco.TasteAiConsentKey
import iad1tya.echo.music.reco.TasteAiGate
import iad1tya.echo.music.reco.TasteSummary
import iad1tya.echo.music.utils.dataStore
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Registry row 906 — «Tu gusto» (Ajustes ▸ IA). The local summary is read once when the screen opens, from the database and the
 * on-device genre cache: no network. The AI description runs only from the «Describir con IA» button and only through
 * [TasteAiGate], which reads the stored consent right before sending. The AI answer stays in this ViewModel (never stored, never
 * logged). Logs carry counts only (AGENTS rule 4); nothing runs in the background (rule 7).
 */
@HiltViewModel
class TasteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    sealed interface Ai {
        data object Idle : Ai
        data object Asking : Ai
        data object Failed : Ai
        data class Done(val taste: TasteSummary.AiTaste) : Ai
    }

    data class UiState(
        val loading: Boolean = true,
        val stats: TasteSummary.Stats = TasteSummary.Stats(emptyList(), emptyList(), emptyList()),
        val local: TasteSummary.Local? = null,
        val ai: Ai = Ai.Idle,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The consent the user accepted (see [TasteAiGate.accepted]). */
    val consentAccepted: StateFlow<Boolean> = context.dataStore.data
        .map { TasteAiGate.accepted(it[TasteAiConsentKey]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var aiJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = try {
                val artists = database.mostPlayedArtists(fromTimeStamp = 0L, limit = TasteSummary.MAX_ARTISTS * 3).first()
                    .map { TasteSummary.Count(it.artist.name, it.songCount) }
                val genres = GenreCache.snapshot(context)
                val years = database.mostPlayedSongs(fromTimeStamp = 0L, limit = TOP_SONGS).first()
                    .map { it.song.year ?: it.album?.year }
                TasteSummary.stats(artists, { genres[it.lowercase()] }, years)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("TASTE local failed: %s", e.javaClass.simpleName)
                TasteSummary.Stats(emptyList(), emptyList(), emptyList())
            }
            _state.update { it.copy(loading = false, stats = stats, local = TasteSummary.local(stats)) }
        }
    }

    /** «Describir con IA». Sends only past [TasteAiGate] (consent accepted, something to describe). */
    fun askAi() {
        if (aiJob?.isActive == true || _state.value.loading) return
        _state.update { it.copy(ai = Ai.Asking) }
        aiJob = viewModelScope.launch(Dispatchers.IO) {
            val stats = _state.value.stats
            val outcome = try {
                val prefs = context.dataStore.data.first()
                val language = context.resources.configuration.locales[0].language
                withTimeoutOrNull(AI_CAP_MS) {
                    TasteAiGate.ask(prefs[TasteAiConsentKey], stats, language) { system, user ->
                        AiPlaylistService.describeTaste(
                            system = system,
                            user = user,
                            provider = prefs[AiProviderKey] ?: "OpenRouter",
                            apiKey = prefs[OpenRouterApiKey] ?: "",
                            baseUrl = prefs[OpenRouterBaseUrlKey] ?: "",
                            model = prefs[OpenRouterModelKey] ?: DefaultOpenRouterModel,
                        ).getOrNull()
                    }
                } ?: TasteAiGate.Outcome.Failed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TasteAiGate.Outcome.Failed
            }
            Timber.i(
                "TASTE_AI outcome=%s artists=%d genres=%d decades=%d",
                outcome.javaClass.simpleName, stats.artists.size, stats.genres.size, stats.decades.size,
            )
            _state.update {
                it.copy(
                    ai = when (outcome) {
                        is TasteAiGate.Outcome.Done -> Ai.Done(outcome.taste)
                        TasteAiGate.Outcome.Failed -> Ai.Failed
                        TasteAiGate.Outcome.NotAccepted, TasteAiGate.Outcome.NothingToSend -> Ai.Idle
                    },
                )
            }
        }
    }

    /** The consent screen's «Aceptar»: only stores the acceptance; nothing is sent until «Describir con IA». */
    fun acceptConsent() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { context.dataStore.edit { it[TasteAiConsentKey] = TasteAiGate.CONSENT_VERSION } }
                .onFailure { if (it is CancellationException) throw it else Timber.w("TASTE consent not stored: %s", it.javaClass.simpleName) }
        }
    }

    /** «Retirar el permiso»: back to off, and the AI text leaves the screen. */
    fun withdrawConsent() {
        aiJob?.cancel()
        _state.update { it.copy(ai = Ai.Idle) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { context.dataStore.edit { it.remove(TasteAiConsentKey) } }
                .onFailure { if (it is CancellationException) throw it else Timber.w("TASTE consent not withdrawn: %s", it.javaClass.simpleName) }
        }
    }

    private companion object {
        /** How many of the most played songs give the decades. */
        const val TOP_SONGS = 100

        /** The same 60 s end-to-end cap as the keyless client of [AiPlaylistService]. */
        const val AI_CAP_MS = 60_000L
    }
}
