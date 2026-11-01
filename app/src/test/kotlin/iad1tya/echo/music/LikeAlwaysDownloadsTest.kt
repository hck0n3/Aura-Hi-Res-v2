package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 622 (owner 2026-11-01: «cada vez que le dé el corazón a una canción tiene que descargarla, que nunca se te olvide»). A like that
 * toggles a SONG row must go through `syncUtils.likeSong` (which downloads it or adopts its cached copy). This pins every place that
 * flips a song's `liked` flag: a new one that skips it fails here.
 */
class LikeAlwaysDownloadsTest {
    private val root = File("src/main/kotlin/com/music/echo")

    @Test
    fun everySongLikeTogglePathCallsLikeSong() {
        val songToggles = listOf(
            "ui/menu/SelectionSongsMenu.kt", "ui/menu/AddToPlaylistDialogOnline.kt", "ui/menu/SongMenu.kt", "ui/menu/YouTubeSongMenu.kt",
            "ui/menu/QueueMenu.kt", "ui/screens/AlbumScreen.kt", "ui/newui/AuraAlbumScreen.kt", "ui/screens/artist/ArtistItemsScreen.kt",
            "ui/newui/AuraArtistItemsScreen.kt", "ui/newui/AuraCollectionScreens.kt", "ui/newui/AuraLocalPlaylistScreen.kt",
            "ui/screens/playlist/LocalPlaylistScreen.kt", "ui/newui/AuraOnlinePlaylistScreen.kt", "ui/screens/recognition/RecognitionScreen.kt",
        )
        for (f in songToggles) {
            val src = File(root, f).readText()
            assertTrue("$f flips a song like but never calls likeSong", src.contains("likeSong("))
        }
    }

    @Test
    fun theDownloadDecisionLivesInOnePlaceThatHonoursTheCache() {
        val util = File(root, "playback/DownloadUtil.kt").readText()
        assertTrue(util.contains("fun downloadOnLike("))
        assertTrue("must adopt a complete cached copy before downloading", util.contains("ListenCacheAdoption.adopt("))
        val sync = File(root, "utils/SyncUtils.kt").readText()
        assertTrue(sync.contains("downloadUtil.get().downloadOnLike("))
    }

    @Test
    fun aLikeFromTheAccountForASongWeAlreadyHaveGetsADateSoItSortsFirst() {
        val sync = File(root, "utils/SyncUtils.kt").readText()
        assertTrue(sync.contains("likedDate = dbSong.likedDate ?: now.minusSeconds("))
    }
}
