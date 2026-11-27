

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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AudioOffload
import iad1tya.echo.music.constants.AudioQuality
import iad1tya.echo.music.constants.AudioQualityKey
import iad1tya.echo.music.constants.DownloadQuality
import iad1tya.echo.music.constants.DownloadQualityKey
import iad1tya.echo.music.constants.CrossfadeCurveKey
import iad1tya.echo.music.constants.CrossfadeDurationKey
import iad1tya.echo.music.constants.CrossfadeEnabledKey
import iad1tya.echo.music.constants.CrossfadeGaplessKey
import iad1tya.echo.music.constants.AutoSkipNextOnErrorKey
import iad1tya.echo.music.constants.DisableLoadMoreWhenRepeatAllKey
import iad1tya.echo.music.constants.EnableGoogleCastKey
import iad1tya.echo.music.constants.HistoryDuration
import iad1tya.echo.music.constants.KeepScreenOn
import iad1tya.echo.music.constants.PauseOnMute
import iad1tya.echo.music.constants.PersistentQueueKey
import iad1tya.echo.music.constants.PersistentShuffleAcrossQueuesKey
import iad1tya.echo.music.constants.EnhancedShuffleKey
import iad1tya.echo.music.constants.PreviousQueueOfferKey
import iad1tya.echo.music.constants.PreventDuplicateTracksInQueueKey
import iad1tya.echo.music.constants.RememberShuffleAndRepeatKey
import iad1tya.echo.music.constants.ResumeOnBluetoothConnectKey
import iad1tya.echo.music.constants.ShufflePlaylistFirstKey
import iad1tya.echo.music.constants.SimilarContent
import iad1tya.echo.music.constants.ShowAudioFallbackToastKey
import iad1tya.echo.music.constants.SkipSilenceInstantKey
import iad1tya.echo.music.constants.SkipSilenceKey
import iad1tya.echo.music.constants.SponsorBlockEnabledKey
import iad1tya.echo.music.constants.StopMusicOnTaskClearKey

