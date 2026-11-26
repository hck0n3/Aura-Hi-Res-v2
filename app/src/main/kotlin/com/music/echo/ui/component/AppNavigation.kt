

package iad1tya.echo.music.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.newui.auraNavTap
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Immutable
private data class NavItemState(
    val isSelected: Boolean,
    val iconRes: Int
)

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    return navigationItems.any { it.route == screenRoute } && 
           currentRoute.startsWith("$screenRoute/")
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    // Registry row 895 (plan, phase 6.2): the TV shell's rail, Spotify TV style — big icons, a label under each one, and
    // Ajustes as its last destination. False = the classic rail exactly as it was.
    tvStyle: Boolean = false,
    onSettingsClick: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val isTvOrCar = rememberIsTvOrCar()
    // Read unconditionally (stable call order); used only by the TV rail: the remote comes back to the last destination.
    val focusRestorer = Modifier.tvFocusRestorer()
    val iconSize = if (tvStyle) 30.dp else 24.dp

    NavigationRail(
        modifier = modifier.then(if (tvStyle) focusRestorer else Modifier),
        containerColor = containerColor
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }
            
            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val interactionSource = remember { MutableInteractionSource() }
            
            
            if (isSearchItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSearchLongClick.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    auraNavTap(screen)
                                    onItemClick(screen, isSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }
            
            NavigationRailItem(
                modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
                selected = isSelected,
                onClick = {
                    if (!isSearchItem) {
                        auraNavTap(screen)
                        onItemClick(screen, isSelected)
                    }

                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId),
                        modifier = Modifier.size(iconSize),
                    )
                },
                label = if (tvStyle) {
                    { RailLabel(stringResource(screen.titleId)) }
                } else {
                    null
                },
                alwaysShowLabel = tvStyle,
            )
            if (tvStyle) Spacer(Modifier.height(8.dp))
        }

        if (onSettingsClick != null) {
            val settingsSelected = currentRoute == "settings" || currentRoute?.startsWith("settings/") == true
            NavigationRailItem(
                modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
                selected = settingsSelected,
                onClick = {
                    ActionLog.tap(ActionId.NAV_RAIL_SETTINGS)
                    onSettingsClick()
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = stringResource(R.string.settings),
                        modifier = Modifier.size(iconSize),
                    )
                },
                label = if (tvStyle) {
                    { RailLabel(stringResource(R.string.settings)) }
                } else {
                    null
                },
                alwaysShowLabel = tvStyle,
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** The TV rail's label: one line, and a long translation ends in «…» instead of being cut (row 870). */
@Composable
private fun RailLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
