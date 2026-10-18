package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AiProviderKey
import iad1tya.echo.music.constants.OpenRouterApiKey
import iad1tya.echo.music.constants.OpenRouterBaseUrlKey
import iad1tya.echo.music.constants.OpenRouterModelKey
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playlistimport.MusicRequestFocus
import iad1tya.echo.music.playlistimport.MusicRequestHistory
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.LastMusicRequest
import iad1tya.echo.music.viewmodels.MusicRequestUiState
import iad1tya.echo.music.viewmodels.MusicRequestViewModel

/**
 * "Pedir música" en INICIO, con la forma que tiene en YouTube Music.
 *
 * 🔴 Dueño, 2026-09-17: *"el apartado de pedir música lo veo muy básico y no funciona bien, y el
 * apartado de pedir música lo quiero en el apartado de inicio, así como YouTube Music, y una
 * apariencia a ese estilo también"*.
 *
 * La primera versión estaba en Buscar y era un `OutlinedTextField` con un botón de play. Tenía razón
 * en las dos cosas: el sitio y la pinta. YouTube Music no pone un campo de texto en su inicio — pone
 * una **tarjeta** que invita, y el campo aparece después, en una hoja, con **sugerencias** que quitan
 * el "¿y ahora qué escribo?". Sin esas sugerencias, un campo vacío en una pantalla de música es justo
 * lo que se siente básico.
 *
 * Son dos piezas a propósito:
 *  · [AuraMusicRequestCard] — lo que se ve en Inicio. Una tarjeta ancha, con la chispa y dos líneas.
 *  · la hoja — el campo, las sugerencias y el progreso, que solo existe mientras se pide.
 */
@Composable
fun AuraMusicRequestCard(
    modifier: Modifier = Modifier,
    viewModel: MusicRequestViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val state by viewModel.state.collectAsState()
    val last by viewModel.last.collectAsState()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    // EL RELEVO. En un `LaunchedEffect` y NO en el cuerpo del composable: poner una cola es un efecto,
    // y hacerlo durante la composición se volvería a ejecutar en cada recomposición antes de que
    // `reset()` cambie el estado — sonaría la misma cola dos o tres veces.
    val ready = state as? MusicRequestUiState.Ready
    LaunchedEffect(ready) {
        val result = ready ?: return@LaunchedEffect
        playerConnection.playQueue(
            ListQueue(
                title = result.title,
                items = result.songs.map { it.toMediaItem() },
                contextId = result.contextId,
            ),
        )
        // La hoja NO se cierra: empieza a sonar ya (como siempre) y la hoja pasa a mostrar el resultado con sus
        // primeras canciones y «Guardar como lista» (YouTube Music hace lo mismo con su estación). Se cierra al
        // tocar fuera o con atrás.
        viewModel.reset()
    }

    // EL RELLENO EN SEGUNDO PLANO llega por su propio canal (no por `state`, que ya volvió a Idle
    // arriba) — ver MusicRequestViewModel.fillInBackground. Vive fuera de `sheetOpen`/`ready` a
    // propósito: la hoja ya se cerró para cuando esto llega, pero la música sigue sonando y es a esa
    // cola a la que hay que sumarle el resto.
    // Fila 361: the fill now goes straight to the service (MusicRequestFill), so it also arrives when
    // Inicio is no longer on screen — nothing to collect here any more.

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 72.dp)
            .clip(AuraShapes.Card)
            // Un degradado sutil en vez de un relleno plano: es lo que separa una tarjeta que INVITA
            // de una fila más de la lista. Se queda en los colores del tema (teal → violeta del
            // gradiente del botón de play) para no meter una paleta nueva en Inicio.
            .background(
                Brush.horizontalGradient(
                    listOf(
                        AuraPalette.Teal.copy(alpha = 0.18f),
                        AuraPalette.SurfaceFill,
                    ),
                ),
            )
            .auraClickableInternal(
                onClick = { sheetOpen = true },
                contentDescription = stringResource(R.string.music_request_label),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(AuraPalette.Teal.copy(alpha = 0.20f)),
        ) {
            Icon(
                painter = painterResource(R.drawable.auto_awesome),
                contentDescription = null,
                tint = AuraPalette.Teal,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.music_request_label),
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            Text(
                text = stringResource(R.string.music_request_card_subtitle),
                style = AuraType.MiniArtist,
                color = AuraPalette.OnGroundMuted,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
        }
        if (state is MusicRequestUiState.Working) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = AuraPalette.Teal,
            )
        }
    }

    if (sheetOpen) {
        AuraMusicRequestSheet(
            state = state,
            last = last,
            onSave = viewModel::saveLastAsPlaylist,
            onRemoveSong = viewModel::removeFromLast,
            onDismissLast = viewModel::dismissLast,
            onDismiss = {
                // Cerrar NO cancela lo que ya está buscando: si tarda, él puede seguir usando la app y
                // la música entra cuando entre. `reset()` solo si no hay nada en marcha.
                if (state !is MusicRequestUiState.Working) viewModel.reset()
                sheetOpen = false
            },
            onRequest = viewModel::request,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AuraMusicRequestSheet(
    state: MusicRequestUiState,
    last: LastMusicRequest?,
    onSave: () -> Unit,
    onRemoveSong: (String) -> Unit,
    onDismissLast: () -> Unit,
    onDismiss: () -> Unit,
    onRequest: (
        prompt: String, provider: String, apiKey: String, baseUrl: String, model: String, focus: MusicRequestFocus,
    ) -> Unit,
) {
    // THE UNIFIED FLOATING STYLE (owner report 2026-09-22: "pedir música" needs the same translucent
    // glass the other bottom sheets get). Same routing AddMusicSheet / the 3-dot menu / the search
    // sheet use: premium skin + a live overlay source → the in-window glass host (glass sampling
    // whatever is actually behind it, dispatcher-priority back, top-center handle, imePadding);
    // otherwise the classic window below, byte-identical to before.
    val overlayHazeState = LocalOverlayHazeState.current
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    if (premium && overlayHazeState != null) {
        AuraInWindowDialog(
            visible = true,
            onDismiss = onDismiss,
            center = false,
            // Measures its own content instead of forcing 85% of the screen — same as every other
            // bottom sheet since RELEASE_INFO's "las ventanas que suben desde abajo miden lo que
            // mide su contenido".
            fullHeight = false,
        ) {
            AuraMusicRequestSheetBody(state = state, last = last, onSave = onSave, onRemoveSong = onRemoveSong, onDismissLast = onDismissLast, onRequest = onRequest)
        }
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AuraShapes.Sheet,
        containerColor = AuraPalette.FloatingFill,
        contentColor = AuraPalette.OnGround,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AuraPalette.OnGround.copy(alpha = 0.28f)),
            )
        },
    ) {
        AuraMusicRequestSheetBody(state = state, last = last, onSave = onSave, onRemoveSong = onRemoveSong, onDismissLast = onDismissLast, onRequest = onRequest)
    }
}

