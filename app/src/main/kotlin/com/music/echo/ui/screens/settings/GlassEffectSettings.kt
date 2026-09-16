/**
 * Aura Hi-Res Player (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.LiquidGlassBlurRadiusKey
import iad1tya.echo.music.constants.LiquidGlassChromaticAberrationKey
import iad1tya.echo.music.constants.LiquidGlassDepthEffectKey
import iad1tya.echo.music.constants.LiquidGlassGlobalEnabledKey
import iad1tya.echo.music.constants.LiquidGlassInteractiveKey
import iad1tya.echo.music.constants.LiquidGlassLensAmountKey
import iad1tya.echo.music.constants.LiquidGlassLensHeightKey
import iad1tya.echo.music.constants.LiquidGlassMiniPlayerEnabledKey
import iad1tya.echo.music.constants.LiquidGlassNavBarEnabledKey
import iad1tya.echo.music.constants.LiquidGlassSurfaceOpacityKey
import iad1tya.echo.music.constants.LiquidGlassSurfaceTintColorKey
import iad1tya.echo.music.constants.LiquidGlassTextColorKey
import iad1tya.echo.music.constants.LiquidGlassVibrancyKey
import iad1tya.echo.music.ui.component.ColorPickerDialog
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.IconButton as AppIconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.ui.component.isGlassEligible
import iad1tya.echo.music.utils.rememberPreference

/**
 * Liquid Glass (Beta) settings. The whole feature is DEFAULT OFF and additionally gated at
 * runtime by [isGlassEligible] (API 31+, raw tier MID/HIGH, not TV/car, Performance Mode off):
 * on ineligible devices the master switch is disabled and marked unavailable, and the effect
 * never renders regardless of the stored preferences.
 *
 * STALE UNTIL 2026-09-16, corrected here: this KDoc used to say the screen was UNREACHABLE with
 * "Interfaz nueva" on. That stopped being true on 2026-08-29 (NavigationBuilder.kt composes it in both
 * interfaces, the route bounce is gone and the search index points at it again — see the CORRECTION
 * note on `newUiEnabled` below), and leaving the claim here would now contradict the screen itself:
 * "Cristal interactivo" renders in BOTH interfaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassEffectSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    // FORZABLE (owner directive 2026-08-29, S26 Ultra/One UI 8.5 report): the master switch must be
    // TOGGLEABLE on any device regardless of the OEM customization layer. The previous behavior gated
    // the switch itself behind isGlassEligible() (API/tier/TV/PerformanceMode), which on whole device
    // families rendered the row disabled — indistinguishable from a placebo setting. The eligibility
    // check now survives ONLY as the honest description of what the in-app pipeline guarantees; the
    // USER decides whether the effect renders. This is safe because the renderer that actually runs
    // is the haze shell glass (AuraGlass.kt — in-app RenderEffect blur, verified working on Samsung
    // where WINDOW blur is a silent no-op: HALLAZGO-034, rows 160/166), not the window-blur path.
    val glassEligible = remember { isGlassEligible(context) }

    val (globalEnabled, onGlobalEnabledChange) = rememberPreference(
        LiquidGlassGlobalEnabledKey, defaultValue = false
    )
    val (interactiveGlass, onInteractiveGlassChange) = rememberPreference(
        LiquidGlassInteractiveKey, defaultValue = false
    )
    val (vibrancy, onVibrancyChange) = rememberPreference(
        LiquidGlassVibrancyKey, defaultValue = 1f
    )
    val (blurRadius, onBlurRadiusChange) = rememberPreference(
        LiquidGlassBlurRadiusKey, defaultValue = 8f
    )
    val (lensHeight, onLensHeightChange) = rememberPreference(
        LiquidGlassLensHeightKey, defaultValue = 0.5f
    )
    val (lensAmount, onLensAmountChange) = rememberPreference(
        LiquidGlassLensAmountKey, defaultValue = 0.5f
    )
    val (chromaticAberration, onChromaticAberrationChange) = rememberPreference(
        LiquidGlassChromaticAberrationKey, defaultValue = true
    )
    val (depthEffect, onDepthEffectChange) = rememberPreference(
        LiquidGlassDepthEffectKey, defaultValue = true
    )
    // 0 marks the theme-adaptive default tint (see MainActivity); the picker then
    // shows the color the current theme resolves to.
    val (surfaceTintColorInt, onSurfaceTintColorChange) = rememberPreference(
        LiquidGlassSurfaceTintColorKey, defaultValue = 0
    )
    val adaptiveTintColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val surfaceTintColor = if (surfaceTintColorInt == 0) {
        adaptiveTintColor
    } else {
        Color(surfaceTintColorInt)
    }
    val (surfaceOpacity, onSurfaceOpacityChange) = rememberPreference(
        LiquidGlassSurfaceOpacityKey, defaultValue = 0.4f
    )
    // 0 marks the theme-adaptive default text color (dark text on light glass, white on
    // dark) — deliberately NOT a hardcoded white default, which was illegible on light themes.
    val (textColorInt, onTextColorChange) = rememberPreference(
        LiquidGlassTextColorKey, defaultValue = 0
    )
    val adaptiveTextColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFF1B1B1B)
    } else {
        Color.White
    }
    val textColor = if (textColorInt == 0) adaptiveTextColor else Color(textColorInt)
    // LiquidGlassPlayerEnabledKey is intentionally NOT read here: the player glass surface is not
    // implemented, so this screen has nothing to toggle (see the disabled row below). The key is still
    // read in MainActivity, so a previously stored choice is preserved.
    val (miniPlayerEnabled, onMiniPlayerEnabledChange) = rememberPreference(
        LiquidGlassMiniPlayerEnabledKey, defaultValue = true
    )
    val (navBarEnabled, onNavBarEnabledChange) = rememberPreference(
        LiquidGlassNavBarEnabledKey, defaultValue = true
    )
    // READ-ONLY notes. With "Interfaz nueva" on, the two per-component switches below still have no
    // CLASSIC renderer (see each bullet), so they stay hidden under the flag — but the screen itself is
    // now reachable and meaningful in BOTH interfaces (owner directive 2026-08-29): the master switch
    // governs the shell haze source (nav bar + mini pill + global top bar) under the new UI.
    //  · mini player — the classic `NewMiniPlayer` pins its style to DEFAULT under the flag
    //    (MiniPlayer.kt); in portrait the composable is not even reached (AuraMiniPlayer replaces it).
    //  · barra de navegación — `FloatingNavigationToolbar`, the sole reader of NAV_BAR, is only
    //    composed on the `!newUiShell` branch (MainActivity.kt); the new shell draws AuraNavigationBar.
    //    Landscape uses the nav RAIL, so there is no orientation in which it comes back either.
    //
    // CORRECTION (the previous pass's comment here was FALSE). It claimed hiding the entry row made
    // this a "second line of defence for a deep link", which was wrong twice over: the entry row was
    // never the only door — the settings SEARCH indexed "Liquid Glass" straight to this screen's route
    // (SearchableSettings.kt), ungated, and the new Ajustes navigates whatever that index returns
    // (AuraSettingsScreen.kt) — and gating two of thirteen rows was never a defence for the other
    // eleven, which stayed live-looking and inert.
    //
    // 2026-08-29: the route bounce is GONE (NavigationBuilder.kt composes this screen in both
    // interfaces) and the search index points at the real route again. The `!newUiEnabled` guards on
    // the two per-component rows remain the honest statement of which CLASSIC switches have a
    // renderer; the shell surfaces they would control under the new UI are governed by the master
    // switch alone.
    val newUiEnabled = iad1tya.echo.music.ui.newui.rememberNewUiEnabled()

    var showVibrancyDialog by rememberSaveable { mutableStateOf(false) }
    var showBlurRadiusDialog by rememberSaveable { mutableStateOf(false) }
    var showLensHeightDialog by rememberSaveable { mutableStateOf(false) }
    var showLensAmountDialog by rememberSaveable { mutableStateOf(false) }
    var showSurfaceOpacityDialog by rememberSaveable { mutableStateOf(false) }
    var showSurfaceTintDialog by rememberSaveable { mutableStateOf(false) }
    var showTextColorDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )
        Material3SettingsGroup(
            title = stringResource(R.string.liquid_glass),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.check),
                    title = { Text(stringResource(R.string.liquid_glass_global_enabled)) },
                    description = {
                        // Honest description, never a gate (owner directive 2026-08-29): eligible
                        // devices keep the standard performance warning; anything else explains
                        // that forcing is deliberate and which pipeline actually renders.
                        Text(
                            stringResource(
                                if (glassEligible) {
                                    R.string.liquid_glass_performance_warning
                                } else {
                                    R.string.liquid_glass_forced_description
                                }
                            )
                        )
                    },
                    enabled = true,
                    trailingContent = {
                        Switch(
                            checked = globalEnabled,
                            onCheckedChange = onGlobalEnabledChange,
                            enabled = true,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (globalEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onGlobalEnabledChange(!globalEnabled) }
                ),
                // "Cristal interactivo" — la otra versión del efecto (orden del dueño 2026-09-16:
                // *"otra versión de liquid glass que se pueda aplicar a las dos versiones de mi
                // apariencia"*). Vive DENTRO del grupo del interruptor maestro y deshabilitada cuando
                // ese está apagado, porque no es un efecto aparte: es otra receta para las mismas
                // superficies. Funciona en las DOS apariencias — con la nueva, MainActivity empieza a
                // grabar la capa de fondo que esta receta muestrea justo cuando esto se enciende.
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.liquid_glass_interactive)) },
                    description = { Text(stringResource(R.string.liquid_glass_interactive_desc)) },
                    enabled = globalEnabled,
                    trailingContent = {
                        Switch(
                            checked = interactiveGlass,
                            onCheckedChange = onInteractiveGlassChange,
                            enabled = globalEnabled,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (interactiveGlass) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { if (globalEnabled) onInteractiveGlassChange(!interactiveGlass) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.liquid_glass_effects),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.liquid_glass_vibrancy)) },
                    description = { Text(stringResource(R.string.liquid_glass_vibrancy_desc)) },
                    onClick = { showVibrancyDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.liquid_glass_blur_radius)) },
                    description = { Text(stringResource(R.string.liquid_glass_blur_radius_desc)) },
                    onClick = { showBlurRadiusDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.liquid_glass_lens_height)) },
                    onClick = { showLensHeightDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.liquid_glass_lens_amount)) },
                    onClick = { showLensAmountDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.liquid_glass_chromatic_aberration)) },
                    trailingContent = {
                        Switch(
                            checked = chromaticAberration,
                            onCheckedChange = onChromaticAberrationChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (chromaticAberration) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onChromaticAberrationChange(!chromaticAberration) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.liquid_glass_depth_effect)) },
                    trailingContent = {
                        Switch(
                            checked = depthEffect,
                            onCheckedChange = onDepthEffectChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (depthEffect) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onDepthEffectChange(!depthEffect) }
                ),
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.liquid_glass_appearance),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.liquid_glass_surface_tint)) },
                    description = { Text(stringResource(R.string.liquid_glass_surface_tint_desc)) },
                    onClick = { showSurfaceTintDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.liquid_glass_surface_opacity)) },
                    description = { Text(stringResource(R.string.liquid_glass_surface_opacity_desc)) },
                    onClick = { showSurfaceOpacityDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.liquid_glass_text_color)) },
                    description = { Text(stringResource(R.string.liquid_glass_text_color_desc)) },
                    onClick = { showTextColorDialog = true }
                ),
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.liquid_glass_per_component),
            items = listOfNotNull(
                // The full screen player does not render a glass surface yet (nothing calls
                // GlassEffectConfig.isEnabledFor(GlassComponent.PLAYER)), so the switch is shown as
                // unavailable instead of pretending to control something. The preference is kept so the
                // user's choice survives until the player surface is actually implemented.
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.liquid_glass_player)) },
                    description = { Text(stringResource(R.string.liquid_glass_player_not_available)) },
                    enabled = false,
                    trailingContent = {
                        Switch(
                            checked = false,
                            onCheckedChange = null,
                            enabled = false,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(id = R.drawable.close),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    }
                ),
                // Both hidden with "Interfaz nueva" on — see the comment on `newUiEnabled` above.
                if (!newUiEnabled) {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.music_note),
                        title = { Text(stringResource(R.string.liquid_glass_mini_player)) },
                        trailingContent = {
                            Switch(
                                checked = miniPlayerEnabled,
                                onCheckedChange = onMiniPlayerEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (miniPlayerEnabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onMiniPlayerEnabledChange(!miniPlayerEnabled) }
                    )
                } else null,
                if (!newUiEnabled) {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.nav_bar),
                        title = { Text(stringResource(R.string.liquid_glass_nav_bar)) },
                        trailingContent = {
                            Switch(
                                checked = navBarEnabled,
                                onCheckedChange = onNavBarEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (navBarEnabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onNavBarEnabledChange(!navBarEnabled) }
                    )
                } else null,
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            )
        )
    }

    if (showVibrancyDialog) {
        var tempValue by remember { mutableFloatStateOf(vibrancy) }
        DefaultDialog(
            onDismiss = { tempValue = vibrancy; showVibrancyDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 1f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = vibrancy; showVibrancyDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onVibrancyChange(tempValue); showVibrancyDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.liquid_glass_vibrancy), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "%.2f".format(tempValue), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..2f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showBlurRadiusDialog) {
        var tempValue by remember { mutableFloatStateOf(blurRadius) }
        DefaultDialog(
            onDismiss = { tempValue = blurRadius; showBlurRadiusDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 8f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = blurRadius; showBlurRadiusDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onBlurRadiusChange(tempValue); showBlurRadiusDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.liquid_glass_blur_radius), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "%.0f".format(tempValue), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..100f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showLensHeightDialog) {
        var tempValue by remember { mutableFloatStateOf(lensHeight) }
        DefaultDialog(
            onDismiss = { tempValue = lensHeight; showLensHeightDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 0.5f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = lensHeight; showLensHeightDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onLensHeightChange(tempValue); showLensHeightDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.liquid_glass_lens_height), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "%.2f".format(tempValue), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showLensAmountDialog) {
        var tempValue by remember { mutableFloatStateOf(lensAmount) }
        DefaultDialog(
            onDismiss = { tempValue = lensAmount; showLensAmountDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 0.5f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = lensAmount; showLensAmountDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onLensAmountChange(tempValue); showLensAmountDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.liquid_glass_lens_amount), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "%.2f".format(tempValue), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showSurfaceOpacityDialog) {
        var tempValue by remember { mutableFloatStateOf(surfaceOpacity) }
        DefaultDialog(
            onDismiss = { tempValue = surfaceOpacity; showSurfaceOpacityDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 0.4f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = surfaceOpacity; showSurfaceOpacityDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onSurfaceOpacityChange(tempValue); showSurfaceOpacityDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.liquid_glass_surface_opacity), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "%.2f".format(tempValue), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showSurfaceTintDialog) {
        ColorPickerDialog(
            initialColor = surfaceTintColor,
            title = stringResource(R.string.liquid_glass_surface_tint),
            onDismiss = { showSurfaceTintDialog = false },
            onConfirm = { color ->
                onSurfaceTintColorChange(color.toArgb())
                showSurfaceTintDialog = false
            },
            // Reset restores the theme-adaptive default rather than a fixed color.
            onReset = {
                onSurfaceTintColorChange(0)
                showSurfaceTintDialog = false
            },
        )
    }

    if (showTextColorDialog) {
        ColorPickerDialog(
            initialColor = textColor,
            title = stringResource(R.string.liquid_glass_text_color),
            onDismiss = { showTextColorDialog = false },
            onConfirm = { color ->
                onTextColorChange(color.toArgb())
                showTextColorDialog = false
            },
            // Reset restores the theme-adaptive default (0), not hardcoded white: text must stay
            // legible over glass in light AND dark themes.
            onReset = {
                onTextColorChange(0)
                showTextColorDialog = false
            },
        )
    }

    iad1tya.echo.music.ui.component.AuraTopAppBar(
        title = { Text(stringResource(R.string.liquid_glass_settings)) },
        navigationIcon = {
            AppIconButton(
                onClick = navController::navigateUp,
                onLongClick = null,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
