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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ForceSplitViewKey
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.constants.ImmersiveCanvasOnRotateKey
import iad1tya.echo.music.constants.ShowNowPlayingPanelKey
import iad1tya.echo.music.constants.SidePanelOnLeftKey
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.utils.DeviceCapabilities
import iad1tya.echo.music.utils.DeviceForm
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.constants.PerformanceClassChoiceKey
import iad1tya.echo.music.ui.component.EnumDialog
import iad1tya.echo.music.ui.component.isGlassSupported
import iad1tya.echo.music.utils.PerformanceClass
import iad1tya.echo.music.utils.PerformanceClassChoice
import iad1tya.echo.music.utils.PerformanceClasses
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.rememberEnumPreference
import kotlinx.coroutines.launch

/** Row 898: the visible name of a performance class (Entrada / Equilibrada / Premium). */
@Composable
private fun performanceClassLabel(cls: PerformanceClass): String = stringResource(
    when (cls) {
        PerformanceClass.ENTRY -> R.string.perf_class_entry
        PerformanceClass.BALANCED -> R.string.perf_class_balanced
        PerformanceClass.PREMIUM -> R.string.perf_class_premium
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val (highPerfMode, onHighPerfModeChange) = rememberPreference(HighPerformanceModeKey, defaultValue = false)
    val (forceSplit, onForceSplitChange) = rememberPreference(ForceSplitViewKey, defaultValue = false)
    val (sidePanelLeft, onSidePanelLeftChange) = rememberPreference(SidePanelOnLeftKey, defaultValue = false)
    val (showNowPlayingPanel, onShowNowPlayingPanelChange) = rememberPreference(ShowNowPlayingPanelKey, defaultValue = true)
    val (immersiveCanvasOnRotate, onImmersiveCanvasOnRotateChange) =
        rememberPreference(ImmersiveCanvasOnRotateKey, defaultValue = false)

    // Read-only diagnostic: why the mode auto-enabled (LOW hardware / TV / car). Row 898: the hardware tier is shown as
    // its performance class (Entrada / Equilibrada / Premium), translated.
    val detectedClass = remember { PerformanceClasses.fromTier(DeviceCapabilities.tier(context)) }
    val detectedForm = remember {
        when {
            DeviceForm.isTelevision(context) -> R.string.perf_class_form_tv
            DeviceForm.isCar(context) -> R.string.perf_class_form_car
            else -> null
        }
    }
    val detectedClassLabel = performanceClassLabel(detectedClass)
    val detected = if (detectedForm != null) {
        stringResource(R.string.perf_class_detected_form, detectedClassLabel, stringResource(detectedForm))
    } else {
        stringResource(R.string.perf_class_detected, detectedClassLabel)
    }
    // Row 898: the manual override. Picking an option is an explicit action and applies that class's switches at once
    // (PerformanceClasses.seedFor); the one-time automatic seed never overwrites anything (App.applyPerformanceClassSeedV1).
    val classChoice by rememberEnumPreference(PerformanceClassChoiceKey, PerformanceClassChoice.AUTO)
    var showClassDialog by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // isGlassEligible without its Performance-mode term (the pick decides that): glass only draws on a raw MID/HIGH tier.
    val glassCapable = remember {
        isGlassSupported() &&
            DeviceCapabilities.tier(context).let { it == iad1tya.echo.music.utils.DeviceTier.MID || it == iad1tya.echo.music.utils.DeviceTier.HIGH } &&
            !DeviceForm.isTvOrCar(context)
    }
    val classInUse = PerformanceClasses.effective(detectedClass, classChoice)
    if (showClassDialog) {
        val autoLabel = stringResource(R.string.perf_class_auto_value, detectedClassLabel)
        EnumDialog(
            onDismiss = { showClassDialog = false },
            onSelect = { choice: PerformanceClassChoice ->
                showClassDialog = false
                val cls = PerformanceClasses.effective(detectedClass, choice)
                scope.launch {
                    runCatching {
                        context.dataStore.edit { p ->
                            p[PerformanceClassChoiceKey] = choice.name
                            PerformanceClasses.write(p, PerformanceClasses.seedValues(PerformanceClasses.seedFor(cls, glassCapable)))
                        }
                    }
                }
            },
            title = stringResource(R.string.perf_class_choice_title),
            current = classChoice,
            values = PerformanceClassChoice.entries.toList(),
            valueText = { choice ->
                when (choice) {
                    PerformanceClassChoice.AUTO -> autoLabel
                    PerformanceClassChoice.ENTRY -> performanceClassLabel(PerformanceClass.ENTRY)
                    PerformanceClassChoice.BALANCED -> performanceClassLabel(PerformanceClass.BALANCED)
                    PerformanceClassChoice.PREMIUM -> performanceClassLabel(PerformanceClass.PREMIUM)
                }
            },
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
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        val newUiActive = iad1tya.echo.music.ui.newui.rememberNewUiEnabled()
        Material3SettingsGroup(
            title = "Rendimiento",
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.speed),
                        title = { Text("Modo alto rendimiento") },
                        description = {
                            Text(
                                "Un único modo que recorta la app al máximo en CUALQUIER dispositivo. Apaga los fondos " +
                                    "animados, los desenfoques de portada (reproductor, cola y cabeceras de álbum, artista y " +
                                    "lista) y el video musical, cambia de pantalla sin animación, usa portadas de menor " +
                                    "resolución, muestra menos carruseles y no los descarga, refresca la letra a ritmo " +
                                    "reducido, apaga SponsorBlock, baja el buffer de audio y cambia la transición entre canciones " +
                                    "a corte directo (la precarga ligera de enlaces se mantiene) — solo audio, fluido y fresco. " +
                                    "Se enciende solo en hardware de gama realmente baja (incluidos TV boxes y pantallas de auto " +
                                    "modestos), pero podés activarlo en cualquier equipo. El sonido (ecualizador, volumen seguro) " +
                                    "NO se ve afectado. Algunos cambios aplican al reiniciar la app."
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = highPerfMode,
                                onCheckedChange = onHighPerfModeChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (highPerfMode) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onHighPerfModeChange(!highPerfMode) }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.speed),
                        title = { Text(detected) },
                        description = {
                            Text("Se activa solo en el primer inicio en hardware de gama realmente baja, incluidos TV boxes y pantallas de auto modestos; los TVs potentes conservan la experiencia completa. Podés activarlo o desactivarlo manualmente cuando quieras.")
                        },
                        onClick = {}
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.speed),
                        title = { Text(stringResource(R.string.perf_class_choice_title)) },
                        description = {
                            Text(stringResource(R.string.perf_class_choice_desc, performanceClassLabel(classInUse)))
                        },
                        onClick = {
                            ActionLog.tap(ActionId.SETPERF_CLASS)
                            showClassDialog = true
                        }
                    )
                )
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = "Pantalla grande",
            items = listOfNotNull(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.speed),
                    title = { Text("Vista dividida estilo Spotify") },
                    description = {
                        Text(
                            "Fuerza el diseño de pantalla ancha (cola/lista a un lado, reproductor y controles al otro) " +
                                "en cualquier dispositivo. Se activa solo en TV, autos, tablets y plegables abiertos; " +
                                "actívalo aquí si lo querés también en tu teléfono u otra pantalla."
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = forceSplit,
                            onCheckedChange = onForceSplitChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (forceSplit) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onForceSplitChange(!forceSplit) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.speed),
                    title = { Text("Panel del reproductor a la izquierda") },
                    description = {
                        Text(
                            "En la vista dividida, mueve la cola/reproductor al lado IZQUIERDO (por defecto va a la " +
                                "derecha). Útil en pantallas de auto donde se prefiere de un lado."
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = sidePanelLeft,
                            onCheckedChange = onSidePanelLeftChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sidePanelLeft) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSidePanelLeftChange(!sidePanelLeft) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.speed),
                    title = { Text("Mostrar el panel del reproductor") },
                    description = {
                        Text(
                            "En la vista dividida, muestra el panel lateral con la carátula y los controles del " +
                                "tema actual. Desactívalo para dejar la lista/biblioteca a todo el ancho (solo la " +
                                "barra lateral y el contenido). Solo aplica en pantallas anchas."
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = showNowPlayingPanel,
                            onCheckedChange = onShowNowPlayingPanelChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showNowPlayingPanel) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowNowPlayingPanelChange(!showNowPlayingPanel) }
                ),
                // Registry #48: rotating to landscape with the animated canvas active used to swallow the whole
                // interface. That immersive view is now opt-in; strings live in resources (the literals above are
                // pre-existing — see registry #37 on hardcoded Spanish in settings screens).
                // Row 349 (owner 2026-10-08, C2 decision 3: «quítalo»): under the new UI this switch did
                // nothing — only the classic player has the canvas-only landscape view. Shown only there.
                if (newUiActive) null else Material3SettingsItem(
                    icon = painterResource(R.drawable.speed),
                    title = { Text(stringResource(R.string.immersive_canvas_on_rotate)) },
                    description = { Text(stringResource(R.string.immersive_canvas_on_rotate_desc)) },
                    trailingContent = {
                        Switch(
                            checked = immersiveCanvasOnRotate,
                            onCheckedChange = onImmersiveCanvasOnRotateChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (immersiveCanvasOnRotate) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onImmersiveCanvasOnRotateChange(!immersiveCanvasOnRotate) }
                )
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    iad1tya.echo.music.ui.component.AuraTopAppBar(
        title = { Text("Rendimiento") },
        navigationIcon = {
            IconButton(
                onClick = { ActionLog.tap(ActionId.SETPERF_BACK); navController.navigateUp() },
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