@Composable
private fun AuraMusicRequestSheetBody(
    state: MusicRequestUiState,
    last: LastMusicRequest?,
    onSave: () -> Unit,
    onRemoveSong: (String) -> Unit,
    onDismissLast: () -> Unit,
    onRequest: (
        prompt: String, provider: String, apiKey: String, baseUrl: String, model: String, focus: MusicRequestFocus,
    ) -> Unit,
) {
    val provider by rememberPreference(AiProviderKey, "OpenRouter")
    val apiKey by rememberPreference(OpenRouterApiKey, "")
    val baseUrl by rememberPreference(
        OpenRouterBaseUrlKey,
        "https://openrouter.ai/api/v1/chat/completions",
    )
    val model by rememberPreference(OpenRouterModelKey, "google/gemini-2.5-flash-lite")

    var prompt by rememberSaveable { mutableStateOf("") }
    // «Todo / Conocido / Descubrir» — se recuerda mientras la hoja vive.
    var mode by rememberSaveable { mutableStateOf(MusicRequestFocus.All) }
    val working = state is MusicRequestUiState.Working
    val focus = remember { FocusRequester() }

    // El teclado listo al abrir: la hoja existe para escribir, y obligar a un toque más en el campo
    // es exactamente la fricción que hace que una función así no se use.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Con un resultado nuevo en pantalla el teclado sobra: baja para que se vea la tarjeta.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(last?.contextId) { if (last != null) focusManager.clearFocus() }

    val send: (String) -> Unit = { text ->
        if (text.isNotBlank() && !working) {
            onRequest(text.trim(), provider, apiKey, baseUrl, model, mode)
        }
    }

    // Voz (owner report 2026-09-22): mismo mecanismo que la búsqueda normal — al terminar de hablar,
    // busca sola, sin un toque extra.
    val voice = rememberAuraVoiceSearch(
        onPartial = { spoken -> prompt = spoken },
        onResult = { spoken ->
            prompt = spoken
            send(spoken)
        },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.auto_awesome),
                contentDescription = null,
                tint = AuraPalette.Teal,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.music_request_label),
                style = AuraType.SheetTitle,
                color = AuraPalette.OnGround,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                enabled = !working,
                singleLine = false,
                maxLines = 3,
                placeholder = {
                    Text(
                        text = stringResource(R.string.music_request_hint),
                        color = AuraPalette.OnGroundFaint,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { send(prompt) }),
                shape = AuraShapes.Card,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AuraPalette.SurfaceFill,
                    unfocusedContainerColor = AuraPalette.SurfaceFill,
                    disabledContainerColor = AuraPalette.SurfaceFill,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = AuraPalette.OnGround,
                    unfocusedTextColor = AuraPalette.OnGround,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
            AuraIconButton(
                icon = AuraIcons.Mic,
                contentDescription = stringResource(R.string.voice_search),
                onClick = voice.launch,
                size = 20.dp,
                tint = if (voice.active) AuraPalette.Teal else AuraPalette.OnGroundFaint,
            )
            // «Empezar»: lanza la petición sin tocar el Enter del teclado (dueño, 2026-10-18). Apagado hasta que
            // hay texto; mientras busca muestra el giro en vez del botón, para no pedir dos veces.
            val canSend = prompt.isNotBlank() && !working
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (canSend) AuraPalette.Teal else AuraPalette.SurfaceFill)
                    .auraClickableInternal(
                        onClick = { send(prompt) },
                        enabled = canSend,
                        contentDescription = stringResource(R.string.music_request_send),
                    ),
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = AuraPalette.Teal,
                    )
                } else {
                    AuraIconGlyph(
                        icon = AuraIcons.Play,
                        contentDescription = null,
                        size = 20.dp,
                        tint = if (canSend) AuraPalette.Ground else AuraPalette.OnGroundFaint,
                    )
                }
            }
        }

        // El resultado de la última petición: lo que está sonando, sus tres primeras canciones y guardarlo.
        if (last != null) {
            AuraMusicRequestResultCard(last = last, onSave = onSave, onRemoveSong = onRemoveSong, onDismiss = onDismissLast)
        }

        // «Todo / Conocido / Descubrir» (YouTube Music: Familiar / Discover). Descubrir = nunca reproducidas y de
        // artistas que no conoces; Conocido = de artistas que ya escuchas.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                MusicRequestFocus.All to R.string.music_request_focus_all,
                MusicRequestFocus.Familiar to R.string.music_request_focus_familiar,
                MusicRequestFocus.Discover to R.string.music_request_focus_discover,
            ).forEach { (value, label) ->
                AuraChip(
                    text = stringResource(label),
                    selected = mode == value,
                    onClick = { mode = value },
                )
            }
        }

        // HISTORIAL / "OTRA TANDA" (ronda 6, dueño): repetir la última petición sin reescribirla es
        // "otra tanda" (canciones distintas, gracias al TTL de MusicRequestRecents); repetir una
        // anterior es el historial — el mismo mecanismo cubre las dos.
        //
        // 2026-10-18 (dueño): cada petición ya completada se puede QUITAR por separado (la ✕ de su
        // chip) o todas a la vez ("Borrar todo"). La lista es observable: antes se leía una sola vez al
        // abrir la hoja y una entrada quitada se habría quedado en pantalla.
        val recentPrompts by MusicRequestHistory.prompts.collectAsState()
        if (recentPrompts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.music_request_recent_label),
                        style = AuraType.MiniArtist,
                        color = AuraPalette.OnGroundMuted,
                    )
                    Text(
                        text = stringResource(R.string.music_request_clear_history),
                        style = AuraType.MiniArtist,
                        color = AuraPalette.Teal,
                        modifier = Modifier
                            .clip(AuraShapes.Pill)
                            .auraClickableInternal(
                                onClick = { MusicRequestHistory.clear() },
                                contentDescription = stringResource(R.string.music_request_clear_history),
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    recentPrompts.forEach { text ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            AuraChip(
                                text = "↻ $text",
                                selected = false,
                                onClick = {
                                    prompt = text
                                    send(text)
                                },
                            )
                            // Quita SOLO esta petición: no toca las demás ni lo que ya suena.
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .auraClickableInternal(
                                        onClick = { MusicRequestHistory.remove(text) },
                                        contentDescription = stringResource(R.string.music_request_remove_entry),
                                    ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = null,
                                    tint = AuraPalette.OnGroundFaint,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // LAS SUGERENCIAS, que es lo que de verdad quita el "muy básico": un campo vacío en una
        // pantalla de música no dice qué se le puede pedir. Al tocar una, se pide directamente —
        // no rellena el campo para que él tenga que darle a otra cosa.
        //
        // 2026-10-18: ya no son cinco fijas. YouTube Music personaliza con lo que escuchas y lo que está
        // sonando, y rota sus ejemplos. Aquí: hasta dos PERSONALES primero (el momento del día y «algo
        // parecido a» lo que suena ahora) y luego seis tomadas al azar de un fondo de catorce, distintas
        // cada vez que se abre la hoja. Las cinco de siempre siguen en el fondo.
        val playerConnection = LocalPlayerConnection.current
        val currentArtist = remember {
            playerConnection?.mediaMetadata?.value?.artists?.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        }
        val suggestions = remember(currentArtist) { musicRequestSuggestions(currentArtist) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            suggestions.forEach { suggestion ->
                val text = if (suggestion.arg != null) {
                    stringResource(suggestion.res, suggestion.arg)
                } else {
                    stringResource(suggestion.res)
                }
                // Una sugerencia que ya está en «Pedir de nuevo» no se repite (se veía dos veces la misma chip).
                if (recentPrompts.none { it.equals(text, ignoreCase = true) }) {
                    AuraChip(
                        text = text,
                        selected = false,
                        onClick = {
                            prompt = text
                            send(text)
                        },
                    )
                }
            }
        }

        when (state) {
            is MusicRequestUiState.Working -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AuraPalette.Teal,
                )
                Text(
                    // Con el total ya conocido se cuenta; mientras no, solo "buscando". Un
                    // "0 de 0" es peor que nada.
                    text = if (state.total > 0) {
                        stringResource(R.string.music_request_working_count, state.done, state.total)
                    } else {
                        stringResource(R.string.music_request_working)
                    },
                    style = AuraType.MiniArtist,
                    color = AuraPalette.OnGroundMuted,
                )
            }

            is MusicRequestUiState.Empty -> Text(
                // "No encontré nada para eso", no "error": no hay nada roto que explicar, y
                // llamarlo error sería culpar a la app de una petición demasiado rara.
                text = stringResource(R.string.music_request_empty),
                style = AuraType.MiniArtist,
                color = AuraPalette.OnGroundMuted,
            )

            else -> Box(Modifier.height(1.dp))
        }
    }
}

