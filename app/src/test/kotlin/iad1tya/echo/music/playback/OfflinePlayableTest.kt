package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 638: the single «plays with zero network» rule the lists use (owner 2026-11-02: offline mode listed songs that did not play). */
class OfflinePlayableTest {

    @Test
    fun theRuleIsTheUnionOfDownloadListenCacheAndExportedFile() {
        val playable = OfflinePlayable.playableIds(setOf("a", "b"), setOf("b", "c"), setOf("d"))
        assertEquals(setOf("a", "b", "c", "d"), playable)
    }

    @Test
    fun onlineNothingIsDimmed() {
        assertFalse(OfflinePlayable.isDimmed("aaaaaaaaaaa", null))
    }

    @Test
    fun offlineASongOutsideTheSetIsDimmedAndOneInsideIsNot() {
        val playable = setOf("aaaaaaaaaaa")
        assertFalse(OfflinePlayable.isDimmed("aaaaaaaaaaa", playable))
        assertTrue(OfflinePlayable.isDimmed("bbbbbbbbbbb", playable))
    }

    @Test
    fun aBlankIdIsNeverJudged() {
        assertFalse(OfflinePlayable.isDimmed("", emptySet()))
    }

    @Test
    fun anEmptySetOfflineDimsEverything() {
        assertTrue(OfflinePlayable.isDimmed("aaaaaaaaaaa", emptySet()))
    }

    @Test
    fun listenKeysMirrorTheResolver() {
        // stream key with a numeric itag → the song
        assertEquals("abcdefghijk", OfflinePlayable.listenKeyOwner("yt-stream-abcdefghijk-251"))
        // a video id that itself contains dashes keeps them; only the numeric itag is stripped
        assertEquals("ab-cd_efgh-i", OfflinePlayable.listenKeyOwner("yt-stream-ab-cd_efgh-i-140"))
        // no itag → the resolver cannot build the replay URI, so it does not count
        assertNull(OfflinePlayable.listenKeyOwner("yt-stream-abcdefghijk"))
        // the companion VIDEO key is not audio
        assertNull(OfflinePlayable.listenKeyOwner("yt-stream-yt-video-abcdefghijk-18"))
        // the legacy mediaId-keyed entry
        assertEquals("abcdefghijk", OfflinePlayable.listenKeyOwner("abcdefghijk"))
        // URLs and other keys are not songs
        assertNull(OfflinePlayable.listenKeyOwner("https://i.ytimg.com/vi/abc/hq.jpg"))
        assertNull(OfflinePlayable.listenKeyOwner("short"))
    }

    @Test
    fun everySongRowThatTheAppDrawsConsultsTheRule() {
        val root = File("src/main/kotlin/com/music/echo")
        for (f in listOf("ui/component/Items.kt", "ui/newui/AuraContent.kt")) {
            val src = File(root, f).readText()
            assertTrue("$f draws song rows without the offline rule", src.contains("OfflinePlayable.isDimmed("))
        }
        val main = File(root, "MainActivity.kt").readText()
        assertTrue("the app root must provide the rule", main.contains("LocalOfflinePlayable provides"))
    }
}
