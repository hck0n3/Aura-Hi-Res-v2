package iad1tya.echo.music.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusableItem

/** Corner radius of one menu group (the glass slab that holds a category of rows). */
private val MenuGroupCorner = 20.dp

/** A row is never shorter than this: 54 dp is a comfortable thumb target without the old 16 dp-padded card. */
private val MenuRowMinHeight = 54.dp

/**
 * ONE glass group per category of a menu (Cola, Biblioteca, Descargas, Navegar…).
 *
 * Owner 2026-10-20: *«Mejora la estética de los tres puntos… se ve feo»*. The old group was a stack of separate rounded
 * cards (24 dp outer / 6 dp inner corners, 4 dp gutters, 16 dp of padding, a big title plus a long grey subtitle on
 * every row), which made every ⋯ sheet very tall and busy. Now it is ONE rounded slab with the translucent card fill and
 * hairline the other floating windows use ([rememberAuraPanelSkin]: fill / line / hairline / ink, so the cover tint of
 * the window shows through), slim dividers inset past the icon, 54 dp rows, a one-line title and an OPTIONAL short
 * description. Every [Material3MenuItemData] keeps working unchanged (same lambdas, same `onClick`, same `cardColors`
 * override for the destructive row of the selection menu).
 *
 * Every menu of the app (song, album, artist, playlist, queue, player, lyrics, selection, YouTube variants) builds its
 * rows through this one composable, so the redesign lands everywhere at once.
 */
@Composable
fun Material3MenuGroup(
    items: List<Material3MenuItemData>
) {
    if (items.isEmpty()) return
    val skin = rememberAuraPanelSkin()
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MenuGroupCorner)
    val fill = if (skin.enabled) skin.fill else scheme.surfaceVariant.copy(alpha = 0.3f)
    val divider = if (skin.enabled) skin.hairline else scheme.outlineVariant.copy(alpha = 0.5f)
    val ink = if (skin.enabled) skin.ink else scheme.onSurface
    val inkMuted = if (skin.enabled) skin.inkMuted else scheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(shape)
            .background(fill)
            .then(if (skin.enabled) Modifier.border(1.dp, skin.line, shape) else Modifier),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                // Hairline inset to where the title starts (16 dp padding + 24 dp icon + 16 dp gap), like Apple Music's
                // context menu. A row without an icon starts at the plain padding.
                Spacer(
                    modifier = Modifier
                        .padding(start = if (item.icon != null) 56.dp else 16.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(divider),
                )
            }
            val colors = item.cardColors
            if (colors != null) {
                // A row that brings its own colours (the inverted "remove download" row of the selection menu) keeps
                // the Card that painted them before; the Card also supplies its content colour.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = colors,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Material3MenuItemRow(item = item, ink = Color.Unspecified, inkMuted = Color.Unspecified)
                }
            } else {
                Material3MenuItemRow(item = item, ink = ink, inkMuted = inkMuted)
            }
        }
    }
}

@Composable
private fun Material3MenuItemRow(
    item: Material3MenuItemData,
    /** [Color.Unspecified] = inherit whatever the parent (a [Card]) already provides. */
    ink: Color,
    inkMuted: Color,
) {
    val rowContent = if (ink.isSpecifiedColor()) ink else LocalContentColor.current
    val rowMuted = if (inkMuted.isSpecifiedColor()) inkMuted else rowContent.copy(alpha = 0.7f)
    CompositionLocalProvider(LocalContentColor provides rowContent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvFocusableItem(rememberIsTvOrCar())
                .clickable(
                    enabled = item.onClick != null,
                    onClick = { item.onClick?.invoke() }
                )
                .heightIn(min = MenuRowMinHeight)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item.icon?.let { icon ->
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                ProvideTextStyle(AuraType.MenuLabel) {
                    item.title()
                }

                item.description?.let { desc ->
                    Spacer(modifier = Modifier.height(1.dp))
                    ProvideTextStyle(
                        AuraType.RowSubtitle.copy(fontSize = 13.sp, lineHeight = 17.sp, color = rowMuted)
                    ) {
                        desc()
                    }
                }
            }
            item.trailingContent?.let { trailing ->
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
            if (item.showChevron) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = rowMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun Color.isSpecifiedColor(): Boolean = this != Color.Unspecified

data class Material3MenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    /** Optional second line. Only where it ADDS information (the artist name, the album title) — never "Añadir a la cola". */
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val cardColors: CardColors? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    /** A trailing chevron: this row opens another screen (Ver artista, Ver álbum…). */
    val showChevron: Boolean = false,
)
