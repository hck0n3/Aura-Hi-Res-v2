package iad1tya.echo.music.ui.newui

/*
 * AJUSTES IN TWO PANES ON A WIDE WINDOW (plan, phase 1 item 2; registry row 873).
 *
 * On an unfolded foldable or a tablet the settings index stays on the LEFT and the page the user picked opens on the
 * RIGHT. The pages are the SAME routes and the SAME screens as on a phone: each page route is registered through
 * `auraSettingsPane` (AuraSettingsTwoPane.kt), which, only on a wide window, draws the index beside it. A phone (and a
 * phone on its side) never sees any of this: the page is drawn exactly as before.
 *
 * This file is pure (no Android, no Compose) so the decisions are tested on the JVM.
 */

/** The Ajustes index itself. */
const val AURA_SETTINGS_ROUTE = "settings"

/**
 * Every page the Ajustes index opens straight away. These are the destinations the index rows of AuraSettingsScreen
 * navigate to; on a wide window each one is shown in the right pane with the index on the left.
 * Not here, on purpose: «Estadísticas» (`stats`), «Migrar listas» (`migration`) and «Escuchar juntos» (`listen_together`)
 * are full screens of their own (the last one a top-level tab), so they keep opening over the whole window.
 */
val AURA_SETTINGS_PANE_ROUTES: List<String> = listOf(
    "settings/player",
    "settings/sound",
    "settings/performance",
    "settings/content",
    "settings/ai",
    "settings/accounts",
    "settings/ytm_sync",
    "settings/lastfm",
    "settings/cloud",
    "settings/storage",
    "settings/integrations/listen_together",
    "settings/device_sync",
    "settings/aura_services",
    "settings/backup_restore",
    "settings/privacy",
    "settings/appearance",
    "settings/logs",
    "settings/feedback",
    "settings/update",
    "settings/about",
)

/**
 * Pages opened FROM one of the pages above, shown in the right pane too, with their parent highlighted in the index.
 * Left out on purpose: the equalizer (`settings/equalizer`, a full screen of its own) and every page that is not under
 * one index page (terms, privacy policy, changelog, playback decryption, Spotify import): they open over the whole window
 * as before.
 */
val AURA_SETTINGS_PANE_CHILDREN: Map<String, String> = mapOf(
    "settings/appearance/theme" to "settings/appearance",
    "settings/appearance/liquidglass" to "settings/appearance",
    "settings/content/romanization" to "settings/content",
    "settings/content/hidden" to "settings/content",
    "settings/sound/autoeq" to "settings/sound",
    "settings/accounts/subscription_cleanup" to "settings/accounts",
    "settings/device_sync/held" to "settings/device_sync",
    "settings/ai/taste" to "settings/ai",
)

/** Index pane width bounds (dp) and the share of the pane it takes between them. */
const val AURA_SETTINGS_INDEX_MIN_WIDTH_DP = 300f
const val AURA_SETTINGS_INDEX_MAX_WIDTH_DP = 360f
const val AURA_SETTINGS_INDEX_WIDTH_FRACTION = 0.4f

/** A route pattern or a concrete route without its query: `settings/storage?autoOpenExportPicker=…` → `settings/storage`. */
fun auraSettingsRouteBase(route: String?): String? = route?.substringBefore('?')

/**
 * Pure: which index row a route highlights — the page itself, or the index page it was opened from. `null` = the route
 * is not shown in the right pane (it is not a settings page, or it keeps its own full screen).
 */
fun auraSettingsPaneSelectionOf(route: String?): String? {
    val base = auraSettingsRouteBase(route) ?: return null
    return when {
        base in AURA_SETTINGS_PANE_ROUTES -> base
        else -> AURA_SETTINGS_PANE_CHILDREN[base]
    }
}

/** Pure: is this route drawn as part of the two-pane Ajustes (the index itself, or a page in the right pane)? */
fun auraSettingsIsPaneRoute(route: String?): Boolean =
    auraSettingsRouteBase(route) == AURA_SETTINGS_ROUTE || auraSettingsPaneSelectionOf(route) != null

/**
 * Pure: does Ajustes use two panes? The same rule as every other wide layout of the app (registry row 869): the pane the
 * screen is laid out in is medium or expanded (600 dp or more) and the window is not a phone on its side. [paneWidthDp] is
 * the CONTENT pane (narrower than the window while the split player is open), so the two panes never squeeze into it.
 */
fun auraSettingsUsesTwoPanes(paneWidthDp: Float, windowHeightDp: Float): Boolean =
    auraWidthClassOf(paneWidthDp) != AuraWidthClass.Compact && !isAuraCompactHeight(windowHeightDp)

/** Pure: width (dp) of the index pane: 40 % of the pane, never under 300 dp nor over 360 dp. */
fun auraSettingsIndexWidthDp(paneWidthDp: Float): Float =
    (paneWidthDp * AURA_SETTINGS_INDEX_WIDTH_FRACTION)
        .coerceIn(AURA_SETTINGS_INDEX_MIN_WIDTH_DP, AURA_SETTINGS_INDEX_MAX_WIDTH_DP)

/**
 * Pure: a move between two routes of the two-pane Ajustes, on a two-pane window, is a change of the RIGHT pane only, so it
 * has no screen transition (the full-width slide would carry the unchanged index out and back in). Every other move — a
 * phone, leaving Ajustes, a page shown in its welcome (onboarding) mode — keeps the app's usual transition.
 */
fun auraSettingsSwapIsInstant(
    twoPanes: Boolean,
    fromRoute: String?,
    fromOnboarding: Boolean,
    toRoute: String?,
    toOnboarding: Boolean,
): Boolean =
    twoPanes && !fromOnboarding && !toOnboarding &&
        auraSettingsIsPaneRoute(fromRoute) && auraSettingsIsPaneRoute(toRoute)

/**
 * The last two-pane decision a composed Ajustes route made. Navigation's transition lambdas are not composable, so they
 * cannot measure the window: they read this. Written (a plain assignment, no state, nothing per frame) each time an Ajustes
 * route composes, which is always before a navigation away from it can start.
 */
object AuraSettingsPanes {
    @Volatile
    var twoPanesNow: Boolean = false

    /** The answer when no two-pane index is on screen. */
    val NotScrolling: () -> Boolean = { false }

    /**
     * Two panes: is the INDEX on the left being scrolled (dragged or flung) right now? The shell's app-bar scroll behavior
     * asks this in its `canScroll`: the index sits inside the same nested-scroll parent as the page on the right, so without
     * it scrolling the index would collapse or expand the page's top bar (e.g. the large title of «Acerca de»). The page's
     * own scroll still drives its bar. Registered by the composed two-pane index (AuraSettingsScreen.kt) and cleared when it
     * leaves; a phone never registers one. Read only when a scroll is dispatched: nothing per frame.
     */
    @Volatile
    var indexScrolling: () -> Boolean = NotScrolling
}
