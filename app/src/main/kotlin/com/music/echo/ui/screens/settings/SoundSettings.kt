package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
// Dead DSP preference-key imports removed (IA cleanup): AudioEnhanceEnabledKey,
// AudioNormalizationKey and AuraSignatureToneEnabledKey were imported but never rendered here
// (their toggles/setters are not wired). Only SafeVolumeEnabledKey is used.
import iad1tya.echo.music.constants.SafeVolumeEnabledKey
import iad1tya.echo.music.constants.FidelityModeKey
import iad1tya.echo.music.ui.component.FidelityPausedNote
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog

/**
 * Dedicated, ordered "Sonido" hub: equalizer, Auto-EQ, all DSP effects/plugins and loudness in one
 * place — extracted from PlayerSettings (which now keeps only playback).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {

    fun thumb(checked: Boolean): @Composable () -> Unit = {
        Icon(
            painter = painterResource(id = if (checked) R.drawable.check else R.drawable.close),
            contentDescription = null,
            modifier = Modifier.size(SwitchDefaults.IconSize),
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Row 902 — «Modo Fidelidad». Read once here: every control it pauses says «En pausa» under itself.
        val (fidelityMode, onFidelityModeChange) = rememberPreference(FidelityModeKey, defaultValue = false)

        // ── Ecualizador (Auto-EQ lives inside AxionEqScreen) ──
        Material3SettingsGroup(
            title = "Ecualizador",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.echoequlizer),
                    title = { Text(stringResource(R.string.echo_equalizer)) },
                    description = {
                        Column {
                            Text(stringResource(R.string.echo_equalizer_desc))
                            FidelityPausedNote(fidelityMode)
                        }
                    },
                    onClick = { ActionLog.tap(ActionId.SETSOUND_EQUALIZER); navController.navigate("settings/equalizer") },
                ),
            ),
        )

        Spacer(Modifier.height(20.dp))

        // Fila 358 (dueño 2026-10-08: "quiero que la parte de sonido y ecualizador estén bien distribuidas en
        // un orden correcto" + "elimina las funciones repetidas"). Order: the equalizer, then what protects the
        // ears and the speaker, then the sound's space and dynamics, then the output quality (the technical
        // part last). "Simulador Tidal" left this screen: it was the same switch as "Firma de sonido Tidal"
        // inside the equalizer, which it depends on (EqBypass.toneStages) — it lives there now, once.
        val (safeVolume, onSafeVolumeChange) = rememberPreference(SafeVolumeEnabledKey, defaultValue = true)
        // ── Masterización (owner directive 2026-09-13): every stage is a user toggle ──
        // Every toggle defaulted ON (owner directive 2026-09-14: "todos los toggles activados por default").
        // Exception, like row 325: the glue compressor defaults OFF (owner order 2026-11-23, row 712). Same
        // constant MusicService reads, so the switch never shows a state the DSP is not in
        // (GlueCompressorDefaultParityTest).
        val (glueCompressor, onGlueCompressorChange) = rememberPreference(
            iad1tya.echo.music.constants.GlueCompressorEnabledKey,
            defaultValue = iad1tya.echo.music.constants.GlueCompressorDefault,
        )
        val (outputDither, onOutputDitherChange) = rememberPreference(
            iad1tya.echo.music.constants.OutputDitherEnabledKey, defaultValue = true
        )
        // PLAN A3 — opt-in, default OFF. The default is the SAME constant MusicService reads (the switch must
        // never show a state the audio sink is not in); Float32ProcessingDefaultParityTest pins it.
        val (float32Processing, onFloat32ProcessingChange) = rememberPreference(
            iad1tya.echo.music.constants.Float32ProcessingEnabledKey,
            defaultValue = iad1tya.echo.music.constants.Float32ProcessingDefault,
        )
        // Exception to "todos los toggles activados por default": owner order 2026-10-06 turns this one OFF
        // by default (must match MusicService's two reads of the same key, or the switch would lie).
        val (speakerBassProtect, onSpeakerBassProtectChange) = rememberPreference(
            iad1tya.echo.music.constants.SpeakerBassProtectEnabledKey, defaultValue = false
        )
        // Exception to the blanket "todos los toggles activados por default" (2026-09-14): the owner
        // reported (2026-09-22) that moving an EQ band perceptibly changes the overall volume — this
        // toggle trimming the preamp by the loudest band's boost on every recompute is exactly that
        // mechanism. Defaults OFF now; still a real, discoverable switch for anyone who wants the
        // clipping protection back.
        val (autoHeadroom, onAutoHeadroomChange) = rememberPreference(
            iad1tya.echo.music.constants.AutoHeadroomEnabledKey, defaultValue = false
        )
        val (stereoWidth, onStereoWidthChange) = rememberPreference(
            iad1tya.echo.music.constants.StereoWidthKey, defaultValue = 1f
        )
        Material3SettingsGroup(
            title = "Volumen y protección",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_up),
                    title = { Text(stringResource(R.string.sound_safe_volume_title)) },
                    description = {
                        Column {
                            Text(stringResource(R.string.sound_safe_volume_desc))
                            FidelityPausedNote(fidelityMode)
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = safeVolume,
                            onCheckedChange = onSafeVolumeChange,
                            thumbContent = thumb(safeVolume),
                        )
                    },
                    onClick = { onSafeVolumeChange(!safeVolume) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_up),
                    title = { Text("Headroom automático") },
                    description = {
                        Column {
                            Text("Baja el preamp tanto como el mayor realce de tu ecualización: los realces nunca saturan.")
                            FidelityPausedNote(fidelityMode)
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = autoHeadroom,
                            onCheckedChange = onAutoHeadroomChange,
                            thumbContent = thumb(autoHeadroom),
                        )
                    },
                    onClick = { onAutoHeadroomChange(!autoHeadroom) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_up),
                    title = { Text("Proteger el altavoz del teléfono") },
                    description = {
                        Text("Atenúa el sub-grave (por debajo de ≈55 Hz) solo cuando suena por el altavoz del teléfono. Con audífonos o Bluetooth no cambia nada.")
                    },
                    trailingContent = {
                        Switch(
                            checked = speakerBassProtect,
                            onCheckedChange = onSpeakerBassProtectChange,
                            thumbContent = thumb(speakerBassProtect),
                        )
                    },
                    onClick = { onSpeakerBassProtectChange(!speakerBassProtect) },
                ),
            ),
        )

        Spacer(Modifier.height(20.dp))

        Material3SettingsGroup(
            title = "Espacio y dinámica",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text("Ancho estéreo: ${(stereoWidth * 100).toInt()} %") },
                    description = {
                        Column {
                            Text("Mid/Side: abre o cierra los lados sin tocar el centro (voz y bombo). 100 % = sin cambio.")
                            FidelityPausedNote(fidelityMode)
                            Slider(
                                value = stereoWidth,
                                onValueChange = { onStereoWidthChange((it * 20).toInt() / 20f) },
                                valueRange = 0.5f..1.5f,
                            )
                        }
                    },
                    onClick = { ActionLog.tap(ActionId.SETSOUND_STEREO_WIDTH_RESET); onStereoWidthChange(1f) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_up),
                    title = { Text(stringResource(R.string.sound_glue_compressor_title)) },
                    description = {
                        Column {
                            Text(stringResource(R.string.sound_glue_compressor_desc))
                            FidelityPausedNote(fidelityMode)
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = glueCompressor,
                            onCheckedChange = onGlueCompressorChange,
                            thumbContent = thumb(glueCompressor),
                        )
                    },
                    onClick = { onGlueCompressorChange(!glueCompressor) },
                ),
            ),
        )

        Spacer(Modifier.height(20.dp))

        Material3SettingsGroup(
            title = "Calidad de salida",
            items = listOf(
                // Row 902 (owner decision H17): off by default; what it turns off is listed in its description.
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.fidelity_mode_title)) },
                    description = { Text(stringResource(R.string.fidelity_mode_desc)) },
                    trailingContent = {
                        Switch(
                            checked = fidelityMode,
                            onCheckedChange = onFidelityModeChange,
                            thumbContent = thumb(fidelityMode),
                        )
                    },
                    onClick = { onFidelityModeChange(!fidelityMode) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text("Procesado en 32 bits (experimental)") },
                    description = {
                        Column {
                            Text(
                                "El ecualizador y la salida trabajan en coma flotante de 32 bits también con audio de " +
                                    "16 bits (Opus, AAC, MP3), como ya pasa con el Hi-Res. Se aplica desde la siguiente " +
                                    "canción. Vuelve a 16 bits si cambias tempo o tono y en Escuchar juntos. Sin " +
                                    "efecto en equipos de gama baja. Solo se nota con una salida de más de 16 bits " +
                                    "(DAC USB, LDAC de 24 bits)."
                            )
                            FidelityPausedNote(fidelityMode)
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = float32Processing,
                            onCheckedChange = onFloat32ProcessingChange,
                            thumbContent = thumb(float32Processing),
                        )
                    },
                    onClick = { onFloat32ProcessingChange(!float32Processing) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text("Dither de salida") },
                    description = {
                        // Stays visible and live in both states: with 32-bit processing ON it still acts
                        // whenever the output falls back to 16 bits (tempo/pitch, Listen Together, low-end).
                        Column {
                            Text(
                                if (float32Processing) {
                                    "Dither triangular con modelado de ruido al convertir a 16 bits. Con «Procesado en 32 bits» " +
                                        "activado solo actúa cuando la salida vuelve a 16 bits: si cambias tempo o tono, en " +
                                        "Escuchar juntos o en equipos de gama baja."
                                } else {
                                    "Dither triangular con modelado de ruido al convertir a 16 bits: pasajes suaves y colas de " +
                                        "reverb más limpios. Actúa cuando la salida es de 16 bits (lo normal con Opus, AAC y " +
                                        "MP3); cuando sale en 32 bits, como el Hi-Res en equipos de gama media y alta, no hace falta."
                                }
                            )
                            FidelityPausedNote(fidelityMode)
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = outputDither,
                            onCheckedChange = onOutputDitherChange,
                            thumbContent = thumb(outputDither),
                        )
                    },
                    onClick = { onOutputDitherChange(!outputDither) },
                ),
            ),
        )

        Spacer(Modifier.height(27.dp))
    }
}
