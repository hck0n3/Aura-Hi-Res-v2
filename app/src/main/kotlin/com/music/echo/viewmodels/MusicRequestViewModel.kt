package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.playlistimport.AiPlaylistGenerator
import iad1tya.echo.music.playlistimport.MusicRequestFocus
import iad1tya.echo.music.playlistimport.MusicRequestFocusFilter
import iad1tya.echo.music.playlistimport.MusicRequestHistory
import iad1tya.echo.music.reco.AffinityEngine
import iad1tya.echo.music.reco.GenreCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
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
    private val dislikeStore: iad1tya.echo.music.dislike.DislikeStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<MusicRequestUiState>(MusicRequestUiState.Idle)
    val state: StateFlow<MusicRequestUiState> = _state.asStateFlow()

    /**
     * Canciones que se suman a la cola YA sonando, en segundo plano — ver [firstBatchCount].
     * `SharedFlow` (no `StateFlow`) porque esto es un evento de una sola vez ("añade esto ahora"), no
     * un estado que una recomposición tardía deba volver a aplicar.
     */
    private val _extend = MutableSharedFlow<ExtendQueue>(extraBufferCapacity = 1)
    val extend: SharedFlow<ExtendQueue> = _extend.asSharedFlow()

    private var job: Job? = null

    /**
     * La última petición que sonó, para poder VERLA y GUARDARLA después (YouTube Music muestra la estación con
     * sus primeras canciones y la deja añadir a la biblioteca; dueño, 2026-10-18). `songs` crece cuando llega el
     * relleno en segundo plano; [LastMusicRequest.fillRunning] dice si todavía puede crecer.
     */
    private val _last = MutableStateFlow<LastMusicRequest?>(null)
    val last: StateFlow<LastMusicRequest?> = _last.asStateFlow()

    /**
     * Deja una canción FUERA de la lista que se va a guardar (dueño, 2026-10-18: que lo que muestra la tarjeta sea
     * interactuable). No toca la cola que ya suena: es la lista a guardar la que cambia.
     */
    fun removeFromLast(songId: String) {
        _last.update { current ->
            current?.takeIf { !it.saved }?.copy(songs = current.songs.filterNot { it.id == songId }) ?: current
        }
    }

    /** Quita la tarjeta de resultado (no toca lo que suena ni lo que ya se guardó). */
    fun dismissLast() {
        _last.value = null
    }

    /**
     * Guarda la última petición como lista local con TODAS las canciones que se conocen ahora. Lo llama la hoja
     * solo cuando el relleno ya terminó, para no guardar un ocho de veinticinco.
     */
    fun saveLastAsPlaylist() {
        val snapshot = _last.value ?: return
        if (snapshot.saved || snapshot.songs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val playlist = PlaylistEntity(
                    name = snapshot.title.trim().ifBlank { "Pedir música" }.take(MAX_SAVED_NAME),
                    bookmarkedAt = LocalDateTime.now(),
                    isEditable = true,
                    isLocal = true,
                )
                database.transaction {
                    insert(playlist)
                    snapshot.songs.forEachIndexed { index, metadata ->
                        insert(metadata)
                        insert(PlaylistSongMap(playlistId = playlist.id, songId = metadata.id, position = index))
                    }
                }
            }.isSuccess
            if (ok) _last.update { current -> current?.takeIf { it.contextId == snapshot.contextId }?.copy(saved = true) ?: current }
        }
    }

    /**
     * Cuántas canciones esperar antes de arrancar a sonar.
     *
     * 🔴 Diez, por orden del dueño (2026-09-17): *"que el máximo de canciones que busque sean 10 para
     * que responda más rápido"*. Eran 25. Y el dueño pidió de vuelta el 2026-09-22: *"quiero que
     * siempre reproduzca de inmediato y en segundo plano agregue 25 canciones"* — es decir, ya no hace
     * falta elegir entre "rápido" y "25": arrancar con un lote más chico y completar el resto sin que
     * nadie espere consigue las dos cosas a la vez. [firstBatchCount] es ese primer lote (arranca en
     * segundos, igual que antes); [targetCount] es el total al que se completa en segundo plano.
     */
    private val firstBatchCount = 8

    /** Ver [firstBatchCount]. Vuelve a 25 (el valor de antes de 2026-09-17). */
    private val targetCount = 25

    /**
     * Cuántos eventos de escucha lee el perfil de gusto. Seiscientos son varias semanas de uso real y
     * una sola consulta; los 3000 del reproductor son para su perfil completo, que se cachea cinco
     * minutos y aquí no hace falta: esto solo tiene que ordenar sesenta candidatas.
     */
    private val TASTE_EVENTS = 600

    /** Ids de canciones ya reproducidas que lee «Descubrir» (solo ids; una consulta). */
    private val PLAYED_IDS_LIMIT = 5000

    /** Largo máximo del nombre de la lista que se guarda. */
    private val MAX_SAVED_NAME = 60

    fun request(
        prompt: String,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        focus: MusicRequestFocus = MusicRequestFocus.All,
    ) {
        if (job?.isActive == true) return
        if (prompt.isBlank()) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.value = MusicRequestUiState.Working(0, 0)
            // 🔴 SU GUSTO, CONSTRUIDO EN PARALELO (dueño, 2026-09-17: *"haz lo mejor de esa
            // recomendación"*).
            //
            // La app ya sabe qué le gusta — es el mismo perfil que usan la radio, Inicio y el
            // aleatorio inteligente — y "pedir música" lo ignoraba por completo: le daba los 80 de
            // cualquiera en vez de LOS SUYOS. Se construye aquí con el mismo motor
            // ([AffinityEngine]), y **arrancado antes** de la petición para que sus lecturas de base
            // de datos corran mientras la red trabaja: el usuario no espera ni un milisegundo más.
            //
            // Solo eventos y "No me gusta": es una lectura, no las cinco que hace el reproductor para
            // su perfil completo. Con eso ya sabe de artistas y géneros, que es lo que hace falta para
            // ELEGIR entre sesenta candidatas. Si falla, null — y entonces no se personaliza nada, que
            // es exactamente como estaba.
            val tasteJob = async {
                runCatching {
                    val events = database.recentEventsWithSong(TASTE_EVENTS).first()
                    // Auditoría del algoritmo (ronda 9, dueño: "vela que nada sea placebo"): sin
                    // artistGenres, el bono de género de AffinityEngine (el más grande de los tres:
                    // 0.55, más que el de artista o el de carril) nunca se sumaba acá — el comentario
                    // de esta misma función prometía "artista y género" pero solo llegaba el primero.
                    // GenreCache.snapshot es una lectura en memoria (SharedPreferences ya cacheadas),
                    // no red — gratis en este presupuesto reducido.
                    AffinityEngine.buildProfile(
                        events, dislikeStore.snapshot(), artistGenres = GenreCache.snapshot(context),
                    )
                }.getOrNull()
            }
            val taste = tasteJob.await()
            // «Conocido / Descubrir»: lo ya reproducido (solo ids, una consulta) y «¿le suena este artista?»
            // según el MISMO perfil de gusto. Solo se lee si hay filtro.
            val playedIds: Set<String> = if (focus == MusicRequestFocus.All) emptySet() else {
                runCatching { database.recentPlayedSongIds(PLAYED_IDS_LIMIT).toHashSet() }.getOrDefault(emptySet())
            }
            // Y por título+artista: la misma canción llega con ids distintos (pista de álbum, vídeo oficial…).
            val playedKeys: Set<String> = if (focus != MusicRequestFocus.Discover) emptySet() else {
                runCatching {
                    database.recentPlayedTitleArtists(PLAYED_IDS_LIMIT)
                        .mapTo(HashSet()) { iad1tya.echo.music.playback.NoRepeatKey.of(it.title, it.artist) }
                }.getOrDefault(emptySet())
            }
            val isPlayed: (MediaMetadata) -> Boolean = { m ->
                m.id in playedIds || MusicRequestFocusFilter.songKey(m) in playedKeys
            }
            val isFamiliar: (MediaMetadata) -> Boolean = { m ->
                taste != null && m.artists.any { a ->
                    MusicRequestFocusFilter.artistVariants(a.name).any { taste.isKnownArtist(it) }
                }
            }
            val familiarity: (MediaMetadata) -> Double = { m ->
                taste?.scoreNames(m.artists.map { it.name }, m.title) ?: 0.0
            }
            // Fila 361: "pon 50 canciones de salsa" — the count he asked for (5..100), else the usual 25.
            val intent = iad1tya.echo.music.playlistimport.MusicRequestIntent.parse(prompt)
            val requestedTotal = intent.count?.coerceIn(5, 100) ?: targetCount
            val produced = AiPlaylistGenerator.produce(
                database = database,
                prompt = prompt,
                count = MusicRequestFocusFilter.candidatesFor(focus, minOf(firstBatchCount, requestedTotal)),
                context = context,
                provider = provider,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                onResolveProgress = { done, total ->
                    _state.value = MusicRequestUiState.Working(done, total)
                },
                taste = taste,
            )
            if (produced == null || produced.songs.isEmpty()) {
                _state.value = MusicRequestUiState.Empty
                return@launch
            }
            // Con filtro se pidieron el doble de candidatos: se aplica y se deja el primer lote del tamaño de
            // siempre (nunca vacío: ver MusicRequestFocusFilter).
            val selected = MusicRequestFocusFilter.select(focus, produced.songs, isFamiliar, isPlayed, familiarity)
            val firstSongs = selected.songs.take(minOf(firstBatchCount, requestedTotal))
            MusicRequestHistory.record(prompt)
            // Un id propio de ESTE pedido: la cola no lleva contextId todavía (nunca lo necesitó,
            // era una lista suelta), y ahora hace falta para que el relleno de abajo sepa que sigue
            // siendo la MISMA cola antes de sumarle canciones — si mientras tanto puso otra cosa a
            // sonar, el relleno no se mete ahí.
            val contextId = "MR:" + UUID.randomUUID()
            // Fila 361: the smart queue that continues this request keeps exactly what was asked.
            if (intent.styles.isNotEmpty() || intent.language != null) {
                iad1tya.echo.music.reco.RequestStyleTargets.put(
                    contextId,
                    iad1tya.echo.music.reco.StyleContinuity.Target(
                        styles = intent.styles.ifEmpty { null },
                        language = intent.language,
                        christian = intent.christian,
                    ),
                )
            }
            val fillWillRun = firstSongs.size < requestedTotal
            _last.value = LastMusicRequest(
                produced.name, firstSongs, contextId, fillRunning = fillWillRun,
                focus = focus, focusRelaxed = selected.relaxed,
            )
            _state.value = MusicRequestUiState.Ready(produced.name, firstSongs, contextId)

            // EL RELLENO EN SEGUNDO PLANO (dueño, 2026-09-22: "que siempre reproduzca de inmediato y
            // en segundo plano agregue 25 canciones"). Lanzado APARTE de `job`, a propósito: `job` es
            // "la espera del primer lote", y el llamante hace `reset()` (que cancela `job`) apenas ve
            // este `Ready` para poder cerrar la hoja — si el relleno viviera en el mismo `job`, ese
            // `reset()` lo mataría antes de que arrancara. Aparte, tampoco bloquea un pedido nuevo: el
            // guard de `request()` solo mira `job`, así que pedir otra cosa mientras esto sigue en
            // marcha no espera a que termine (y su resultado, si llega tarde, trae un contextId viejo
            // que `extendQueueForContext` ya no reconoce si el usuario cambió de cola mientras tanto).
            if (fillWillRun) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        fillInBackground(
                            contextId, prompt, requestedTotal, provider, apiKey, baseUrl, model, taste, firstSongs,
                            focus, isFamiliar, isPlayed, familiarity,
                        )
                    } finally {
                        _last.update { current ->
                            current?.takeIf { it.contextId == contextId }?.copy(fillRunning = false) ?: current
                        }
                    }
                }
            }
        }
    }

    /**
     * Completa hasta [target] canciones para la MISMA petición y las manda por [_extend]. Es un
     * segundo pedido completo (no una continuación del primero: `produce` no expone eso hoy) — mismo
     * prompt y mismo gusto, así que en la práctica trae una versión más larga del mismo resultado; se
     * filtra por id lo que el primer lote ([alreadyPlaying]) ya incluyó para no repetir nada.
     */
    private suspend fun fillInBackground(
        contextId: String,
        prompt: String,
        target: Int,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        taste: iad1tya.echo.music.reco.TasteProfile?,
        alreadyPlaying: List<MediaMetadata>,
        focus: MusicRequestFocus,
        isFamiliar: (MediaMetadata) -> Boolean,
        isPlayed: (MediaMetadata) -> Boolean,
        familiarity: (MediaMetadata) -> Double,
    ) {
        val fuller = runCatching {
            AiPlaylistGenerator.produce(
                database = database,
                prompt = prompt,
                // Fila 361: only what is MISSING — the first batch is already marked recent, so a full
                // count here used to bring 25 new songs on top of the first 8 (a 33-song queue).
                count = MusicRequestFocusFilter.candidatesFor(focus, (target - alreadyPlaying.size).coerceAtLeast(1)),
                provider = provider,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                taste = taste,
                context = context,
            )
        }.getOrNull()
        if (fuller == null || fuller.songs.isEmpty()) return
        val already = alreadyPlaying.mapTo(HashSet()) { it.id }
        val extra = MusicRequestFocusFilter
            .select(focus, fuller.songs.filter { it.id !in already }, isFamiliar, isPlayed, familiarity)
            .songs
            .take((target - alreadyPlaying.size).coerceAtLeast(1))
        if (extra.isNotEmpty()) {
            _last.update { current ->
                current?.takeIf { it.contextId == contextId }?.copy(songs = current.songs + extra) ?: current
            }
            // Fila 361: straight to the service — the Home card may be gone by now (see MusicRequestFill).
            iad1tya.echo.music.playback.MusicRequestFill.post(contextId, extra)
        }
    }

    /** Cancela lo que haya en marcha y vuelve al estado inicial. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = MusicRequestUiState.Idle
    }
}

/**
 * La última petición que sonó. [songs] son las que se conocen ahora (el primer lote + lo que ya llegó del relleno);
 * [fillRunning] = el relleno aún puede añadir más, así que guardar ya daría una lista corta; [saved] = ya está en
 * la biblioteca.
 */