import iad1tya.echo.music.constants.PreloadNextSongEnabledKey
import iad1tya.echo.music.constants.PreloadNextSongLimitKey
import iad1tya.echo.music.constants.PreloadLyricsEnabledKey

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.EnumDialog
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import kotlin.math.roundToInt
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.OPUS
    )
    val (downloadQuality, onDownloadQualityChange) = rememberEnumPreference(
        DownloadQualityKey,
        defaultValue = DownloadQuality.YOUTUBE,
    )

    // Streaming/download Saavn/Qobuz pickers removed — lock legacy prefs back to YouTube/Opus.
    LaunchedEffect(audioQuality, downloadQuality) {
        if (audioQuality != AudioQuality.OPUS) onAudioQualityChange(AudioQuality.OPUS)
        if (downloadQuality != DownloadQuality.YOUTUBE) onDownloadQualityChange(DownloadQuality.YOUTUBE)
    }

    val (showAudioFallbackToast, onShowAudioFallbackToastChange) = rememberPreference(
        ShowAudioFallbackToastKey,
        defaultValue = true
    )
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false
    )
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = iad1tya.echo.music.constants.CrossfadeDefaultSeconds
    )
    val (crossfadeCurve, onCrossfadeCurveChange) = rememberPreference(
        CrossfadeCurveKey,
        defaultValue = 4
    )
    val (crossfadeGapless, onCrossfadeGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = false
    )
    // Row 902: «Modo Fidelidad» (Ajustes ▸ Sonido) pauses the crossfade and the soft start; both rows say so.
    val fidelityMode = iad1tya.echo.music.ui.component.rememberFidelityModeOn()
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(
        PersistentQueueKey,
        defaultValue = true
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false
    )
    val (skipSilenceInstant, onSkipSilenceInstantChange) = rememberPreference(
        SkipSilenceInstantKey,
        defaultValue = false
    )
    // Audio/EQ/DSP settings moved to the dedicated SoundSettings screen ("settings/sound").

    val (audioOffload, onAudioOffloadChange) = rememberPreference(
        key = AudioOffload,
        defaultValue = false
    )

    // AUDIO OFFLOAD BLOCKERS — must mirror the gate in MusicService.onCreate exactly.
    // Offload hands the ENCODED stream to the DSP hardware, so the decoder stops producing PCM and NO
    // AudioProcessor runs. The service therefore vetoes offload while anything that really processes audio
    // is on. Until 0.6.142 this screen only knew about crossfade, because the other veto terms named dead
    // stubs; now that the gate was reduced to live terms, showing only crossfade would make the switch lie:
    // the user turns crossfade off, flips offload on, sees it "on", and the engine still refuses because
    // Safe Volume or the EQ is active. Both extra terms are read here so the reason shown is the real one.
    val (safeVolumeEnabled, _) = rememberPreference(
        key = iad1tya.echo.music.constants.SafeVolumeEnabledKey,
        defaultValue = true
    )
    val offloadGateContext = androidx.compose.ui.platform.LocalContext.current
    val eqProfileRepository = remember(offloadGateContext) {
        iad1tya.echo.music.eq.data.EQProfileRepositoryEntryPoint.get(offloadGateContext)
    }
    val eqActiveProfile by eqProfileRepository.activeProfile.collectAsState()
    val eqUnsavedProfile by eqProfileRepository.unsavedProfile.collectAsState()
    // Same precedence as MusicService and EqualizerService: an unsaved edit wins over the saved profile.
    val eqActive = (eqUnsavedProfile ?: eqActiveProfile) != null

    // Same "actually running" reading as the service gate: High-Performance Mode force-disables crossfade,
    // so under HPM the crossfade key must not block offload here either or the switch would stay greyed out
    // for a reason that no longer applies.
    val (highPerformanceMode, _) = rememberPreference(
        key = iad1tya.echo.music.constants.HighPerformanceModeKey,
        defaultValue = false
    )
    val (spatialEnabled, _) = rememberPreference(
        key = iad1tya.echo.music.constants.SpatialAudioEnabledKey,
        defaultValue = false
    )
    // Row 714: the rest of what the service gate reads, with the service's own defaults. Without them the
    // switch looked available while the engine still refused offload (Tidal signature, mastering, a room).
    val (tidalSignatureEnabled, _) = rememberPreference(
        key = iad1tya.echo.music.constants.TidalSimulationEnabledKey,
        defaultValue = true
    )
    val (glueCompressorEnabled, _) = rememberPreference(
        key = iad1tya.echo.music.constants.GlueCompressorEnabledKey,
        defaultValue = iad1tya.echo.music.constants.GlueCompressorDefault,
    )
    val (speakerBassProtectEnabled, _) = rememberPreference(
        key = iad1tya.echo.music.constants.SpeakerBassProtectEnabledKey,
        defaultValue = false
    )
    val (stereoWidth, _) = rememberPreference(
        key = iad1tya.echo.music.constants.StereoWidthKey,
        defaultValue = 1f
    )
    // Row 920: the psychoacoustic bass vetoes offload like the other mastering stages (same terms as the service).
    val (virtualBassEnabled, _) = rememberPreference(
        key = iad1tya.echo.music.constants.VirtualBassEnabledKey,
        defaultValue = iad1tya.echo.music.constants.VirtualBassDefault,
    )
    val listenTogetherRoom = LocalListenTogetherManager.current?.roomState?.collectAsState()?.value
    val inListenTogetherRoom = listenTogetherRoom?.inRoom == true

    // The SAME decision as the service (AudioOffloadGate), not a copy of it: a duplicated `when` had already
    // drifted (it knew nothing of Tidal, mastering or Listen Together).
    val offloadBlock = iad1tya.echo.music.playback.audio.AudioOffloadGate.blockReason(
        iad1tya.echo.music.playback.audio.AudioOffloadGate.Inputs(
            userWantsOffload = audioOffload,
            crossfadeEnabled = crossfadeEnabled,
            highPerformanceMode = highPerformanceMode,
            safeVolumeEnabled = safeVolumeEnabled,
            equalizerActive = eqActive,
            spatialEnabled = iad1tya.echo.music.playback.audio.AudioOffloadGate.eqToneStagesLive(
                eqActive, spatialEnabled, tidalSignatureEnabled,
            ),
            listenTogetherActive = inListenTogetherRoom,
            masteringActive = glueCompressorEnabled || speakerBassProtectEnabled || stereoWidth != 1f || virtualBassEnabled,
        ),
    )
    val offloadBlockedReason: String? = when (offloadBlock) {
        iad1tya.echo.music.playback.audio.AudioOffloadGate.BlockReason.CROSSFADE ->
            stringResource(R.string.audio_offload_disabled_by_crossfade)
        iad1tya.echo.music.playback.audio.AudioOffloadGate.BlockReason.SAFE_VOLUME ->
            stringResource(R.string.audio_offload_disabled_by_safe_volume)
        iad1tya.echo.music.playback.audio.AudioOffloadGate.BlockReason.EQUALIZER ->
            stringResource(R.string.audio_offload_disabled_by_equalizer)
        iad1tya.echo.music.playback.audio.AudioOffloadGate.BlockReason.SPATIAL ->
            stringResource(R.string.audio_offload_disabled_by_spatial)
        iad1tya.echo.music.playback.audio.AudioOffloadGate.BlockReason.LISTEN_TOGETHER ->
            stringResource(R.string.audio_offload_disabled_by_listen_together)
        iad1tya.echo.music.playback.audio.AudioOffloadGate.BlockReason.MASTERING ->
            stringResource(R.string.audio_offload_disabled_by_mastering)
        null -> null
    }


    val (preloadNextSongEnabled, onPreloadNextSongEnabledChange) = rememberPreference(
        key = PreloadNextSongEnabledKey,
        defaultValue = true
    )

    // Must match the engine default (MusicService reads PreloadNextSongLimitKey with default 2).
    val (preloadNextSongLimit, onPreloadNextSongLimitChange) = rememberPreference(
        key = PreloadNextSongLimitKey,
        defaultValue = 2
    )

    val (preloadLyricsEnabled, onPreloadLyricsEnabledChange) = rememberPreference(
        key = PreloadLyricsEnabledKey,
        defaultValue = true
    )

    val (dataSaverEnabled, onDataSaverEnabledChange) = rememberPreference(
        key = iad1tya.echo.music.constants.DataSaverEnabledKey,
        defaultValue = false
    )
    // Registry 811 (owner H6b): streaming quality per network. Default HIGH on both = what every stream got before.
    val (wifiStreamQuality, onWifiStreamQualityChange) = rememberEnumPreference(
        iad1tya.echo.music.playback.NetworkStreamQuality.WifiKey,
        defaultValue = iad1tya.echo.music.playback.NetworkStreamQuality.Level.HIGH,
    )
    val (mobileStreamQuality, onMobileStreamQualityChange) = rememberEnumPreference(
        iad1tya.echo.music.playback.NetworkStreamQuality.MobileKey,
        defaultValue = iad1tya.echo.music.playback.NetworkStreamQuality.Level.HIGH,
    )
    var streamQualityDialogFor by remember { mutableStateOf<String?>(null) }
    val streamQualityHigh = stringResource(R.string.stream_quality_high)
    val streamQualitySaver = stringResource(R.string.stream_quality_saver)
    val streamQualityForced = stringResource(R.string.stream_quality_forced_by_data_saver)
    val streamQualityName: (iad1tya.echo.music.playback.NetworkStreamQuality.Level) -> String = {
        if (it == iad1tya.echo.music.playback.NetworkStreamQuality.Level.SAVER) streamQualitySaver else streamQualityHigh
    }

    val (fadeOnManualChange, onFadeOnManualChangeChange) = rememberPreference(
        key = iad1tya.echo.music.constants.FadeOnManualChangeKey,
        defaultValue = true
    )

    val (sponsorBlockEnabled, onSponsorBlockEnabledChange) = rememberPreference(
        key = SponsorBlockEnabledKey,
        defaultValue = true
    )

    val context = androidx.compose.ui.platform.LocalContext.current

    val (enableGoogleCast, onEnableGoogleCastChange) = rememberPreference(
        key = EnableGoogleCastKey,
        defaultValue = true
    )

    val (disableLoadMoreWhenRepeatAll, onDisableLoadMoreWhenRepeatAllChange) = rememberPreference(
        DisableLoadMoreWhenRepeatAllKey,
        defaultValue = false
    )
    val (similarContentEnabled, similarContentEnabledChange) = rememberPreference(
        key = SimilarContent,
        defaultValue = true
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false
    )
    val (persistentShuffleAcrossQueues, onPersistentShuffleAcrossQueuesChange) = rememberPreference(
        PersistentShuffleAcrossQueuesKey,
        defaultValue = false
    )
    val (enhancedShuffle, onEnhancedShuffleChange) = rememberPreference(
        EnhancedShuffleKey,
        defaultValue = true
    )
    val (rememberShuffleAndRepeat, onRememberShuffleAndRepeatChange) = rememberPreference(
        RememberShuffleAndRepeatKey,
        defaultValue = true
    )
    val (shufflePlaylistFirst, onShufflePlaylistFirstChange) = rememberPreference(
        ShufflePlaylistFirstKey,
        defaultValue = false
    )
    val (preventDuplicateTracksInQueue, onPreventDuplicateTracksInQueueChange) = rememberPreference(
        PreventDuplicateTracksInQueueKey,
        defaultValue = true
    )
    // Must match MusicService.previousQueueOfferEnabledHint's default (ON) or the switch would lie.
    val (previousQueueOffer, onPreviousQueueOfferChange) = rememberPreference(
        PreviousQueueOfferKey,
        defaultValue = true
    )
    // Must match the ONLY consumer (MainActivity.onDestroy reads this key with default false); showing
    // true here made the switch lie about the behavior users actually get.
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = false
    )
    val (pauseOnMute, onPauseOnMuteChange) = rememberPreference(
        PauseOnMute,
        defaultValue = false
    )
    val (resumeOnBluetoothConnect, onResumeOnBluetoothConnectChange) = rememberPreference(
        ResumeOnBluetoothConnectKey,
        defaultValue = false
    )
    val (keepScreenOn, onKeepScreenOnChange) = rememberPreference(
        KeepScreenOn,
        defaultValue = false
    )
    // Value is in SECONDS and must match the engine default (MusicService: HistoryDuration * 1000f,
    // falling back to 30000f = 30 s).
    val (historyDuration, onHistoryDurationChange) = rememberPreference(
        HistoryDuration,
        defaultValue = 30f
    )
    // What the slider and its label both show. A value stored by the OLD 1..100 range (e.g. 1) sits
    // outside the current 5..120 range, so it is clamped for display AND written back once — otherwise
    // the thumb, the number and the engine would each report something different.
    val historyDurationShown = historyDuration.coerceIn(5f, 120f)
    LaunchedEffect(historyDuration) {
        if (historyDuration != historyDurationShown) onHistoryDurationChange(historyDurationShown)
    }

    var showCrossfadeCurveDialog by remember {
        mutableStateOf(false)
    }

    val crossfadeCurveName: (Int) -> String = {
        when (it) {
            // Labels must match the actual gain math in MusicService.crossfadeGains: 0 = linear (p,1-p),
            // 1 = equal-power (sin/cos). They were previously swapped, so "Suave" played linear math.
            1 -> "Suave (igual potencia)"
            2 -> "Suave larga (curva S)"
            3 -> "Exponencial (rápida)"
            // Registry 812 (owner H17, 2026-11-24): the shape is CrossfadeMath curve 4 — both songs move together,
            // one lowering while the other rises, equal power, done by 85% of the window. The old label described an
            // earlier asymmetric tuning. Only the label changed: the stored value is still 4.
            4 -> "Cruce natural (una baja mientras la otra sube)"
            5 -> "V (sale, luego entra)"
            6 -> "Logarítmica (muere de verdad)"
            7 -> "Respiro profundo (pausa marcada)"
            8 -> "Ultra suave (ganancia constante)"
            else -> "Lineal"
        }
    }

    if (showCrossfadeCurveDialog) {
        EnumDialog(
            onDismiss = { showCrossfadeCurveDialog = false },
            onSelect = {
                onCrossfadeCurveChange(it)
                showCrossfadeCurveDialog = false
            },
            title = "Estilo de transición",
            current = crossfadeCurve,
            values = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8),
            valueText = { crossfadeCurveName(it) }
        )
    }

    streamQualityDialogFor?.let { network ->
        val mobile = network == "mobile"
        EnumDialog(
            onDismiss = { streamQualityDialogFor = null },
            onSelect = {
                if (mobile) onMobileStreamQualityChange(it) else onWifiStreamQualityChange(it)
                streamQualityDialogFor = null
            },
            title = stringResource(if (mobile) R.string.stream_quality_mobile else R.string.stream_quality_wifi),
            current = if (mobile) mobileStreamQuality else wifiStreamQuality,
            values = iad1tya.echo.music.playback.NetworkStreamQuality.Level.entries.toList(),
            valueText = { streamQualityName(it) }
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
        var showCrossfadeBetaDialog by remember { mutableStateOf(false) }

        if (showCrossfadeBetaDialog) {
            DefaultDialog(
                onDismiss = { showCrossfadeBetaDialog = false },
                title = { Text(stringResource(R.string.crossfade_beta_title)) },
                buttons = {
                    TextButton(onClick = { ActionLog.tap(ActionId.SETPLR_CROSSFADE_BETA_CANCEL); showCrossfadeBetaDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = {
                        showCrossfadeBetaDialog = false
                        onCrossfadeEnabledChange(true)
                    }) {
                        Text(stringResource(R.string.enable))
                    }
                }
            ) {
                Text(stringResource(R.string.crossfade_beta_message))
            }
        }

        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        Material3SettingsGroup(
            title = "Ahorro de datos",
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.offline),
                    title = { Text("Modo ahorro de datos") },
                    description = {
                        Text("Fuerza el audio en Opus y desactiva letras automáticas, videos, precarga, canvas y scrobbling para gastar el mínimo de datos")
                    },
                    trailingContent = {
                        Switch(
                            checked = dataSaverEnabled,
                            onCheckedChange = onDataSaverEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    },
                    onClick = { onDataSaverEnabledChange(!dataSaverEnabled) }
                ))
                // Registry 811 (owner H6b): one quality for Wi-Fi, another for mobile data. Data Saver ON wins over both.
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.network_node),
                    title = { Text(stringResource(R.string.stream_quality_wifi)) },
                    description = { Text(if (dataSaverEnabled) streamQualityForced else streamQualityName(wifiStreamQuality)) },
                    onClick = { ActionLog.tap(ActionId.SETPLR_STREAM_QUALITY_WIFI); streamQualityDialogFor = "wifi" }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.network_node),
                    title = { Text(stringResource(R.string.stream_quality_mobile)) },
                    description = { Text(if (dataSaverEnabled) streamQualityForced else streamQualityName(mobileStreamQuality)) },
                    onClick = { ActionLog.tap(ActionId.SETPLR_STREAM_QUALITY_MOBILE); streamQualityDialogFor = "mobile" }
                ))
            }
        )

        Material3SettingsGroup(
            title = stringResource(R.string.player),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.notification),
                    title = { Text("Mostrar notificaciones de audio de respaldo") },
                    description = {
                        Text("Muestra una notificación cuando se baja a una calidad de transmisión inferior")
                    },
                    trailingContent = {
                        Switch(
                            checked = showAudioFallbackToast,
                            onCheckedChange = onShowAudioFallbackToastChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    },
                    onClick = { onShowAudioFallbackToastChange(!showAudioFallbackToast) }
                ))

                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.linear_scale),
                    title = { Text(stringResource(R.string.crossfade)) },
                    description = {
                        Column {
                            Text(stringResource(R.string.crossfade_desc))
                            iad1tya.echo.music.ui.component.FidelityPausedNote(fidelityMode)
                        }
                    },
                    showBadge = true,
                    trailingContent = {
                        Switch(
                            checked = crossfadeEnabled,
                            onCheckedChange = {
                                if (!crossfadeEnabled) {
                                    showCrossfadeBetaDialog = true
                                } else {
                                    onCrossfadeEnabledChange(false)
                                }
                            },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (crossfadeEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = {
                        ActionLog.tap(ActionId.SETPLR_CROSSFADE)
                        if (!crossfadeEnabled) {
                            showCrossfadeBetaDialog = true
                        } else {
                            onCrossfadeEnabledChange(false)
                        }
                    }
                ))
                if (crossfadeEnabled) {
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.timer),
                        title = { Text(stringResource(R.string.crossfade_duration)) },
                        description = {
                            Column {
                                Text(pluralStringResource(R.plurals.seconds, crossfadeDuration.toInt(), crossfadeDuration.toInt()))
                                Slider(
                                    value = crossfadeDuration,
                                    onValueChange = onCrossfadeDurationChange,
                                    valueRange = 1f..15f,
                                    steps = 14
                                )
                            }
                        }
                    ))
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.graphic_eq),
                        title = { Text("Estilo de transición") },
                        description = { Text(crossfadeCurveName(crossfadeCurve)) },
                        onClick = { ActionLog.tap(ActionId.SETPLR_CROSSFADE_CURVE); showCrossfadeCurveDialog = true }
                    ))
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.album),
                        title = { Text(stringResource(R.string.crossfade_gapless)) },
                        description = { Text(stringResource(R.string.crossfade_gapless_desc)) },
                        trailingContent = {
                            Switch(
                                checked = crossfadeGapless,
                                onCheckedChange = onCrossfadeGaplessChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (crossfadeGapless) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onCrossfadeGaplessChange(!crossfadeGapless) }
                    ))
                }
                // Smooth entry on MANUAL track changes. Its consumer (MusicService.fadeOnManualChangeHint)
                // does NOT depend on crossfade, so this row must live OUTSIDE the crossfade block — nested
                // there it was unreachable (and therefore stuck ON) whenever crossfade was off.
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.play),
                    title = { Text("Entrada suave al cambiar de canción") },
                    description = {
                        Column {
                            Text("Al saltar o elegir otra canción a mano, la nueva entra enseguida con un fundido muy corto (estilo AIMP) en vez de golpe")
                            iad1tya.echo.music.ui.component.FidelityPausedNote(fidelityMode)
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = fadeOnManualChange,
                            onCheckedChange = onFadeOnManualChangeChange,
                            colors = SwitchDefaults.colors()
                        )
                    },
                    onClick = { onFadeOnManualChangeChange(!fadeOnManualChange) }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.history),
                    title = { Text(stringResource(R.string.history_duration)) },
                    description = {
                        // 5..120 s in 5 s stops: the engine default (30 s) must be a REAL stop, otherwise the
                        // slider silently refuses to return to the value the app ships with (the old
                        // 1..100/steps=9 range only stopped at 1, 11, 21, 31…). The floor stays ABOVE zero on
                        // purpose: at 0 the "played long enough to count" test passes for every instantly
                        // skipped track, which would pollute history and Mi Top.
                        Slider(
                            value = historyDurationShown,
                            onValueChange = { onHistoryDurationChange(it.roundToInt().toFloat()) },
                            valueRange = 5f..120f,
                            steps = 22
                        )
                    },
                    trailingContent = {
                        // The SHOWN (clamped) value, matching the thumb: a legacy stored 1f would otherwise
                        // park the thumb on the 5s stop while the label read "1 second" — and the engine
                        // would keep using 1s until the slider was physically dragged.
                        Text(
                            text = pluralStringResource(
                                R.plurals.seconds,
                                historyDurationShown.roundToInt(),
                                historyDurationShown.roundToInt(),
                            )
                        )
                    }
                ))
                // Skip-silence toggles are intentionally HIDDEN from the UI: skip silence is hardcoded OFF in
                // MusicService for Hi-Res / video A/V sync, so both switches ("Skip silence" and "Skip silence
                // instantly") were non-functional. The SkipSilenceKey / SkipSilenceInstantKey prefs and their
                // callbacks are left defined on purpose; only the two Switch items were removed.
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.audio_offload)) },
                    description = {
                        Text(offloadBlockedReason ?: stringResource(R.string.audio_offload_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = if (offloadBlockedReason != null) false else audioOffload,
                            onCheckedChange = onAudioOffloadChange,
                            enabled = offloadBlockedReason == null,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (offloadBlockedReason == null && audioOffload) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { if (offloadBlockedReason == null) onAudioOffloadChange(!audioOffload) }
                ))
                

                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.skip_next),
                    title = { Text("Precargar la siguiente canción") },
                    description = { Text("Almacena en caché la siguiente canción para reproducción sin cortes") },
                    trailingContent = {
                        Switch(
                            checked = preloadNextSongEnabled,
                            onCheckedChange = onPreloadNextSongEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (preloadNextSongEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPreloadNextSongEnabledChange(!preloadNextSongEnabled) }
                ))

                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.skip_next),
                    title = { Text(stringResource(R.string.sponsorblock_title)) },
                    description = { Text(stringResource(R.string.sponsorblock_description)) },
                    trailingContent = {
                        Switch(
                            checked = sponsorBlockEnabled,
                            onCheckedChange = onSponsorBlockEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sponsorBlockEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSponsorBlockEnabledChange(!sponsorBlockEnabled) }
                ))

                if (preloadNextSongEnabled) {
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.library_music),
                        title = { Text("Límite de precarga") },
                        description = {
                            Slider(
                                value = preloadNextSongLimit.toFloat(),
                                onValueChange = { onPreloadNextSongLimitChange(it.roundToInt()) },
                                valueRange = 1f..10f,
                                steps = 9
                            )
                        },
                        trailingContent = {
                            Text(text = preloadNextSongLimit.toString())
                        }
                    ))
                    
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.queue_music),
                        title = { Text("Precargar letras") },
                        description = { Text("También almacena en caché las letras de las canciones precargadas") },
                        trailingContent = {
                            Switch(
                                checked = preloadLyricsEnabled,
                                onCheckedChange = onPreloadLyricsEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (preloadLyricsEnabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onPreloadLyricsEnabledChange(!preloadLyricsEnabled) }
                    ))
                }
                
                if (BuildConfig.CAST_AVAILABLE) {
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.cast),
                        title = { Text(stringResource(R.string.google_cast)) },
                        description = { Text(stringResource(R.string.google_cast_description)) },
                        trailingContent = {
                            Switch(
                                checked = enableGoogleCast,
                                onCheckedChange = onEnableGoogleCastChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (enableGoogleCast) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onEnableGoogleCastChange(!enableGoogleCast) }
                    ))
                }
                // Búsqueda progresiva: ya no es opcional (orden del dueño 2026-09-16, "siempre
                // activada sí o sí"). El comportamiento vive en Thumbnail.kt; dejar aquí un
                // interruptor que no decide nada sería mentirle al usuario.
            }
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.queue),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.queue_music),
                    title = { Text(stringResource(R.string.persistent_queue)) },
                    description = { Text(stringResource(R.string.persistent_queue_desc)) },
                    trailingContent = {
                        Switch(
                            checked = persistentQueue,
                            onCheckedChange = onPersistentQueueChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (persistentQueue) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPersistentQueueChange(!persistentQueue) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.repeat),
                    title = { Text(stringResource(R.string.disable_load_more_when_repeat_all)) },
                    description = { Text(stringResource(R.string.disable_load_more_when_repeat_all_desc)) },
                    trailingContent = {
                        Switch(
                            checked = disableLoadMoreWhenRepeatAll,
                            onCheckedChange = onDisableLoadMoreWhenRepeatAllChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (disableLoadMoreWhenRepeatAll) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onDisableLoadMoreWhenRepeatAllChange(!disableLoadMoreWhenRepeatAll) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.similar),
                    title = { Text(stringResource(R.string.enable_similar_content)) },
                    description = { Text(stringResource(R.string.similar_content_desc)) },
                    trailingContent = {
                        Switch(
                            checked = similarContentEnabled,
                            onCheckedChange = similarContentEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (similarContentEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { similarContentEnabledChange(!similarContentEnabled) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text("Aleatorio mejorado") },
                    description = { Text("El aleatorio recuerda qué canciones ya sonaron en cada álbum, EP, playlist tuya, playlist que sigues y en la biblioteca, y no repite ninguna hasta haberlas puesto todas. La memoria se mantiene aunque cierres la app o apagues y enciendas el aleatorio. Las canciones que ya oíste se marcan con un visto aunque esta opción esté apagada. Desactívalo para volver al aleatorio clásico.") },
                    trailingContent = {
                        Switch(
                            checked = enhancedShuffle,
                            onCheckedChange = onEnhancedShuffleChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enhancedShuffle) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnhancedShuffleChange(!enhancedShuffle) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text(stringResource(R.string.persistent_shuffle_title)) },
                    description = { Text(stringResource(R.string.persistent_shuffle_desc)) },
                    trailingContent = {
                        Switch(
                            checked = persistentShuffleAcrossQueues,
                            onCheckedChange = onPersistentShuffleAcrossQueuesChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (persistentShuffleAcrossQueues) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPersistentShuffleAcrossQueuesChange(!persistentShuffleAcrossQueues) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text(stringResource(R.string.remember_shuffle_and_repeat)) },
                    description = { Text(stringResource(R.string.remember_shuffle_and_repeat_desc)) },
                    trailingContent = {
                        Switch(
                            checked = rememberShuffleAndRepeat,
                            onCheckedChange = onRememberShuffleAndRepeatChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (rememberShuffleAndRepeat) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onRememberShuffleAndRepeatChange(!rememberShuffleAndRepeat) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text(stringResource(R.string.shuffle_playlist_first)) },
                    description = { Text(stringResource(R.string.shuffle_playlist_first_desc)) },
                    trailingContent = {
                        Switch(
                            checked = shufflePlaylistFirst,
                            onCheckedChange = onShufflePlaylistFirstChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (shufflePlaylistFirst) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShufflePlaylistFirstChange(!shufflePlaylistFirst) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.queue_music),
                    title = { Text(stringResource(R.string.prevent_duplicate_tracks_in_queue)) },
                    description = { Text(stringResource(R.string.prevent_duplicate_tracks_in_queue_desc)) },
                    trailingContent = {
                        Switch(
                            checked = preventDuplicateTracksInQueue,
                            onCheckedChange = onPreventDuplicateTracksInQueueChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (preventDuplicateTracksInQueue) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPreventDuplicateTracksInQueueChange(!preventDuplicateTracksInQueue) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.replay),
                    title = { Text(stringResource(R.string.previous_queue_offer_setting)) },
                    description = { Text(stringResource(R.string.previous_queue_offer_setting_desc)) },
                    trailingContent = {
                        Switch(
                            checked = previousQueueOffer,
                            onCheckedChange = onPreviousQueueOfferChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (previousQueueOffer) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPreviousQueueOfferChange(!previousQueueOffer) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.skip_next),
                    title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
                    description = { Text(stringResource(R.string.auto_skip_next_on_error_desc)) },
                    trailingContent = {
                        Switch(
                            checked = autoSkipNextOnError,
                            onCheckedChange = onAutoSkipNextOnErrorChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (autoSkipNextOnError) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAutoSkipNextOnErrorChange(!autoSkipNextOnError) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.misc),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.clear_all),
                    title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
                    trailingContent = {
                        Switch(
                            checked = stopMusicOnTaskClear,
                            onCheckedChange = onStopMusicOnTaskClearChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (stopMusicOnTaskClear) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onStopMusicOnTaskClearChange(!stopMusicOnTaskClear) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_off_pause),
                    title = { Text(stringResource(R.string.pause_music_when_media_is_muted)) },
                    trailingContent = {
                        Switch(
                            checked = pauseOnMute,
                            onCheckedChange = onPauseOnMuteChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (pauseOnMute) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPauseOnMuteChange(!pauseOnMute) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.bluetooth),
                    title = { Text(stringResource(R.string.resume_on_bluetooth_connect)) },
                    trailingContent = {
                        Switch(
                            checked = resumeOnBluetoothConnect,
                            onCheckedChange = onResumeOnBluetoothConnectChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (resumeOnBluetoothConnect) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onResumeOnBluetoothConnectChange(!resumeOnBluetoothConnect) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.screenshot),
                    title = { Text(stringResource(R.string.keep_screen_on_when_player_is_expanded)) },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = onKeepScreenOnChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (keepScreenOn) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onKeepScreenOnChange(!keepScreenOn) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = "Avanzado",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lock),
                    title = { Text(stringResource(R.string.youtube_decryption_settings)) },
                    description = { Text(stringResource(R.string.youtube_decryption_desc)) },
                    onClick = { ActionLog.tap(ActionId.SETPLR_YOUTUBE_DECRYPTION_SETTINGS); navController.navigate("settings/youtube_decryption") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    iad1tya.echo.music.ui.component.AuraTopAppBar(
        title = { Text(stringResource(R.string.player_and_audio)) },
        navigationIcon = {
            IconButton(
                onClick = { ActionLog.tap(ActionId.SETPLR_BACK); navController.navigateUp() },
                onLongClick = null
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
