package iad1tya.echo.music.ui.newui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Names an ICON-ONLY control for TalkBack (UX audit P22C, registry row 734).
 *
 * `clickable(onClickLabel = …)` — what [auraClickableInternal] and the primitives' clickable pass the description as —
 * is only the action hint («toca dos veces para …»). It is not the node's name: a control whose only child is an icon
 * with `contentDescription = null` is announced as a bare «Botón». Use this on the clickable of a control that draws no
 * text; a control that shows its label as text must NOT use it (the description would replace what it reads).
 */
internal fun Modifier.auraButtonName(name: String): Modifier = this.semantics { contentDescription = name }