data class LastMusicRequest(
    val title: String,
    val songs: List<MediaMetadata>,
    val contextId: String,
    val fillRunning: Boolean,
    val saved: Boolean = false,
    /** El filtro con el que se pidió, y si no pudo cumplirse del todo (la tarjeta lo dice). */
    val focus: MusicRequestFocus = MusicRequestFocus.All,
    val focusRelaxed: Boolean = false,
)

/** Canciones del relleno en segundo plano para sumar a la cola de [contextId]. Ver [MusicRequestViewModel.extend]. */
data class ExtendQueue(val contextId: String, val songs: List<MediaMetadata>)

sealed interface MusicRequestUiState {
    data object Idle : MusicRequestUiState

    /** Buscando. [total] es 0 mientras todavía no se sabe cuántas hay que resolver. */
    data class Working(val done: Int, val total: Int) : MusicRequestUiState

    /**
     * Listo para sonar. El llamante pone la cola con [contextId] y llama a `reset()`; ese mismo id es
     * el que trae [MusicRequestViewModel.extend] cuando llega el relleno en segundo plano, para saber
     * a qué cola sumarlo.
     */
    data class Ready(val title: String, val songs: List<MediaMetadata>, val contextId: String) :
        MusicRequestUiState

    /**
     * No se encontró nada. **Un estado propio y no un `Error`**: aquí no hay nada roto que explicar —
     * ni la IA ni la búsqueda dieron canciones para esa descripción — y decirle "error" por una
     * petición demasiado rara sería culpar a la app de algo que no es un fallo.
     */
    data object Empty : MusicRequestUiState
}
