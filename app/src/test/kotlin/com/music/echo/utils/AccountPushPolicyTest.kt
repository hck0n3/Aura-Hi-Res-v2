package iad1tya.echo.music.utils

import iad1tya.echo.music.utils.AccountPushPolicy.Op
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20: «que de todo lo que yo me desuscriba, se desuscriba en tiempo real de YouTube, al
 * igual que los "no me gusta"».
 *
 * Pins the two things that made an unlike / un-save "come back" or never leave the phone: the stale-list
 * veto rules of [AccountPushPolicy] and the wiring (every tap goes through [AccountPush], the sync honours
 * the veto, "No me gusta" reaches the account).
 */
class AccountPushPolicyTest {

    private val now = 1_000_000_000L

    @Test
    fun `a pending negative action vetoes the account's list`() {
        for (op in listOf(Op.SONG_UNLIKE, Op.SONG_DISLIKE, Op.LIBRARY_REMOVE, Op.PLAYLIST_UNSAVE)) {
            assertTrue("$op pending must veto", AccountPushPolicy.restoreSuppressed(op, 0L, now))
        }
    }

    @Test
    fun `a positive action never vetoes anything`() {
        for (op in listOf(Op.SONG_LIKE, Op.LIBRARY_ADD, Op.PLAYLIST_SAVE)) {
            assertFalse(AccountPushPolicy.restoreSuppressed(op, 0L, now))
        }
    }

    @Test
    fun `a confirmed negative action keeps the veto only for the grace window`() {
        val confirmedAt = now - AccountPushPolicy.CONFIRMED_GRACE_MS + 1
        assertTrue(AccountPushPolicy.restoreSuppressed(Op.SONG_UNLIKE, confirmedAt, now))
        assertFalse(
            "after the grace the account's list is trusted again (the user may re-like on another device)",
            AccountPushPolicy.restoreSuppressed(Op.SONG_UNLIKE, now - AccountPushPolicy.CONFIRMED_GRACE_MS, now),
        )
    }

    @Test
    fun `backoff grows, is capped and never storms`() {
        assertEquals(30_000L, AccountPushPolicy.backoffMs(1))
        assertEquals(60_000L, AccountPushPolicy.backoffMs(2))
        assertTrue(AccountPushPolicy.backoffMs(3) > AccountPushPolicy.backoffMs(2))
        assertEquals(30_000L, AccountPushPolicy.backoffMs(0))
        assertTrue(AccountPushPolicy.backoffMs(500) <= 6L * 60 * 60_000L)
    }

    @Test
    fun `an action is abandoned after repeated rejections or a week`() {
        assertFalse(AccountPushPolicy.shouldGiveUp(0, now, now))
        assertTrue(AccountPushPolicy.shouldGiveUp(AccountPushPolicy.MAX_HARD_FAILURES, now, now))
        assertTrue(AccountPushPolicy.shouldGiveUp(0, now - AccountPushPolicy.MAX_AGE_MS - 1, now))
    }

    @Test
    fun `a rating, a library entry and a playlist never share an outbox slot`() {
        assertEquals(Op.SONG_LIKE.group, Op.SONG_DISLIKE.group)
        assertFalse(Op.SONG_LIKE.group == Op.LIBRARY_ADD.group)
        assertFalse(Op.LIBRARY_ADD.group == Op.PLAYLIST_SAVE.group)
    }

    // ---- wiring: a placebo here would compile and do nothing ----------------------------------

    private val root: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun app(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `entity taps go through the durable outbox, not a fire-and-forget call`() {
        val song = app("db/entities/SongEntity.kt")
        assertTrue(song.contains("AccountPush.submit"))
        assertFalse("YouTube.likeVideo fire-and-forget came back", song.contains("YouTube.likeVideo"))
        assertFalse(song.contains("YouTube.toggleSongLibrary"))
        for (f in listOf("db/entities/AlbumEntity.kt", "db/entities/PlaylistEntity.kt")) {
            val src = app(f)
            assertTrue("$f", src.contains("AccountPush.submit"))
            assertFalse("$f", src.contains("YouTube.likePlaylist"))
        }
        val artist = app("db/entities/ArtistEntity.kt")
        assertTrue(artist.contains("onLiveArtistUnsubscribe"))
    }

    @Test
    fun `the sync honours the veto on every path that could re-mark what the user removed`() {
        val sync = app("utils/SyncUtils.kt")
        assertEquals(
            "insertLikedSongsPage + executeMirrorLikedSongs",
            2, Regex("""suppressesRemoteSongLike\(""").findAll(sync).count(),
        )
        assertTrue(sync.contains("suppressesRemoteLibraryAdd("))
        assertTrue(sync.contains("suppressesRemotePlaylistSave("))
        assertFalse(
            "executeLikeSong must not replay the stale snapshot it was queued with",
            sync.contains("YouTube.likeVideo(s.id, s.liked)"),
        )
    }

    @Test
    fun `No me gusta on a song reaches the account and the endpoint exists`() {
        val service = app("playback/MusicService.kt")
        assertTrue(service.contains("AccountPushPolicy.Op.SONG_DISLIKE"))
        val inner = File(root, "innertube/src/main/kotlin/com/music/innertube/InnerTube.kt").readText()
        assertTrue(inner.contains("httpClient.post(\"like/dislike\")"))
    }
}
