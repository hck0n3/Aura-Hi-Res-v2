package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * "Pedir música": escribes lo que te apetece y suena.
 *
 * 🔴 Petición del dueño (2026-09-17): la función que YouTube Music llama "pedir música", *"pero de
 * manera gratuita y funcional"*, y con la respuesta *"que solo entregue el resultado"* — sin decir si
 * la armó la IA o la búsqueda.
 *
 * ## Por qué es una fila y no una pantalla
 * Lo que devuelve esto no es una lista que revisar: es música que empieza a sonar. Una pantalla propia
 * obligaría a un "atrás" después de conseguir exactamente lo que se pidió. Vive donde ya se está
 * buscando algo que poner, y al conseguirlo el reproductor toma el relevo.
 *
 * ## Lo que NO dice
 * Ni una palabra sobre el origen. El `MusicRequestUiState` no tiene un campo para eso — no es que se
 * omita al dibujar, es que **no llega hasta aquí**. Esa decisión, y por qué es la contraria a la del
 * diálogo de playlists con IA, está razonada en [MusicRequestViewModel].
 */
@Composable
fun AuraMusicRequestRow(
    modifier: Modifier = Modifier,
    onStartedPlaying: () -> Unit = {},
    viewModel: MusicRequestViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val state by viewModel.state.collectAsState()

    val provider by rememberPreference(AiProviderKey, "OpenRouter")
    val apiKey by rememberPreference(OpenRouterApiKey, "")
    val baseUrl by rememberPreference(
        OpenRouterBaseUrlKey,
        "https://openrouter.ai/api/v1/chat/completions",
    )
    val model by rememberPreference(OpenRouterModelKey, "google/gemini-2.5-flash-lite")

    var prompt by rememberSaveable { mutableStateOf("") }
    val working = state is MusicRequestUiState.Working

    // El relevo: en cuanto hay canciones, se pone la cola y el estado vuelve a Idle. Se hace aquí y no
    // en el ViewModel porque poner una cola es del reproductor, y un ViewModel que lo hiciera tendría
    // que conocer la conexión al servicio — que es justo lo que lo ata a una pantalla.
    // En un `LaunchedEffect` y NO en el cuerpo del composable: poner una cola es un efecto, y hacerlo
    // durante la composición se ejecutaría otra vez en cada recomposición antes de que `reset()`
    // llegue a cambiar el estado — sonaría la misma cola dos o tres veces.
    val ready = state as? MusicRequestUiState.Ready
    androidx.compose.runtime.LaunchedEffect(ready) {
        val result = ready ?: return@LaunchedEffect
        playerConnection.playQueue(
            ListQueue(
                title = result.title,
                items = result.songs.map { it.toMediaItem() },
            ),
        )
        viewModel.reset()
        prompt = ""
        onStartedPlaying()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            singleLine = true,
            enabled = !working,
            label = { Text(stringResource(R.string.music_request_label)) },
            placeholder = { Text(stringResource(R.string.music_request_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = { viewModel.request(prompt, provider, apiKey, baseUrl, model) },
            ),
            trailingIcon = {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = AuraPalette.Teal,
                    )
                } else {
                    AuraIconButton(
                        icon = AuraIcons.Play,
                        contentDescription = stringResource(R.string.music_request_label),
                        onClick = { viewModel.request(prompt, provider, apiKey, baseUrl, model) },
                        size = 20.dp,
                        enabled = prompt.isNotBlank(),
                        tint = AuraPalette.Teal,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state is MusicRequestUiState.Empty) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                // "No encontré nada para eso", no "error": no hay nada roto que explicar, y llamarlo
                // error sería culpar a la app de una petición demasiado rara. Ver [MusicRequestUiState].
                Text(
                    text = stringResource(R.string.music_request_empty),
                    style = AuraType.MiniArtist,
                    color = AuraPalette.OnGroundMuted,
                )
            }
        }
    }
}
