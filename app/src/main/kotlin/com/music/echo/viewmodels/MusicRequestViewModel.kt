package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.playlistimport.AiPlaylistGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Pedir música": una descripción en lenguaje natural → canciones que empiezan a sonar.
 *
 * 🔴 Petición del dueño (2026-09-17): *"hay una función que tiene YouTube Music que se llama pedir
 * música, ¿se puede agregar esto a mi app pero de manera gratuita y funcional?"*, y después la forma
 * exacta: *"un híbrido que lleve la IA y cuando no detecte que la IA está disponible lo haga de manera
 * gratuita sin IA, pero que no haga referencia si lo hizo o no lo hizo con IA; cuando dé la respuesta
 * […] que solo entregue el resultado"*.
 *
 * ## En qué se diferencia de [AiPlaylistViewModel], que se parece mucho
 * Aquel **guarda una playlist** en la biblioteca y por eso etiqueta el origen ("(sin IA)"): una lista
 * guardada que no se parece a lo pedido queda ahí indistinguible de una curada, y la etiqueta es lo que
 * convierte esa sorpresa en información. Este **no guarda nada**: suena y ya. Ahí la etiqueta no
 * informa de nada accionable — solo interrumpe con detalle de implementación a alguien que pidió
 * canciones. De ahí que este estado no tenga ningún campo sobre el origen: no es que se omita al
 * mostrarlo, es que **no llega hasta aquí**. El origen se registra en el log dentro de
 * [AiPlaylistGenerator.produce], que es donde sirve para diagnosticar sin molestar.
 */
@HiltViewModel
class MusicRequestViewModel
@Inject
constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow<MusicRequestUiState>(MusicRequestUiState.Idle)
    val state: StateFlow<MusicRequestUiState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Cuántas canciones pedir.
     *
     * 🔴 Diez, por orden del dueño (2026-09-17): *"que el máximo de canciones que busque sean 10 para
     * que responda más rápido, y luego la cola infinita inteligente continuará con el mismo algoritmo
     * para completar la función"*. Eran 25.
     *
     * Y encaja con cómo está hecho el reproductor, no es solo "menos": la cola que se pone aquí es una
     * `ListQueue`, y `MusicService` guarda TODAS sus canciones como semilla de la radio
     * (`radioSeedPool`). Al acabar las diez, la continuación multi-semilla las usa enteras — con su
     * mezcla de artistas y géneros — para seguir con el mismo algoritmo de siempre. O sea que diez no
     * es una cola corta: es la semilla, y lo que viene después ya lo pone el algoritmo.
     */
    private val requestedCount = 10

    fun request(
        prompt: String,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
    ) {
        if (job?.isActive == true) return
        if (prompt.isBlank()) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.value = MusicRequestUiState.Working(0, 0)
            val produced = AiPlaylistGenerator.produce(
                database = database,
                prompt = prompt,
                count = requestedCount,
                provider = provider,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                onResolveProgress = { done, total ->
                    _state.value = MusicRequestUiState.Working(done, total)
                },
            )
            _state.value = when {
                produced == null || produced.songs.isEmpty() -> MusicRequestUiState.Empty
                else -> MusicRequestUiState.Ready(produced.name, produced.songs)
            }
        }
    }

    /** Cancela lo que haya en marcha y vuelve al estado inicial. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = MusicRequestUiState.Idle
    }
}

sealed interface MusicRequestUiState {
    data object Idle : MusicRequestUiState

    /** Buscando. [total] es 0 mientras todavía no se sabe cuántas hay que resolver. */
    data class Working(val done: Int, val total: Int) : MusicRequestUiState

    /** Listo para sonar. El llamante pone la cola y llama a `reset()`. */
    data class Ready(val title: String, val songs: List<MediaMetadata>) : MusicRequestUiState

    /**
     * No se encontró nada. **Un estado propio y no un `Error`**: aquí no hay nada roto que explicar —
     * ni la IA ni la búsqueda dieron canciones para esa descripción — y decirle "error" por una
     * petición demasiado rara sería culpar a la app de algo que no es un fallo.
     */
    data object Empty : MusicRequestUiState
}
