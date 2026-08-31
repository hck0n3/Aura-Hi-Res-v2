
package iad1tya.echo.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.ui.newui.AuraCoverBlurPlate
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.LocalAuraFloatingChrome
import iad1tya.echo.music.ui.newui.auraFloatingContainerColor
import iad1tya.echo.music.ui.newui.auraFloatingContentColor
import iad1tya.echo.music.ui.newui.auraFloatingScrimColor
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin

val LocalBottomSheetPageState = compositionLocalOf { BottomSheetPageState() }

@Stable
class BottomSheetPageState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)

    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPage(
    modifier: Modifier = Modifier,
    state: BottomSheetPageState,
    background: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation),
) {
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    val sheetColor = if (premium) auraFloatingContainerColor() else background
    val onSheet = if (premium) auraFloatingContentColor() else MaterialTheme.colorScheme.onSurface

    AnimatedBottomSheet(
        isVisible = state.isVisible,
        onDismissRequest = {
            focusManager.clearFocus()
            state.isVisible = false
        },
        sheetState = sheetState,
        shape = if (premium) AuraShapes.Sheet else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = sheetColor,
        contentColor = onSheet,
        scrimColor = if (premium) auraFloatingScrimColor() else Color.Black.copy(alpha = 0.32f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (premium) AuraPalette.OnGround.copy(alpha = 0.28f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
            )
        },
        modifier = modifier.fillMaxHeight(),
    ) {
        CompositionLocalProvider(LocalAuraFloatingChrome provides premium) {
            // Cover-blur plate (registry row 197 extension, owner directive 2026-08-30: "la
            // búsqueda dentro del reproductor también lo quiero"). This is the frost sheet the
            // player's quick-search slides in through (bottomSheetPageState.show, AuraPlayer.kt)
            // — the plate is the FIRST background layer of the WHOLE sliding panel, full-bleed
            // edge to edge, above the flat containerColor it replaces and under all the content.
            // Same layer order as BottomSheetMenu.kt: plate inside a Box that fills the sheet,
            // content Column as the sizing sibling on top (the padding moves INSIDE the box so
            // the blur has no flat 16dp frame around it). Composes NOTHING with the Liquid Glass
            // switch OFF, no cover playing, local track or API < 31 — byte-identical sheet.
            // Painted only — it consumes no input, so the search field's IME, the result lists'
            // scroll and the drag-handle dismissal keep working exactly as today. The browse
            // panes (artist/album/playlist) swap content INSIDE this same panel, so the whole
            // search flow inherits the effect. The other tenant, ShowMediaInfo, paints its own
            // opaque background over the full sheet and is unaffected; ShowOffsetDialog is
            // translucency-family like the rest of the row-197 overlays. Classic (non-premium)
            // never composes the plate.
            Box(modifier = Modifier.fillMaxHeight()) {
                if (premium) {
                    AuraCoverBlurPlate(modifier = Modifier.matchParentSize())
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                ) {
                    state.content(this)
                }
            }
        }
    }
}
