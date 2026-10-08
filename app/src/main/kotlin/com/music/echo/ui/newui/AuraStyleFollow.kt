package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R
import iad1tya.echo.music.reco.MusicStyle
import iad1tya.echo.music.reco.StyleContinuity

/**
 * Fila 356, punto 4 — the queue says which style the smart queue follows ("Siguiendo: Cumbia · cristiana")
 * and lets the user correct it: back to what the app detected, similar styles, any style, or another exact
 * style. An exact correction also teaches the app that artist's style (see setExactStyleOverride).
 *
 * Shown only when a style is being followed — nothing changes in the queue otherwise.
 */
@Composable
internal fun AuraStyleFollowRow(
    follow: StyleContinuity.Follow?,
    onOverride: (StyleContinuity.Override?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (follow == null) return
    if (follow.detected.isEmpty() && follow.applied.isEmpty() && follow.override == null) return
    var expanded by remember { mutableStateOf(false) }
    val christian = stringResource(R.string.style_follow_christian)
    val anyStyle = stringResource(R.string.style_follow_any)
    val similar = stringResource(R.string.style_follow_similar)
    val styleText = when (follow.override) {
        StyleContinuity.Override.AnyStyle -> anyStyle
        StyleContinuity.Override.Similar -> similar
        else -> follow.applied.joinToString(" + ") { MusicStyle.displayName(it) }
            .let { if (follow.christian && it.isNotEmpty()) "$it · $christian" else it }
    }
    if (styleText.isBlank()) return
    val description = stringResource(R.string.style_follow_cd)
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(AuraShapes.Pill)
                .background(AuraPalette.SurfaceFill)
                .auraClickableInternal(onClick = { expanded = true }, contentDescription = description)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.style_follow_label, styleText),
                style = AuraType.Chip,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            AuraIconGlyph(
                icon = AuraIcons.ChevronDown,
                contentDescription = null,
                size = 14.dp,
                tint = AuraPalette.OnGroundMuted,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = AuraShapes.Card,
            containerColor = AuraPalette.FrostFill,
        ) {
            @Composable
            fun option(label: String, selected: Boolean, onClick: () -> Unit) {
                val check: (@Composable () -> Unit)? = if (selected) {
                    { AuraIconGlyph(icon = AuraIcons.Check, contentDescription = null, size = 16.dp) }
                } else {
                    null
                }
                DropdownMenuItem(
                    text = { Text(label, color = AuraPalette.OnGround, style = AuraType.Chip) },
                    trailingIcon = check,
                    onClick = {
                        expanded = false
                        onClick()
                    },
                )
            }
            val detectedNames = follow.detected.joinToString(" + ") { MusicStyle.displayName(it) }
            option(
                label = stringResource(R.string.style_follow_detected) +
                    if (detectedNames.isNotEmpty()) ": $detectedNames" else "",
                selected = follow.override == null,
            ) { onOverride(null) }
            option(similar, follow.override == StyleContinuity.Override.Similar) {
                onOverride(StyleContinuity.Override.Similar)
            }
            option(anyStyle, follow.override == StyleContinuity.Override.AnyStyle) {
                onOverride(StyleContinuity.Override.AnyStyle)
            }
            HorizontalDivider(color = AuraPalette.OnGround.copy(alpha = 0.12f))
            Text(
                text = stringResource(R.string.style_follow_change),
                style = AuraType.Chip,
                color = AuraPalette.OnGroundMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // The detected styles' families first (the likely corrections: cumbia ↔ merengue), then the rest.
            val families = follow.detected.mapNotNull { MusicStyle.familyOf(it) }.toSet()
            val ordered = MusicStyle.ALL.sortedBy { if (it.family in families) 0 else 1 }
            ordered.forEach { style ->
                val exact = follow.override as? StyleContinuity.Override.Exact
                option(MusicStyle.displayName(style.id), exact?.style == style.id) {
                    onOverride(StyleContinuity.Override.Exact(style.id))
                }
            }
        }
    }
}
