package iad1tya.echo.music.ui

import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.ui.newui.PlayerArtistFollow
import iad1tya.echo.music.ui.newui.PlayerArtistFollow.Candidate
import iad1tya.echo.music.ui.newui.PlayerArtistFollow.Resolution
import iad1tya.echo.music.ui.newui.PlayerArtistFollow.Several
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 670 (owner 2026-11-22): «Suscribirme / Suscrito» in the player's artist actions. The decisions are pure and pinned
 * here; the wiring (same toggle as the artist page, application scope, first-tap chips, confirmation of a name match) is pinned
 * as source text.
 */
class PlayerArtistFollowTest {
    @Test
    fun `only channel ids can reach the account`() {
        assertTrue(PlayerArtistFollow.isChannelId("UCabc"))
        assertTrue(PlayerArtistFollow.isChannelId("MPLAUCabc"))
        assertFalse(PlayerArtistFollow.isChannelId(null))
        assertFalse(PlayerArtistFollow.isChannelId(""))
        assertFalse("a local row id", PlayerArtistFollow.isChannelId("LAab12cd34"))
        assertFalse(PlayerArtistFollow.isChannelId("LOCAL_artist"))
        assertFalse("an upload artist has no public channel", PlayerArtistFollow.isChannelId("FEmusic_library_privately_owned_artist_x"))
    }

    @Test
    fun `a tap toggles only while the state is the one the user saw`() {
        assertTrue(PlayerArtistFollow.shouldToggle(shownFollowed = false, followedNow = false))
        assertTrue(PlayerArtistFollow.shouldToggle(shownFollowed = true, followedNow = true))
        assertFalse("«Suscribirme» on an artist already followed must not unsubscribe", PlayerArtistFollow.shouldToggle(false, true))
        assertFalse(PlayerArtistFollow.shouldToggle(true, false))
    }

    private fun c(id: String, title: String) = Candidate(id, title, null)

    @Test
    fun `a name resolves to a channel only on an exact match (row 646)`() {
        assertNull("partial word match", PlayerArtistFollow.pickExactChannel("Bad Bun", listOf(c("UCbb", "Bad Bunny"))))
        assertEquals(c("UCx", "Beyonce"), PlayerArtistFollow.pickExactChannel("Beyoncé", listOf(c("UCx", "Beyonce"))))
        assertEquals(
            c("UC2", "MUSE"),
            PlayerArtistFollow.pickExactChannel("Muse", listOf(c("UC1", "Muse Tribute"), c("UC2", "MUSE"), c("UC3", "Muse"))),
        )
        assertNull("not a channel", PlayerArtistFollow.pickExactChannel("Muse", listOf(c("MPREb_x", "Muse"))))
        assertNull(PlayerArtistFollow.pickExactChannel("Muse", emptyList()))
    }

    @Test
    fun `a name too short once normalized is never searched`() {
        assertFalse("«Би-2» normalizes to «2»", PlayerArtistFollow.canResolveByName("Би-2"))
        assertFalse("nothing comparable", PlayerArtistFollow.canResolveByName("宇多田ヒカル"))
        assertFalse(PlayerArtistFollow.canResolveByName("U2"))
        assertTrue("«M.I.A.» is «mia»: searched, but confirmed by the user", PlayerArtistFollow.canResolveByName("M.I.A."))
        assertNull(PlayerArtistFollow.pickExactChannel("Би-2", listOf(c("UCb", "2"))))
        assertEquals(Resolution.NotFound, PlayerArtistFollow.resolveLocally(MediaMetadata.Artist(id = "LAab12cd34", name = "Би-2"), null))
    }

    @Test
    fun `the song's own channel, or one already confirmed, needs no search`() {
        assertEquals(Resolution.Known("UCa"), PlayerArtistFollow.resolveLocally(MediaMetadata.Artist(id = "UCa", name = "A"), null))
        assertEquals(Resolution.Known("UCz"), PlayerArtistFollow.resolveLocally(MediaMetadata.Artist(id = "LAab12cd34", name = "Muse"), "UCz"))
        assertNull("search by name", PlayerArtistFollow.resolveLocally(MediaMetadata.Artist(id = null, name = "Muse"), null))
    }

    @Test
    fun `a failed search is told apart from an artist that does not exist`() {
        assertEquals(Resolution.SearchFailed, PlayerArtistFollow.fromSearch("Muse", Result.failure(IOException("offline"))))
        assertEquals(Resolution.NotFound, PlayerArtistFollow.fromSearch("Muse", Result.success(listOf(c("UC1", "Muse Tribute")))))
        assertEquals(Resolution.ByName(c("UC3", "Muse")), PlayerArtistFollow.fromSearch("Muse", Result.success(listOf(c("UC3", "Muse")))))
    }

    @Test
    fun `the several-artists chip says whether all, some or none are followed`() {
        assertEquals(Several.ALL, PlayerArtistFollow.several(listOf(true, true)))
        assertEquals(Several.SOME, PlayerArtistFollow.several(listOf(true, false)))
        assertEquals(Several.NONE, PlayerArtistFollow.several(listOf(false, false)))
        assertEquals(Several.NONE, PlayerArtistFollow.several(emptyList()))
    }

