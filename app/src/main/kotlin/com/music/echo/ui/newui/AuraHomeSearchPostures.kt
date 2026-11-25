package iad1tya.echo.music.ui.newui

/*
 * Foldable postures for Inicio / Búsqueda / Estadísticas (plan, phase 1 item 4; registry row 878).
 *
 * Pure (no Android, no Compose) so the decisions are tested on the JVM. The window layer (AuraWindowInfo.kt) reports the
 * posture and the hinges in WINDOW pixels; a screen only knows its own box. These functions answer "where does the hinge
 * cross MY box" and "how do I split at it", and say null whenever the answer is "keep today's layout".
 */

/** Where a hinge crosses a box: [startDp]..[endDp] measured from the box's own left (vertical hinge). */
data class AuraHingeBand(val startDp: Float, val endDp: Float)

/**
 * Pure. In BOOK posture (half opened, fold vertical), where the separating vertical hinge crosses a box that starts at
 * [boxLeftPx] in the window and is [boxWidthPx] wide, in dp from the box's left. Null when the posture is not book, when
 * no half-opened separating vertical hinge exists, or when it does not fall strictly inside the box (a box that sits
 * wholly on one side of the fold has nothing to avoid).
 */
fun auraBookHingeBand(
    posture: AuraPosture,
    hinges: List<AuraHinge>,
    boxLeftPx: Float,
    boxWidthPx: Float,
    density: Float,
): AuraHingeBand? {
    if (posture != AuraPosture.Book || density <= 0f || boxWidthPx <= 0f) return null
    val hinge = hinges.firstOrNull { !it.isFlat && it.isVertical && it.isSeparating } ?: return null
    val start = (hinge.left - boxLeftPx) / density
    val end = (hinge.right - boxLeftPx) / density
    if (start <= 0f || end >= boxWidthPx / density || end < start) return null
    return AuraHingeBand(start, end)
}

/** Neither half of a search split at the hinge may be narrower than this (dp): the detail's own floor. */
const val AURA_SEARCH_HINGE_MIN_PANE_DP = 300f

/**
 * Pure. Width (dp) of the search DETAIL half when the split sits exactly on the hinge [band] of a box [totalDp] wide:
 * the list ends where the hinge starts and the detail begins where it ends (an occluding hinge's own width stays empty).
 * [detailOnLeft] is a right-to-left layout, where the detail is the left half. Null (keep the usual ~46 % split) when
 * there is no band or either half would be narrower than [AURA_SEARCH_HINGE_MIN_PANE_DP].
 */
fun auraSearchHingeDetailWidth(totalDp: Float, band: AuraHingeBand?, detailOnLeft: Boolean = false): Float? {
    band ?: return null
    val left = band.startDp
    val right = totalDp - band.endDp
    if (left < AURA_SEARCH_HINGE_MIN_PANE_DP || right < AURA_SEARCH_HINGE_MIN_PANE_DP) return null
    return if (detailOnLeft) left else right
}
