package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** Row 343: "lo elimino y al segundo vuelve a aparecer" (removing from a YouTube-synced playlist). */
class PlaylistRemovalSyncTest {

    private val now = LocalDateTime.of(2026, 10, 7, 18, 0, 0)

    @Test
    fun `the stored entry id wins, and a missing one is taken from the remote copy`() {
        val ids = RemotePlaylistEdits.resolveSetVideoIds(
            toRemove = listOf("a" to "svA", "b" to null),
            remote = listOf("a" to "svA", "b" to "svB", "c" to "svC"),
        )
        assertEquals(listOf("svA", "svB"), ids)
    }

    @Test
    fun `a song repeated in the playlist never removes the same entry twice`() {
        val ids = RemotePlaylistEdits.resolveSetVideoIds(
            toRemove = listOf("a" to null, "a" to null),
            remote = listOf("a" to "sv1", "x" to "svX", "a" to "sv2"),
        )
        assertEquals(listOf("sv1", "sv2"), ids)
        // An id already known locally is not handed to another row.
        val mixed = RemotePlaylistEdits.resolveSetVideoIds(
            toRemove = listOf("a" to "sv1", "a" to null),
            remote = listOf("a" to "sv1", "a" to "sv2"),
        )
        assertEquals(listOf("sv1", "sv2"), mixed)
    }

    @Test
    fun `an entry YouTube does not have stays unresolved instead of guessing`() {
        assertEquals(listOf(null), RemotePlaylistEdits.resolveSetVideoIds(listOf("z" to null), listOf("a" to "sv")))
    }

    @Test
    fun `an edit made while the remote copy downloaded is never overwritten`() {
        val before = now.minusHours(2)
        assertTrue(PlaylistSyncGuard.standDown(false, before, now.minusSeconds(1), now))
        assertTrue(PlaylistSyncGuard.standDown(false, null, now.minusSeconds(1), now))
    }

    @Test
    fun `a very recent edit is left alone, an old one is reconciled`() {
        val recent = now.minusSeconds(20)
        assertTrue(PlaylistSyncGuard.standDown(false, recent, recent, now))
        val old = now.minusSeconds(PlaylistSyncGuard.RECENT_LOCAL_EDIT_SECONDS + 1)
        assertFalse(PlaylistSyncGuard.standDown(false, old, old, now))
        assertFalse(PlaylistSyncGuard.standDown(false, null, null, now))
        // A clock that jumped backwards never freezes a playlist.
        val future = now.plusHours(1)
        assertFalse(PlaylistSyncGuard.standDown(false, future, future, now))
    }

    @Test
    fun `an empty local playlist is always filled`() {
        assertFalse(PlaylistSyncGuard.standDown(true, now.minusHours(1), now, now))
    }

    @Test
    fun `missing entry ids are filled from the matching remote entries`() {
        assertEquals(
            listOf(1 to "sv1", 3 to "sv3"),
            PlaylistSyncGuard.setVideoIdBackfill(listOf("sv0", null, null, null), listOf("sv0", "sv1", null, "sv3")),
        )
        assertTrue(PlaylistSyncGuard.setVideoIdBackfill(listOf("a"), listOf("b")).isEmpty())
    }

    /** The placebo that caused this: a lookup in a table nothing writes. It must not come back. */
    @Test
    fun `no removal path looks the entry id up in the never-written set_video_id table`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val offenders = File(root, "app/src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readText().contains("getSetVideoId(") }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
