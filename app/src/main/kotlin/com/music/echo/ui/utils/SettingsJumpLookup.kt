package iad1tya.echo.music.ui.utils

/**
 * The pure half of the settings-search jump ([SettingsSearchJump]): given the destination screen's
 * semantics tree, which node is the row an index entry promised?
 *
 * Kept free of Compose/Android types (the tree is read through three lambdas) so the key -> row rule
 * is unit-tested on the JVM.
 *
 * The KEY of an index entry is its title: the very string `SearchableSettings.kt` resolves from the same
 * resource (or literal) the destination screen renders, so a title that is on screen is found and a
 * title that is not (a dialog body, a row hidden by a flag) finds nothing and the jump degrades to the
 * old behaviour: the screen opens at the top.
 */
object SettingsJumpLookup {

    /** A matched node and, when it has one, the settings row (itself or an ancestor) that wraps it. */
    data class Match<N>(val node: N, val row: N?)

    fun normalize(text: String): String = text.trim().replace(WHITESPACE, " ")

    fun matches(texts: List<String>, key: String): Boolean {
        val wanted = normalize(key)
        return wanted.isNotEmpty() && texts.any { normalize(it) == wanted }
    }

    /**
     * Depth-first, in screen order. The first match INSIDE a settings row wins (that is what gets the
     * highlight); a match outside every row — a top-bar title, a screen with no settings rows — is only
     * the fallback, so the same text in the top bar never steals the jump from the row below it.
     */
    fun <N> find(
        root: N,
        key: String,
        children: (N) -> List<N>,
        texts: (N) -> List<String>,
        isRow: (N) -> Boolean,
    ): Match<N>? {
        val wanted = normalize(key)
        if (wanted.isEmpty()) return null
        var fallback: Match<N>? = null
        val stack = ArrayDeque<Pair<N, N?>>()
        stack.addLast(root to (if (isRow(root)) root else null))
        while (stack.isNotEmpty()) {
            val (node, row) = stack.removeLast()
            if (texts(node).any { normalize(it) == wanted }) {
                val match = Match(node, row)
                if (row != null) return match
                if (fallback == null) fallback = match
            }
            val kids = children(node)
            for (i in kids.indices.reversed()) {
                val kid = kids[i]
                stack.addLast(kid to (if (isRow(kid)) kid else row))
            }
        }
        return fallback
    }

    /**
     * How far to scroll so the row's centre lands on the viewport's centre. Positive scrolls the content
     * up (reveals what is below), the sign `SemanticsActions.ScrollBy` uses.
     */
    fun scrollDelta(rowTop: Float, rowHeight: Float, viewportTop: Float, viewportBottom: Float): Float =
        (rowTop + rowHeight / 2f) - (viewportTop + viewportBottom) / 2f

    private val WHITESPACE = Regex("\\s+")
}
