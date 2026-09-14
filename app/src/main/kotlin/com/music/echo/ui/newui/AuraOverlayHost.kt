package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.atomic.AtomicLong

// ──────────────────────────────────────────────────────────────────────────────────────────────────
// GLASS FOR EVERY FLOATING WINDOW (owner directive 2026-09-14: "no solo el mismo color, quiero el
// mismo estilo hasta sus transparencias y efectos" — the mini player's). The mini pill is real glass
// because it is a SIBLING of the shell haze source. Dialogs are composed by screens INSIDE the NavHost,
// i.e. DESCENDANTS of that source, where haze 1.7.2 silently draws nothing — so they could only ever
// show the opaque plate under their glass. This host is the portal: an in-window dialog registers its
// panel here and the outlet composes it next to BottomSheetMenu (a sibling of the source, above the
// player sheet and menus), where the same shellGlass the pill uses actually samples and blurs.
// ──────────────────────────────────────────────────────────────────────────────────────────────────

@Stable
class AuraOverlayHostState {
    internal val entries = mutableStateListOf<AuraOverlayEntry>()
}

internal class AuraOverlayEntry(val id: Long, val content: @Composable () -> Unit)

/** Provided by MainActivity while the new UI and its glass are on; null → dialogs compose in place. */
val LocalAuraOverlayHost = staticCompositionLocalOf<AuraOverlayHostState?> { null }

/**
 * True inside a real dialog / sheet WINDOW. An overlay opened from there must stay a window: the host
 * lives in the activity window, which draws UNDER any dialog window.
 */
val LocalInsideDialogWindow = staticCompositionLocalOf { false }

private val nextOverlayId = AtomicLong()

/** Composes every registered floating panel, in opening order (the latest on top). */
@Composable
fun AuraOverlayOutlet(state: AuraOverlayHostState?) {
    state ?: return
    state.entries.forEach { entry -> key(entry.id) { entry.content() } }
}

/** True when the portal is usable here (a host exists and we are not inside a dialog window). */
@Composable
fun auraOverlayHostAvailable(): Boolean =
    LocalAuraOverlayHost.current != null && !LocalInsideDialogWindow.current

/**
 * Registers [content] with the host for as long as the caller stays composed. Returns false (and
 * registers nothing) when no host is usable, so the caller composes in place as before.
 */
@Composable
internal fun rememberAuraOverlayPortal(content: @Composable () -> Unit): Boolean {
    val host = LocalAuraOverlayHost.current
    if (host == null || LocalInsideDialogWindow.current) return false
    val latest = rememberUpdatedState(content)
    val id = remember { nextOverlayId.incrementAndGet() }
    DisposableEffect(host, id) {
        val entry = AuraOverlayEntry(id) { latest.value() }
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
    return true
}
