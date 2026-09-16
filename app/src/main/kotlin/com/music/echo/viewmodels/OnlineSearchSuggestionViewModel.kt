

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.utils.YouTubeUrlParser
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.SearchHistory
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.getNonBlocking
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    init {
        viewModelScope.launch {
            query
                // Near-instant, YouTube-style suggestions from the very first letter. 80 ms is short
                // enough to feel immediate yet still coalesces a fast burst of keystrokes; flatMapLatest
                // cancels any in-flight request when the next character arrives, so no request pile-up.
                .debounce(80)
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        database.searchHistory().map { history ->
                            SearchSuggestionViewState(
                                history = history,
                            )
                        }
                    } else {
                        val parsedUrl = YouTubeUrlParser.parse(query)
                        // getNonBlocking, no get: `get` es `runBlocking { data.first() }` y pasa por el
                        // actor del DataStore, esperando detrás de cualquier edit{} en vuelo. Eran DOS
                        // lecturas bloqueantes de disco POR TECLA en la ruta más sensible a la latencia
                        // de toda la app. El snapshot de proceso existe justo para esto.
                        val hideExplicit = context.dataStore.getNonBlocking(HideExplicitKey, false)
                        val hideVideoSongs = context.dataStore.getNonBlocking(HideVideoSongsKey, false)

                        database
                            .searchHistory(query)
                            .map { it.take(3) }
                            .flatMapLatest { history ->
                                // 🔴 ORDEN DEL DUEÑO (2026-09-16): *"cuando busco algo en SimpMusic las
                                // respuestas son full rápidas… hasta los resultados mientras voy
                                // escribiendo"*.
                                //
                                // Antes, TODO este bloque colgaba de un `YouTube.searchSuggestions(query)`
                                // resuelto ANTES de construir el flujo: el panel entero —incluido el
                                // HISTORIAL LOCAL, que es una consulta a Room y está listo al instante—
                                // esperaba al viaje de red en CADA tecla. Con red lenta eso es mirar el
                                // estado anterior mientras escribes, que es exactamente la diferencia que
                                // él nota. SimpMusic no hace esperar a nada local.
                                //
                                // Dos emisiones: lo que ya tenemos, y luego lo mismo enriquecido. El
                                // debounce de 80 ms y el flatMapLatest de arriba siguen igual — no eran
                                // el problema, y flatMapLatest cancela esta corrutina en cuanto llega la
                                // siguiente tecla, así que la emisión tardía nunca pisa a una más nueva.
                                flow {
                                    emit(
                                        SearchSuggestionViewState(
                                            history = history,
                                            isFromLink = parsedUrl != null,
                                        )
                                    )

                                    val parsedItem =
                                        if (parsedUrl != null) fetchParsedUrlItem(parsedUrl) else null
                                    val result =
                                        if (parsedUrl != null) {
                                            null
                                        } else {
                                            YouTube.searchSuggestions(query).getOrNull()
                                        }

                                    emit(
                                        SearchSuggestionViewState(
                                            history = history,
                                            suggestions =
                                            result
                                                ?.queries
                                                ?.filter { suggestionQuery ->
                                                    history.none { it.query == suggestionQuery }
                                                }.orEmpty(),
                                            items = listOfNotNull(parsedItem) +
                                            result
                                                ?.recommendedItems
                                                ?.distinctBy { it.id }
                                                ?.filter { it.id != parsedItem?.id }
                                                ?.filterExplicit(hideExplicit)
                                                ?.filterVideoSongs(hideVideoSongs)
                                                .orEmpty(),
                                            isFromLink = parsedUrl != null
                                        )
                                    )
                                }
                            }
                    }
                }.collect {
                    _viewState.value = it
                }
        }
    }

    private suspend fun fetchParsedUrlItem(parsedUrl: YouTubeUrlParser.ParsedUrl): YTItem? {
        println("[LINK_PARSE_DEBUG] Fetching metadata for: $parsedUrl")
        return try {
            val item = when (parsedUrl) {
                is YouTubeUrlParser.ParsedUrl.Video -> {
                    YouTube.queue(listOf(parsedUrl.id)).getOrNull()?.firstOrNull()
                }

                is YouTubeUrlParser.ParsedUrl.Artist -> {
                    YouTube.artist(parsedUrl.id).getOrNull()?.artist
                }
            }
            println("[LINK_PARSE_DEBUG] Fetch successful: ${item?.id} (${item?.javaClass?.simpleName})")
            item
        } catch (e: Exception) {
            println("[LINK_PARSE_DEBUG] Fetch failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
    val isFromLink: Boolean = false,
)