/**
 * La tarjeta de resultado de «Pedir música» (el equivalente a la estación de YouTube Music): qué suena, las tres
 * primeras canciones y guardarla como lista. Guardar espera a que termine el relleno en segundo plano para no
 * guardar una lista corta.
 *
 * Dueño, 2026-10-18: *«que el usuario pudiera ver esas 10 más o lo que salga allí, que sea más interactuable»*.
 * «+21 más» ahora despliega TODA la lista (con su propio desplazamiento, para no estirar la hoja): tocar una
 * canción reproduce desde ahí (misma cola y mismo contexto, así el relleno sigue llegando) y la ✕ deja una
 * canción fuera de la lista que se va a guardar — no toca lo que ya suena.
 */
@Composable
private fun AuraMusicRequestResultCard(
    last: LastMusicRequest,
    onSave: () -> Unit,
    onRemoveSong: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.music_request_playing_title, last.title),
                    style = AuraType.RowTitle,
                    color = AuraPalette.OnGround,
                    maxLines = 2,
                    overflow = AuraDefaultOverflow,
                )
                Text(
                    text = pluralStringResource(R.plurals.n_song, last.songs.size, last.songs.size),
                    style = AuraType.MiniArtist,
                    color = AuraPalette.OnGroundMuted,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .auraClickableInternal(
                        onClick = onDismiss,
                        contentDescription = stringResource(R.string.close),
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    tint = AuraPalette.OnGroundFaint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // El filtro no pudo cumplirse del todo: se dice, en vez de mezclar en silencio lo que no encaja.
        if (last.focusRelaxed && last.focus != MusicRequestFocus.All) {
            Text(
                text = stringResource(
                    if (last.focus == MusicRequestFocus.Familiar) R.string.music_request_relaxed_familiar
                    else R.string.music_request_relaxed_discover,
                ),
                style = AuraType.MiniArtist,
                color = AuraPalette.Teal,
            )
        }
        val shown = if (expanded) last.songs else last.songs.take(3)
        // Desplegada, la lista tiene su propio alto máximo y se desplaza por dentro: la hoja no crece sin fin.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expanded) Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                    else Modifier,
                ),
        ) {
            shown.forEachIndexed { index, song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .auraClickableInternal(
                            onClick = {
                                // Desde esta canción, con la misma cola y el mismo contexto de la petición.
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = last.title,
                                        items = last.songs.map { it.toMediaItem() },
                                        startIndex = index,
                                        contextId = last.contextId,
                                    ),
                                )
                            },
                            contentDescription = song.title,
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = "${index + 1}",
                        style = AuraType.MiniArtist,
                        color = AuraPalette.OnGroundFaint,
                        modifier = Modifier.sizeIn(minWidth = 22.dp),
                    )
                    Text(
                        text = "${song.title} · ${song.artists.firstOrNull()?.name.orEmpty()}",
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGround,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                        modifier = Modifier.weight(1f),
                    )
                    if (expanded && !last.saved) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(50))
                                .auraClickableInternal(
                                    onClick = { onRemoveSong(song.id) },
                                    contentDescription = stringResource(R.string.music_request_remove_from_save),
                                ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                                tint = AuraPalette.OnGroundFaint,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        if (last.songs.size > 3) {
            Text(
                text = if (expanded) {
                    stringResource(R.string.music_request_show_less)
                } else {
                    stringResource(R.string.music_request_show_all, last.songs.size)
                },
                style = AuraType.MiniArtist,
                color = AuraPalette.Teal,
                modifier = Modifier
                    .clip(AuraShapes.Pill)
                    .auraClickableInternal(onClick = { expanded = !expanded })
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
        if (expanded && !last.saved) {
            Text(
                text = stringResource(R.string.music_request_edit_hint),
                style = AuraType.MiniArtist,
                color = AuraPalette.OnGroundFaint,
            )
        }
        when {
            last.saved -> Text(
                text = stringResource(R.string.music_request_saved),
                style = AuraType.MiniArtist,
                color = AuraPalette.Teal,
            )
            last.fillRunning -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AuraPalette.Teal,
                )
                Text(
                    text = stringResource(R.string.music_request_filling),
                    style = AuraType.MiniArtist,
                    color = AuraPalette.OnGroundMuted,
                )
            }
            else -> AuraChip(
                text = stringResource(R.string.music_request_save),
                selected = false,
                onClick = onSave,
            )
        }
    }
}

