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
 * wide. One on a compact window, as always; from the medium width, as many 400 dp columns as fit (two from 800 dp, three
 * from 1200 dp). The GRID view is not decided here: it keeps its own adaptive cells (`auraLibraryCellDp`).
 */
internal fun auraLibraryListColumns(paneWidthDp: Float): Int {
    if (auraWidthClassOf(paneWidthDp) == AuraWidthClass.Compact) return 1
    return (paneWidthDp / AURA_LIBRARY_LIST_MIN_COLUMN_DP).toInt().coerceIn(1, AURA_LIBRARY_LIST_MAX_COLUMNS)
}
