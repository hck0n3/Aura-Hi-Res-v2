package iad1tya.echo.music.ui.newui

/*
 * Plan, phase 1 item 4 (registry rows 875 and 876): how many columns the Biblioteca LIST view uses, and when a detail page
 * (album, playlist, artist, automatic list) puts its header in a column of its own. Pure — no Android, no Compose — so the
 * numbers are tested on the JVM. The callers pass the width of the pane they are laid out in (the window layer's
 * `auraPaneWidthDp()` or their own BoxWithConstraints) and the layer's `isCompactHeight`.
 *
 * A compact window (< AURA_MEDIUM_MIN_WIDTH_DP: a phone, the folded Fold7's cover screen) ALWAYS gets one column: nothing
 * changes there.
 */

/**
 * Narrowest a column of list rows may get: the same 400 dp a 4-row song page needs side by side on the artist page
 * (`AURA_SONG_PAGE_MIN_WIDTH`, registry row 522), so a row keeps its title, artist and trailing icons readable.
 */
internal const val AURA_LIBRARY_LIST_MIN_COLUMN_DP = 400f

/** Never more than three columns of rows: past that a row is a stamp and the eye loses its line. */
internal const val AURA_LIBRARY_LIST_MAX_COLUMNS = 3

/**
 * Pure: columns of the Biblioteca LIST view (songs, albums, artists, playlists, the pinned hub) for a pane [paneWidthDp]
 * wide. One on a compact window or a compact-height one ([compactHeight]: a phone on its side keeps its list), as always;
 * from the medium width, as many 400 dp columns as fit (two from 800 dp, three from 1200 dp). The GRID view is not decided
 * here: it keeps its own adaptive cells (`auraLibraryCellDp`).
 */
internal fun auraLibraryListColumns(paneWidthDp: Float, compactHeight: Boolean): Int {
    if (compactHeight || auraWidthClassOf(paneWidthDp) == AuraWidthClass.Compact) return 1
    return (paneWidthDp / AURA_LIBRARY_LIST_MIN_COLUMN_DP).toInt().coerceIn(1, AURA_LIBRARY_LIST_MAX_COLUMNS)
}

/** Share of the pane the header column takes on a detail page (plan: «~40 %»). */
internal const val AURA_DETAIL_HEADER_FRACTION = 0.4f

/** The header column is the phone header: never narrower than the narrowest phone it is drawn for… */
internal const val AURA_DETAIL_HEADER_MIN_DP = 320f

/** …and never wider than the widest phone (`AURA_HOME_REFERENCE_MAX_WIDTH`, 430 dp), so its square cover stays a cover. */
internal const val AURA_DETAIL_HEADER_MAX_DP = 430f

/** The track column must keep at least one readable column of rows. */
internal const val AURA_DETAIL_BODY_MIN_DP = AURA_LIBRARY_LIST_MIN_COLUMN_DP

/**
 * Pure: width (dp) of the header column of a detail page in a pane [paneWidthDp] wide, or `null` = one column, exactly as
 * before. Two columns need a window that is not compact (width) nor compact-height (a phone on its side keeps its page),
 * and room for both: the header at 40 % of the pane between 320 and 430 dp, and at least 400 dp left for the tracks —
 * from a 720 dp pane on (a medium pane where it fits, every expanded pane).
 */
internal fun auraDetailHeaderWidthDp(paneWidthDp: Float, compactHeight: Boolean): Float? {
    if (compactHeight || auraWidthClassOf(paneWidthDp) == AuraWidthClass.Compact) return null
    val header = (paneWidthDp * AURA_DETAIL_HEADER_FRACTION).coerceIn(AURA_DETAIL_HEADER_MIN_DP, AURA_DETAIL_HEADER_MAX_DP)
    return header.takeIf { paneWidthDp - header >= AURA_DETAIL_BODY_MIN_DP }
}
