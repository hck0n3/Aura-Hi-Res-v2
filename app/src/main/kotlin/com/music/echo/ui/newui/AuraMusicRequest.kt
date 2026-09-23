package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.painterResource
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
import iad1tya.echo.music.utils.rememberPreference
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
            ),
        )
        viewModel.reset()
        sheetOpen = false
    }

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
    onDismiss: () -> Unit,
    onRequest: (prompt: String, provider: String, apiKey: String, baseUrl: String, model: String) -> Unit,
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
            AuraMusicRequestSheetBody(state = state, onRequest = onRequest)
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
        AuraMusicRequestSheetBody(state = state, onRequest = onRequest)
    }
}

@Composable
private fun AuraMusicRequestSheetBody(
    state: MusicRequestUiState,
    onRequest: (prompt: String, provider: String, apiKey: String, baseUrl: String, model: String) -> Unit,
) {
    val provider by rememberPreference(AiProviderKey, "OpenRouter")
    val apiKey by rememberPreference(OpenRouterApiKey, "")
    val baseUrl by rememberPreference(
        OpenRouterBaseUrlKey,
        "https://openrouter.ai/api/v1/chat/completions",
    )
    val model by rememberPreference(OpenRouterModelKey, "google/gemini-2.5-flash-lite")

    var prompt by rememberSaveable { mutableStateOf("") }
    val working = state is MusicRequestUiState.Working
    val focus = remember { FocusRequester() }

    // El teclado listo al abrir: la hoja existe para escribir, y obligar a un toque más en el campo
    // es exactamente la fricción que hace que una función así no se use.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val send: (String) -> Unit = { text ->
        if (text.isNotBlank() && !working) {
            onRequest(text.trim(), provider, apiKey, baseUrl, model)
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
        }

        // LAS SUGERENCIAS, que es lo que de verdad quita el "muy básico": un campo vacío en una
        // pantalla de música no dice qué se le puede pedir. Al tocar una, se pide directamente —
        // no rellena el campo para que él tenga que darle a otra cosa.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            musicRequestSuggestions().forEach { suggestionRes ->
                val text = stringResource(suggestionRes)
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
 * Las sugerencias de la hoja.
 *
 * Cinco y no quince: son para arrancar, no un catálogo. Y son descripciones de MOMENTO ("para
 * estudiar", "para el gimnasio") en vez de géneros, porque es lo que esta función hace mejor que la
 * búsqueda normal — un género ya se busca escribiéndolo.
 */
private fun musicRequestSuggestions(): List<Int> = listOf(
    R.string.music_request_suggestion_study,
    R.string.music_request_suggestion_gym,
    R.string.music_request_suggestion_sleep,
    R.string.music_request_suggestion_drive,
    R.string.music_request_suggestion_party,
)