    @Test
    fun `each artist of the song has its own key`() {
        assertEquals("UCa_A", PlayerArtistFollow.key(MediaMetadata.Artist(id = "UCa", name = "A")))
        assertEquals("null_A", PlayerArtistFollow.key(MediaMetadata.Artist(id = null, name = "A")))
    }

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    @Test
    fun `the player's tap is the artist page's tap and reads only the deliberate follow`() {
        val follow = main("ui/newui/PlayerArtistFollow.kt")
        // Rows 106/107: the shared toggle (toggleLike + confirmArtistUnsubscribed, live call not gated on the upload switch).
        assertTrue(follow.contains("toggleArtistFollow(channelId, ArtistEntity(id = channelId, name = name))"))
        assertTrue(follow.contains("if (PlayerArtistFollow.shouldToggle(shownFollowed, isPlayerArtistFollowed(channelId))) {"))
        // Row 154: state from `followedByUserAt` under every spelling of the channel, never `bookmarkedAt`.
        assertTrue(follow.contains("database.isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(channelId))"))
        assertTrue(follow.contains("isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(channelId)).first()"))
        assertFalse(follow.contains("bookmarkedAt"))
        assertFalse("no second door to the account", follow.contains("subscribeChannel("))
        assertFalse(follow.contains("toggleLike("))
        assertFalse(follow.contains("YtmUploadSyncKey"))
        // Row 646: the fuzzy first-hit resolver of the navigation is not used to subscribe.
        assertFalse(follow.contains("resolveOnlineArtistBrowseId("))
        assertTrue(follow.contains("PlayerArtistFollow.fromSearch("))
    }

    @Test
    fun `the chip and the chooser rows are first-tap safe and the tap outlives the player`() {
        val player = main("ui/newui/AuraPlayer.kt")
        assertTrue(player.contains("context.applicationScope.launch(Dispatchers.Main) {"))
        assertTrue(player.contains("when (val found = resolvePlayerArtistChannel(artist, channelIds[key])) {"))
        assertTrue("one tap at a time per artist", player.contains("if (songId != null && followInFlight.add(key)) {"))
        assertEquals(
            "the single-artist chip and each chooser row",
            2,
            Regex("""onClick = \{ followArtistFromPlayer\(artist, followed\) \},\s*firstTap = true,""").findAll(player).count(),
        )
        assertTrue(player.contains("text = stringResource(if (followed) R.string.subscribed else R.string.subscribe),"))
        assertTrue("several artists open the chooser in follow mode", player.contains("selectArtistToFollow = true"))
        assertTrue(player.contains("if (showSelectArtistDialog && chooserIsCurrent && selectArtistToFollow) {"))
        // The navigation chooser is still there for «Ver artista».
        assertTrue(player.contains("} else if (showSelectArtistDialog && chooserIsCurrent) {"))
        assertTrue("four chips must wrap, not be squeezed out", player.contains("androidx.compose.foundation.layout.FlowRow("))
    }

    @Test
    fun `a name match is confirmed, a failed search blames the network, the several chip tells the state`() {
        val player = main("ui/newui/AuraPlayer.kt")
        // Row 646: nothing is subscribed from a name match until «¿Suscribirte a …?» is accepted.
        assertTrue(player.contains("pendingFollow = PendingPlayerFollow(songId, key, found.channel)"))
        assertTrue(player.contains("stringResource(R.string.player_subscribe_confirm, pending.channel.title)"))
        assertTrue(player.contains("pendingFollow?.takeIf { it.songId == mediaMetadata?.id }?.let { pending ->"))
        assertTrue(player.contains("model = pending.channel.thumbnailUrl,"))
        assertTrue(player.contains("R.string.couldnt_load_artist"))
        assertTrue(player.contains("PlayerArtistFollow.Several.ALL -> stringResource(R.string.subscribed)"))
        assertTrue(player.contains("R.string.player_artists_some_subscribed"))
        assertTrue("several artists: selected only when all are followed", player.contains("selected = several == PlayerArtistFollow.Several.ALL,"))
        // A chooser closes when the song changes under it.
        assertTrue(player.contains("val chooserIsCurrent = chooserSongId != null && chooserSongId == mediaMetadata?.id"))
        assertEquals("artist chooser (navigate and follow) and playlist chooser are tied to their song", 3, Regex("""chooserSongId = meta\.id""").findAll(player).count())
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            val echo = File(root, "app/src/main/res/$dir/echo_strings.xml").readText()
            assertTrue("$dir lacks subscribe", echo.contains("name=\"subscribe\""))
            assertTrue("$dir lacks subscribed", echo.contains("name=\"subscribed\""))
            val strings = File(root, "app/src/main/res/$dir/strings.xml").readText()
            assertTrue("$dir lacks player_subscribe_confirm", strings.contains("name=\"player_subscribe_confirm\""))
            assertTrue("$dir lacks player_artists_some_subscribed", strings.contains("name=\"player_artists_some_subscribed\""))
            assertTrue("$dir lacks couldnt_load_artist", strings.contains("name=\"couldnt_load_artist\""))
        }
        assertTrue(File(root, "app/src/main/res/values/echo_strings.xml").readText().contains("<string name=\"subscribed\">Subscribed</string>"))
    }
}