/** Una sugerencia: su texto y, si lo lleva, el dato que se mete en él (el artista que suena). */
private class MusicRequestSuggestion(val res: Int, val arg: String? = null)

/**
 * Las sugerencias de la hoja: son descripciones de MOMENTO ("para estudiar", "para el gimnasio") en vez de
 * géneros, porque es lo que esta función hace mejor que la búsqueda normal — un género ya se busca
 * escribiéndolo.
 *
 * Personales primero (el momento del día y, si suena algo, «algo parecido a <artista>»), y después seis del
 * fondo, al azar. Se calcula UNA vez por apertura de la hoja (`remember`): las chips no cambian de sitio
 * mientras se miran.
 */
private fun musicRequestSuggestions(currentArtist: String?): List<MusicRequestSuggestion> {
    val hour = java.time.LocalTime.now().hour
    val timeOfDay = when (hour) {
        in 5..11 -> R.string.music_request_suggestion_morning
        in 12..18 -> R.string.music_request_suggestion_afternoon
        else -> R.string.music_request_suggestion_night
    }
    val personal = buildList {
        currentArtist?.let { add(MusicRequestSuggestion(R.string.music_request_suggestion_like_artist, it)) }
        add(MusicRequestSuggestion(timeOfDay))
    }
    val pool = listOf(
        R.string.music_request_suggestion_study,
        R.string.music_request_suggestion_gym,
        R.string.music_request_suggestion_sleep,
        R.string.music_request_suggestion_drive,
        R.string.music_request_suggestion_party,
        R.string.music_request_suggestion_focus,
        R.string.music_request_suggestion_rain,
        R.string.music_request_suggestion_sunday,
        R.string.music_request_suggestion_cleaning,
        R.string.music_request_suggestion_throwback,
        R.string.music_request_suggestion_deepcuts,
        R.string.music_request_suggestion_worship,
        R.string.music_request_suggestion_new,
    ).shuffled().take(6).map { MusicRequestSuggestion(it) }
    return personal + pool
}
