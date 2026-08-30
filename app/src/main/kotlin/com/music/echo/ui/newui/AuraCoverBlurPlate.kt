package iad1tya.echo.music.ui.newui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.constants.LiquidGlassGlobalEnabledKey
import iad1tya.echo.music.utils.PrefsBridge
import iad1tya.echo.music.utils.isLocalMediaId

/**
 * The dialog/menu answer to the S26 Ultra "transparencia plana" report (registry row 197, owner
 * directive 2026-08-30).
 *
 * ## Why a cover-blur plate, and not window blur / haze
 * Samsung compiles the framework with `config_windowBlurEnabled=false`, so `FLAG_BLUR_BEHIND` /
 * `setBackgroundBlurRadius` are silent no-ops there (HALLAZGO-034, rows 160/166) — every dialog
 * and menu on the owner's Galaxy reads as flat transparency. The live backdrop-sampling shader is
 * under audit for the native sig-11 on One UI 8.5 (row 196) and is NOT to be used here. The one blur
 * that renders on EVERY OEM is the technique the mini player and the expanded player's
 * LIQUID_GLASS style already ship: paint the CURRENT TRACK'S COVER blurred with the native
 * `Modifier.blur` (a RenderEffect on the drawn layer, not on the window, not on the backdrop) and
 * lay a tint over it. One 128×128 decode per track, shared with the pill/player's own request —
 * per-TRACK cost, never per-frame, and no backdrop is ever sampled.
 *
 * ## Behaviour (the contract this plate must keep)
 *  · **Liquid Glass master switch OFF** (or not yet emitted — cold start) → this composable
 *    composes NOTHING. Byte-identical to today's flat tint in every dialog/menu that hosts it.
 *  · **No song playing / local track / no thumbnail / API < 31** → nothing either: the cover would
 *    be unblurred (a different style, not a degraded one — same discipline as
 *    `rememberAuraGround`'s own coverUrl gate), and a plate with no cover would only dim the
 *    existing tint. The caller's existing fill keeps doing exactly what it did.
 *  · **Switch ON + playable cover** → the blurred cover is drawn under the caller's existing
 *    tint/texture, as the FIRST background layer. The tint is not removed: it still reads on top,
 *    which is what keeps text contrast where it is today (OnGround ≥ 4.5:1 was measured over
 *    Ground; the 0.72 scrim keeps the composited plate in that family).
 *
 * ## Sizing
 * Sizing belongs to the HOST, not to the plate: pass `Modifier.matchParentSize()` inside the
 * host Box (the exact pattern [AuraFloatingSurface]'s own frost layer uses) so the plate covers
 * the whole panel and follows its host's size, whatever that host is. The host must keep another
 * child that contributes its size (the dialog's content Column) — a `matchParentSize` plate with
 * no sizing sibling is the registry row 1 trap.
 *
 * ## Cost / battery (AGENTS rule 7)
 * Same request the pill makes: ONE 128×128 `allowHardware(false)` decode per track, blurred once
 * by the layer's RenderEffect while the dialog is open. No animation, no invalidation loop, no
 * per-frame work — the plate is a still image for as long as the dialog is on screen.
 */
@Composable
fun AuraCoverBlurPlate(
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.72f,
    /** When the plate fully replaces the flat base behind it, redraw the host's hairline on top. */
    stroke: Shape? = null,
) {
    // GATE 1 — premium only, enforced HERE and not just at the call sites (registry lesson #5: the
    // barrier lives in the consumer too). A plate composed in a classic (non-premium) dialog would
    // paint a dark blurred navy over a light Material surface — unreadable. Hosts gate as well,
    // but a host that forgets must fail SAFE, not silently corrupt.
    val skin = rememberAuraPanelSkin()
    if (!skin.enabled || !skin.darkGround) return

    // GATE 2 — the Liquid Glass master switch is the single owner-approved governor for every
    // forced-glass look (row 191). PrefsBridge.peek is the process-wide volatile snapshot — the
    // same read `glassForcedPreference()` / `AuraPalette.FrostFill` make — never a blocking
    // DataStore read in composition. Null (cold start, before the first emission) = OFF: the
    // HALLAZGO-034 byte-identical fallback is what ships.
    if (PrefsBridge.peek(LiquidGlassGlobalEnabledKey) != true) return

    val connection = LocalPlayerConnection.current ?: return
    val mediaMetadata by connection.mediaMetadata.collectAsState()
    val id = mediaMetadata?.id ?: return
    // `Modifier.blur` is a no-op below API 31 and a local track has no remote cover — the same two
    // gates `rememberAuraGround` applies before drawing its own cover (AuraPlayer.kt).
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val coverUrl = mediaMetadata?.thumbnailUrl
        ?.takeIf { it.isNotEmpty() && !id.isLocalMediaId() }
        ?: return

    val context = LocalContext.current
    // remember(url) — one request object per track, the same cache key (no transformations, no
    // size in the memory-cache key) the pill and the player's cover already share, so a track
    // whose cover is already in the pill/player pays no extra decode at all.
    val request = remember(context, coverUrl) {
        ImageRequest.Builder(context)
            .data(coverUrl)
            .size(128, 128)
            .allowHardware(false)
            .crossfade(false)
            .build()
    }

    Box(modifier = modifier) {
        // The blurred cover — a RenderEffect on THIS layer, never on the window, never on the
        // backdrop. `ContentScale.Crop` so the artwork fills the panel with no letterbox bands.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(46.dp),
        ) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // The scrim, a CRISP sibling drawn ON TOP of the blur — the same layer order
        // [AuraGroundLayer] uses for the pill (cover inside the blur, scrim outside it). Put
        // inside the blurred box the scrim's own edges would fade with the blur and the artwork
        // would bleed at the panel border. GroundRaised follows the AMOLED switch exactly like
        // every other floating plate; 0.72 keeps OnGround text in the ≥ 4.5:1 family it was
        // measured for.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(AuraPalette.GroundRaised.copy(alpha = scrimAlpha)),
        )
        // Optional hairline re-stroke. `AuraFloatingSurface` paints its SurfaceLine border on the
        // transparent Surface that sits UNDER the content — a plate drawn on top of that base
        // covers the border stroke, so hosts that replace their base (this plate) ask it to be
        // drawn again, over the plate, same 1dp / same colour / same shape as the original.
        if (stroke != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, AuraPalette.SurfaceLine, stroke),
            )
        }
    }
}
