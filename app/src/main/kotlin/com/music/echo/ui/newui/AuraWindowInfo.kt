package iad1tya.echo.music.ui.newui

/*
 * THE ONE ANSWER TO "HOW BIG IS THE WINDOW" (plan, phase 1 item 1).
 *
 * Before this layer every screen measured the window its own way: `Configuration.screenWidthDp`,
 * `LocalWindowInfo.containerSize`, a `BoxWithConstraints` at the root… and each one kept its own idea of what "wide" or
 * "short" meant. [AuraWindowInfo] is measured ONCE, near the root (`ProvideAuraWindowInfo` in MainActivity's setContent),
 * and everything that decides a layout from the WINDOW reads it from there (`auraWindowInfo()`).
 *
 * This file is pure (no Android, no Compose) so the classifier is tested on the JVM: the numbers in, the classes out.
 * The Compose side (the CompositionLocal, the provider, the readers) is in AuraWindowInfoLocal.kt.
 */

/** Width at/above which a window or pane is "medium": an unfolded foldable, a small tablet. Material's own line. */
const val AURA_MEDIUM_MIN_WIDTH_DP = 600

/** Width at/above which a window or pane is "expanded": a big tablet, a desktop window. Material's own line. */
const val AURA_EXPANDED_MIN_WIDTH_DP = 840

/** Below this height a window is "compact height": a phone turned on its side. Material's own line. */
const val AURA_COMPACT_HEIGHT_BELOW_DP = 480

/** Width class of a window or a pane. */
enum class AuraWidthClass { Compact, Medium, Expanded }

/** How a foldable is held. [Flat] also covers every device with no hinge at all. */
enum class AuraPosture {
    Flat,

    /** Half opened with the fold HORIZONTAL: a laptop / a tent on a table. */
    Tabletop,

    /** Half opened with the fold VERTICAL: held like a book. */
    Book,
}

/**
 * A fold or hinge of the display, in WINDOW pixels (as the window manager reports it). [isFlat] false = half opened.
 * [isSeparating] = the hinge splits the window into two logical areas; [isOccluding] = it hides content under it.
 */
data class AuraHinge(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val isFlat: Boolean,
    val isVertical: Boolean,
    val isSeparating: Boolean,
    val isOccluding: Boolean,
)

/**
 * The current WINDOW (not the physical screen): an app in split screen or free-form gets its own size here.
 *
 * Two readings of the same window are kept, ON PURPOSE, until wave 2 unifies them (registry row 869):
 *  - [widthDp] / [heightDp]: `LocalWindowInfo.containerSize` in dp (Float). The wide shell, the width-only layouts and the
 *    track columns have always decided on this one.
 *  - [configWidthDp] / [configHeightDp]: `Configuration.screenWidthDp` / `screenHeightDp` (whole dp). The floating-window
 *    recipe, the sheet height caps, the roomy picker rows, the library cells, Home's shelves and the tablet cover have
 *    always decided on this one.
 * They describe the same window but can differ by a rounding (or by the system bars on old platform versions), so
 * moving a call site from one to the other could move a layout across a threshold. Each call site keeps the one it had.
 */
data class AuraWindowInfo(
    val widthDp: Float,
    val heightDp: Float,
    val configWidthDp: Int,
    val configHeightDp: Int,
    val posture: AuraPosture = AuraPosture.Flat,
    val hinges: List<AuraHinge> = emptyList(),
) {
    /** Width class of the whole window. A pane narrower than the window: see `auraPaneWidthDp()`. */
    val widthClass: AuraWidthClass get() = auraWidthClassOf(widthDp)

    /** A phone turned on its side (wide but short). */
    val isCompactHeight: Boolean get() = isAuraCompactHeight(heightDp)

    /** Wider than tall. The same test the shell has always used for its landscape rail. */
    val isLandscape: Boolean get() = widthDp > heightDp
}

/** Pure: the width class of [widthDp]. Inclusive lower bounds: 600 is medium, 840 is expanded. */
fun auraWidthClassOf(widthDp: Float): AuraWidthClass = when {
    widthDp >= AURA_EXPANDED_MIN_WIDTH_DP -> AuraWidthClass.Expanded
    widthDp >= AURA_MEDIUM_MIN_WIDTH_DP -> AuraWidthClass.Medium
    else -> AuraWidthClass.Compact
}

/** Pure: is [heightDp] a compact height (a phone on its side)? 480 itself is not compact. */
fun isAuraCompactHeight(heightDp: Float): Boolean = heightDp < AURA_COMPACT_HEIGHT_BELOW_DP

/**
 * Pure: the posture from the hinges the window manager reports. A half-opened horizontal fold wins (tabletop: the
 * definition the Compose adaptive library uses for `Posture.isTabletop`), then a half-opened vertical one (book).
 * No hinge, or every hinge flat, is [AuraPosture.Flat].
 */
fun auraPostureOf(hinges: List<AuraHinge>): AuraPosture = when {
    hinges.any { !it.isFlat && !it.isVertical } -> AuraPosture.Tabletop
    hinges.any { !it.isFlat && it.isVertical } -> AuraPosture.Book
    else -> AuraPosture.Flat
}
