package iad1tya.echo.music.ui.utils

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Settings search -> the exact option (UX audit P22D).
 *
 * A search result used to open its screen at the TOP, leaving the user to hunt for the row they had
 * just found. Now the result carries its key (the entry's title, see [SettingsJumpLookup]) into the
 * destination, which is scrolled so that row sits in the middle of the screen and the row flashes a
 * wash for ~1.5 s.
 *
 * ## Why not a nav argument per route
 * The ~25 settings destinations are registered with fixed routes (`NavigationBuilder.kt`) and are
 * also reached from many plain `navigate("settings/…")` calls. Giving each one an optional
 * `?highlight=` argument would rewrite every registration and every screen signature for a one-shot
 * cosmetic effect. Instead the key rides with the navigation call itself: [navigate] opens the route,
 * takes the NEW back-stack entry and runs the jump on THAT entry's lifecycle scope, so it is cancelled
 * if the user leaves the screen first and never fires on any other screen.
 *
 * ## How the row is found without touching the 18 screens
 * Every settings row and group header goes through `Material3SettingsGroup`, which (only while a jump is
 * armed) tags itself with [TokenKey] via [rememberSettingsJumpAnchor]. Once the destination entry is
 * RESUMED (its enter transition is over), the merged semantics tree of the window is searched for the
 * key — the same tree TalkBack and `performScrollTo()` read — and the nearest vertical scroller is asked
 * to scroll by the distance that centres it (`SemanticsActions.ScrollBy`, the action `performScrollTo`
 * uses). A screen built without `Material3SettingsGroup` still gets the scroll, just not the wash.
 *
 * ## Cost (AGENTS.md rule 7)
 * Nothing runs until the user taps a search result. Then at most [SCAN_ATTEMPTS] tree walks, a few
 * frames apart, bounded by [JUMP_TIMEOUT_MS]; the wash animates one row for 1.5 s and the draw lambda
 * is removed when it ends. No polling, nothing per frame while music plays. Nothing is logged.
 */
object SettingsSearchJump {

    /** True from the tap until the jump ends. Rows add their tag only while it is true. */
    var armed by mutableStateOf(false)
        private set

    /** The token of the row being highlighted, or null. */
    var target: Any? by mutableStateOf(null)
        private set

    /** Semantics tag a settings row carries while [armed]. Not an accessibility property. */
    val TokenKey = SemanticsPropertyKey<Any>("AuraSettingsJumpToken")

    private val flashAnim = Animatable(0f)

    /** Current wash strength, 0..1. Read only from a draw lambda, so it invalidates drawing, not composition. */
    val flashAlpha: Float get() = flashAnim.value

    private var generation = 0

    /**
     * Opens [route] and jumps to the row whose text is [key]. If the row is not on that screen (a dialog
     * body, a row hidden by a flag) the screen simply opens at the top, as it always did.
     */
    fun navigate(navController: NavController, view: View, route: String, key: String) {
        val gen = ++generation
        target = null
        armed = true
        navController.navigate(route)
        val entry = navController.currentBackStackEntry
        if (entry == null) {
            armed = false
            return
        }
        entry.lifecycleScope.launch {
            var highlighting = false
            try {
                withTimeoutOrNull(JUMP_TIMEOUT_MS) {
                    entry.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                    repeat(SCAN_ATTEMPTS) {
                        delay(SCAN_INTERVAL_MS)
                        if (gen != generation) return@withTimeoutOrNull
                        val token = jump(view, key) ?: return@repeat
                        if (token !== NO_ROW) {
                            target = token
                            highlighting = true
                        }
                        return@withTimeoutOrNull
                    }
                }
            } finally {
                if (!highlighting && gen == generation) armed = false
            }
        }
    }

    /**
     * Runs the wash on the row whose token is [token]; called by that row. Clears the jump when done or
     * cancelled, unless a newer jump has already replaced it.
     */
    internal suspend fun flash(token: Any) {
        try {
            flashAnim.snapTo(0f)
            flashAnim.animateTo(1f, tween(FLASH_IN_MS))
            delay(FLASH_HOLD_MS)
            flashAnim.animateTo(0f, tween(FLASH_OUT_MS))
        } finally {
            if (target === token) {
                target = null
                armed = false
            }
        }
    }

    /** null = key not on screen yet; [NO_ROW] = scrolled, nothing to highlight; else the row's token. */
    private fun jump(view: View, key: String): Any? {
        val root = (view as? RootForTest)?.semanticsOwner?.rootSemanticsNode ?: return null
        val match = SettingsJumpLookup.find(
            root = root,
            key = key,
            children = { it.children },
            texts = { node -> node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() },
            isRow = { it.config.contains(TokenKey) },
        ) ?: return null
        val node = match.row ?: match.node
        scrollToCentre(node)
        return match.row?.config?.getOrNull(TokenKey) ?: NO_ROW
    }

    private fun scrollToCentre(node: SemanticsNode) {
        var scroller = node.parent
        while (scroller != null &&
            !(scroller.config.contains(SemanticsActions.ScrollBy) &&
                scroller.config.contains(SemanticsProperties.VerticalScrollAxisRange))
        ) {
            scroller = scroller.parent
        }
        if (scroller == null) return
        val viewport = scroller.boundsInRoot
        // positionInRoot is NOT clipped, so a row still below the fold reports where it really is.
        val dy = SettingsJumpLookup.scrollDelta(
            rowTop = node.positionInRoot.y,
            rowHeight = node.size.height.toFloat(),
            viewportTop = viewport.top,
            viewportBottom = viewport.bottom,
        )
        if (abs(dy) >= 1f) scroller.config[SemanticsActions.ScrollBy].action?.invoke(0f, dy)
    }

    private val NO_ROW = Any()

    private const val JUMP_TIMEOUT_MS = 5_000L
    private const val SCAN_ATTEMPTS = 8
    private const val SCAN_INTERVAL_MS = 120L
    private const val FLASH_IN_MS = 200
    private const val FLASH_HOLD_MS = 800L
    private const val FLASH_OUT_MS = 500
}

/**
 * The modifier a settings row (or group header) wears so [SettingsSearchJump] can find and highlight it.
 * While no jump is armed it is `Modifier` itself: no tag, no remember, no draw lambda.
 */
@Composable
fun rememberSettingsJumpAnchor(highlight: Color): Modifier {
    if (!SettingsSearchJump.armed) return Modifier
    val token = remember { Any() }
    val active = SettingsSearchJump.target === token
    if (active) {
        LaunchedEffect(token) { SettingsSearchJump.flash(token) }
    }
    val tagged = Modifier.semantics { this[SettingsSearchJump.TokenKey] = token }
    return if (active) {
        tagged.drawBehind {
            val a = SettingsSearchJump.flashAlpha
            if (a > 0f) drawRect(highlight.copy(alpha = highlight.alpha * a))
        }
    } else {
        tagged
    }
}
