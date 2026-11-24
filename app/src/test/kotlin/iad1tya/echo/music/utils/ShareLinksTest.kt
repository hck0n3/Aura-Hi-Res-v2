package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinksTest {

    @Test
    fun songLinkIsDirectYouTubeMusicWatchUrl() {
        assertEquals(
            "https://music.youtube.com/watch?v=_KZEkEb_dvA",
            ShareLinks.song("_KZEkEb_dvA")
        )
    }

    @Test
    fun playlistLinkIsDirectYouTubeMusicPlaylistUrl() {
        assertEquals(
            "https://music.youtube.com/playlist?list=PLabc123",
            ShareLinks.playlist("PLabc123")
        )
    }

    @Test
    fun channelLinkIsDirectYouTubeMusicChannelUrl() {
        assertEquals(
            "https://music.youtube.com/channel/UCxyz",
            ShareLinks.channel("UCxyz")
        )
    }

    // ── Every Share action goes through a *ShareText function (owner 2026-10-20: always song.link) ──

    @Test
    fun songShareIsHeadingPlusSongLinkYouTubeId() {
        assertEquals(
            "Nuestra Canción — Elvis Crespo\nhttps://song.link/y/abc123",
            ShareLinks.songShareText("abc123", "Nuestra Canción", listOf("Elvis Crespo"), songLinkEnabled = true),
        )
    }

    @Test
    fun songShareWithoutTitleIsTheBareLink() {
        assertEquals("https://song.link/y/abc123", ShareLinks.songShareText("abc123", "  ", emptyList(), songLinkEnabled = true))
    }

    @Test
    fun albumShareWrapsTheAlbumPlaylistUrl() {
        assertEquals(
            "Paraíso — Artista A, Artista B\nhttps://song.link/https://music.youtube.com/playlist?list=OLAK5uy_abc",
            ShareLinks.albumShareText("OLAK5uy_abc", "MPREb_x", "Paraíso", listOf("Artista A", "Artista B"), songLinkEnabled = true),
        )
    }

    @Test
    fun albumWithoutPlaylistIdFallsBackToItsBrowseUrlNeverToListNull() {
        assertEquals(
            "Paraíso\nhttps://song.link/https://music.youtube.com/browse/MPREb_x",
            ShareLinks.albumShareText(null, "MPREb_x", "Paraíso", emptyList(), songLinkEnabled = true),
        )
        assertEquals(
            "Paraíso\nhttps://music.youtube.com/browse/MPREb_x",
            ShareLinks.albumShareText("", "MPREb_x", "Paraíso", emptyList(), songLinkEnabled = false),
        )
    }

    @Test
    fun playlistAndArtistSharesAreTheSongLinkWrapperOfTheYouTubeMusicUrl() {
        assertEquals(
            "Mi lista\nhttps://song.link/https://music.youtube.com/playlist?list=PLabc123",
            ShareLinks.playlistShareText("PLabc123", "Mi lista", songLinkEnabled = true),
        )
        assertEquals(
            "Chichi Peralta\nhttps://song.link/https://music.youtube.com/channel/UCxyz",
            ShareLinks.artistShareText("UCxyz", "Chichi Peralta", songLinkEnabled = true),
        )
    }

    @Test
    fun withTheSettingOffEveryKindSendsTheDirectYouTubeMusicLink() {
        assertEquals("T\nhttps://music.youtube.com/watch?v=v1", ShareLinks.songShareText("v1", "T", emptyList(), songLinkEnabled = false))
        assertEquals("P\nhttps://music.youtube.com/playlist?list=PL1", ShareLinks.playlistShareText("PL1", "P", songLinkEnabled = false))
        assertEquals("A\nhttps://music.youtube.com/channel/UC1", ShareLinks.artistShareText("UC1", "A", songLinkEnabled = false))
    }

    @Test
    fun aMissingIdNeverProducesAFabricatedLink() {
        assertEquals("Mi lista", ShareLinks.playlistShareText(null, "Mi lista", songLinkEnabled = true))
        assertEquals("Nombre", ShareLinks.artistShareText("", "Nombre", songLinkEnabled = true))
    }

    @Test
    fun whatAuraSharesRoundTripsThroughTheUnwrapAura() {
        // The receiving Aura must get back exactly the YouTube Music URL it can route natively.
        val artist = ShareLinks.artistShareText("UCxyz", "A", songLinkEnabled = true).substringAfter('\n')
        assertEquals("https://music.youtube.com/channel/UCxyz", ExternalMusicLinks.songLinkWrappedYouTubeUrl(artist))
        val album = ShareLinks.albumShareText("OLAK5uy_abc", null, "B", emptyList(), songLinkEnabled = true).substringAfter('\n')
        assertEquals("https://music.youtube.com/playlist?list=OLAK5uy_abc", ExternalMusicLinks.songLinkWrappedYouTubeUrl(album))
    }

    // ── Registry row 847 (owner 2026-11-24, Odesli point 2): a link anyone opens in their own app, with no Odesli API ──

    @Test
    fun songLinkIsTheSongLinkPageOfTheYouTubeVideoId() {
        // odesli.co/<YouTube Music link> redirects to exactly this page (checked 2026-11-24), which song.link serves without a key.
        assertEquals("https://song.link/y/_KZEkEb_dvA", ShareLinks.songLink("_KZEkEb_dvA"))
    }

    @Test
    fun viaSongLinkWrapsTheYouTubeMusicUrlUnchanged() {
        assertEquals(
            "https://song.link/https://music.youtube.com/playlist?list=OLAK5uy_abc",
            ShareLinks.viaSongLink("https://music.youtube.com/playlist?list=OLAK5uy_abc"),
        )
        assertEquals("https://song.link/https://music.youtube.com/channel/UCxyz", ShareLinks.viaSongLink(ShareLinks.channel("UCxyz")))
    }

    @Test
    fun songLinkSharingIsOnByDefaultAndMakesNoRequest() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val src = File(root, "app/src/main/kotlin/com/music/echo/utils/ShareLinks.kt").readText()
        assertTrue(src.contains("fun useSongLink(): Boolean = PrefsBridge.peek(ShareUseSongLinkKey) ?: true"))
        assertTrue(src.contains("private const val SONG_LINK_BASE = \"https://song.link/y/\""))
        assertFalse(src.contains("api.song.link"))
        assertFalse(src.contains("HttpURLConnection"))
        val settings = File(root, "app/src/main/kotlin/com/music/echo/ui/screens/settings/ContentSettings.kt").readText()
        assertTrue(settings.contains("rememberPreference(key = iad1tya.echo.music.constants.ShareUseSongLinkKey, defaultValue = true)"))
    }
}
