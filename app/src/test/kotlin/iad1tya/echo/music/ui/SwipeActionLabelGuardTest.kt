package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 806 (owner decision H1): both Aura swipe wrappers keep their per-screen meanings but always show the
 * action's icon AND words while the row moves. Pinned as source because a lost label compiles just as well.
 */
class SwipeActionLabelGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val content: String by lazy {
        File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraContent.kt").also { assertTrue(it.isFile) }.readText()
    }

    @Test
    fun `the song swipe names both of its actions`() {
        val box = content.substringAfter("fun AuraSwipeSongBox(").substringBefore("private const val AURA_LIKE_THRESHOLD")
        assertTrue(box.contains("AuraSwipeActionLabel(offset, threshold, goingRight = true, icon = AuraIcons.Queue, label = stringResource(R.string.play_next))"))
        assertTrue(box.contains("AuraSwipeActionLabel(offset, threshold, goingRight = false, icon = AuraIcons.Plus, label = stringResource(R.string.add_to_queue))"))
        // Meanings unchanged (row 773 pins the rest).
        assertTrue(box.contains("player?.playNext(listOf(mediaItem))") && box.contains("player?.addToQueue(listOf(mediaItem))"))
    }

    @Test
    fun `the library swipe names like or unlike and add to playlist`() {
        val box = content.substringAfter("fun AuraLibrarySwipeActionsBox(").substringBefore("private fun BoxScope.AuraSwipeActionLabel(")
        assertTrue(box.contains("label = stringResource(if (liked) R.string.action_remove_like else R.string.action_like),"))
        assertTrue(box.contains("label = stringResource(R.string.add_to_playlist),"))
        assertTrue(box.contains("onToggleLike()") && box.contains("onAddToPlaylist()"))
    }

    @Test
    fun `the label is drawn over the row, read only in the draw phase, never in the accessibility tree`() {
        val label = content.substringAfter("private fun BoxScope.AuraSwipeActionLabel(").substringBefore("\n}\n")
        assertTrue(label.contains(".clearAndSetSemantics {}"))
        assertTrue(label.contains("alpha = if (matches) (abs(value) / (threshold * 0.5f)).coerceIn(0f, 1f) else 0f"))
        assertTrue(label.contains(".drawBehind { drawRect(AuraPalette.Ground) }"))
        assertTrue(label.contains("AuraIconGlyph(") && label.contains("text = label,"))
        // No gesture of its own: the drag must stay on the parent's draggable.
        assertFalse(label.contains("pointerInput") || label.contains("clickable") || label.contains("draggable"))
        // The offset is never read in composition (it would recompose every row on every drag frame).
        assertEquals(1, Regex("""offset\.floatValue""").findAll(label).count())
    }
}
