package iad1tya.echo.music.ui.newui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fila 498 — LAS LOSAS DE CRISTAL DEL MENÚ ⋯, para el resto de ventanas.
 *
 * Dueño (2026-10-21, con la captura del menú ⋯ de una canción como modelo): *«quiero que Pedir música y Añadir a lista
 * de reproducción sean del mismo estilo que las opciones de esta imagen»*. El menú ⋯ ([iad1tya.echo.music.ui.component.Material3MenuGroup])
 * agrupa sus filas en UNA losa redondeada (20 dp) con la placa traslúcida y el filete de las demás ventanas flotantes,
 * filas de 54 dp con el texto grande del menú y separadores finos; Pedir música y Añadir a lista eran filas sueltas,
 * campos con otra forma y chips. Estas piezas son esa misma losa, para que las dos ventanas usen el mismo material
 * (`AuraPalette.SurfaceFill` + `SurfaceLine` + `Divider`: lo mismo que lee `rememberAuraPanelSkin` en terreno oscuro).
 */
internal val AuraGlassCorner = 20.dp

/** Alto mínimo de una fila de losa: el mismo que el del menú ⋯. */
internal val AuraGlassRowMinHeight = 54.dp

/** UNA losa: el contenedor redondeado, traslúcido y con filete que agrupa filas relacionadas. */
@Composable
internal fun AuraGlassSlab(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AuraGlassCorner)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(shape)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, shape),
        content = content,
    )
}

/** El separador fino entre dos filas de una losa, sangrado hasta donde empieza el texto. */
@Composable
internal fun AuraGlassDivider(inset: Dp = 16.dp) {
    Spacer(
        modifier = Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(1.dp)
            .background(AuraPalette.Divider),
    )
}

/** Una fila de losa: icono opcional, título del menú (grande) y, si hace falta, algo al final. */
@Composable
internal fun AuraGlassRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    iconTint: Color = AuraPalette.OnGround,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .auraClickableInternal(onClick = onClick, contentDescription = title)
            .heightIn(min = AuraGlassRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = title,
            style = AuraType.MenuLabel,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Un selector de varias opciones DENTRO de una losa («Pequeña / Grande», «Todo / Conocido / Descubrir»): la opción
 * elegida lleva el lavado de acento y su filete, las demás quedan en la losa.
 */
@Composable
internal fun AuraGlassSegmented(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AuraGlassCorner)
    val cellShape = RoundedCornerShape(AuraGlassCorner - 4.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, shape)
            .padding(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(cellShape)
                    .background(if (on) AuraPalette.Teal.copy(alpha = 0.22f) else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, AuraPalette.Teal.copy(alpha = 0.45f), cellShape) else Modifier)
                    .auraClickableInternal(onClick = { onSelect(index) }, contentDescription = label)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label,
                    style = AuraType.Chip,
                    color = if (on) AuraPalette.OnGround else AuraPalette.OnGroundMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Un TRAMO de losa para una lista perezosa (`LazyColumn`): la lista de playlists de «Añadir a lista» puede tener cientos
 * de filas y no se puede componer entera dentro de una sola [AuraGlassSlab]. Cada fila pinta su trozo de la misma losa:
 * relleno y filete continuos (el primero redondea arriba, el último abajo; los de en medio solo llevan los lados) y el
 * separador fino debajo de todos menos del último. Se dibuja SIN recortar fuera de la fila: la forma completa de la losa
 * se prolonga y se recorta a los límites de este tramo, así que las esquinas salen exactas.
 */
@Composable
internal fun Modifier.auraGlassSegment(first: Boolean, last: Boolean, dividerInset: Dp = 16.dp): Modifier {
    val fill = AuraPalette.SurfaceFill
    val line = AuraPalette.SurfaceLine
    val divider = AuraPalette.Divider
    val corners = RoundedCornerShape(
        topStart = if (first) AuraGlassCorner else 0.dp,
        topEnd = if (first) AuraGlassCorner else 0.dp,
        bottomStart = if (last) AuraGlassCorner else 0.dp,
        bottomEnd = if (last) AuraGlassCorner else 0.dp,
    )
    return this.drawBehind {
        val radius = AuraGlassCorner.toPx()
        val stroke = 1.dp.toPx()
        val extraTop = if (first) 0f else radius * 2f
        val extraBottom = if (last) 0f else radius * 2f
        clipRect {
            translate(top = -extraTop) {
                val outline = RoundedCornerShape(AuraGlassCorner).createOutline(
                    Size(size.width, size.height + extraTop + extraBottom),
                    layoutDirection,
                    this,
                )
                drawOutline(outline, color = fill)
                // El filete va por dentro del borde: un trazo centrado en el borde perdería la mitad al recortar.
                translate(left = stroke / 2f, top = stroke / 2f) {
                    val inner = RoundedCornerShape(AuraGlassCorner).createOutline(
                        Size(size.width - stroke, size.height + extraTop + extraBottom - stroke),
                        layoutDirection,
                        this,
                    )
                    drawOutline(inner, color = line, style = Stroke(width = stroke))
                }
            }
        }
        if (!last) {
            val y = size.height - stroke / 2f
            drawLine(
                color = divider,
                start = Offset(dividerInset.toPx(), y),
                end = Offset(size.width - stroke, y),
                strokeWidth = stroke,
            )
        }
    }.clip(corners)
}
