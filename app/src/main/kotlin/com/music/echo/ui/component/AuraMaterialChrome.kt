package iad1tya.echo.music.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin

// ──────────────────────────────────────────────────────────────────────────────────────────────────
// SAME CHROME ON EVERY SCREEN (owner 2026-09-13: "muchas inconsistencias de diseño"). Screens that are
// still classic composables drew Material's own top bar and cards on top of the new UI. These drop-ins
// keep Material's parameters, so a call site only changes its name; with the new UI on they render the
// Aura bar (transparent over the Aura ground, Aura title type, Aura icon tints) and the Aura card
// (surface fill + hairline, no elevation). With the new UI off they are the exact Material components.
// ──────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun auraPremium(): Boolean = rememberAuraPanelSkin().let { it.enabled && it.darkGround }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun auraTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = AuraPalette.GroundRaised,
    navigationIconContentColor = AuraPalette.OnGround,
    titleContentColor = AuraPalette.OnGround,
    actionIconContentColor = AuraPalette.OnGroundMuted,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val premium = auraPremium()
    TopAppBar(
        title = if (premium) {
            { ProvideTextStyle(AuraType.SheetTitle) { title() } }
        } else {
            title
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = if (premium) auraTopBarColors() else colors,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraCenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val premium = auraPremium()
    CenterAlignedTopAppBar(
        title = if (premium) {
            { ProvideTextStyle(AuraType.SheetTitle) { title() } }
        } else {
            title
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = if (premium) auraTopBarColors() else colors,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun AuraCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!auraPremium()) {
        Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
        return
    }
    Card(
        modifier = modifier,
        shape = AuraShapes.Card,
        colors = CardDefaults.cardColors(containerColor = AuraPalette.SurfaceFill, contentColor = AuraPalette.OnGround),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, AuraPalette.SurfaceLine),
        content = content,
    )
}

@Composable
fun AuraCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!auraPremium()) {
        Card(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
        return
    }
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = AuraShapes.Card,
        colors = CardDefaults.cardColors(containerColor = AuraPalette.SurfaceFill, contentColor = AuraPalette.OnGround),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, AuraPalette.SurfaceLine),
        content = content,
    )
}
