package iad1tya.echo.music.ui

import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.ui.newui.PlayerArtistFollow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 670 (owner 2026-11-22): «Suscribirme / Suscrito» in the player's artist actions. The decisions are pure and pinned
 * here; the wiring (same toggle as the artist page, application scope, first-tap chips) is pinned as source text.
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

    @Test
    fun `a name resolves to a channel only on an exact match (row 646)`() {
        assertNull("partial word match", PlayerArtistFollow.pickExactChannel("Bad", listOf("UCbb" to "Bad Bunny")))
        assertEquals("UCx", PlayerArtistFollow.pickExactChannel("Beyoncé", listOf("UCx" to "Beyonce")))
        assertEquals("UC2", PlayerArtistFollow.pickExactChannel("Muse", listOf("UC1" to "Muse Tribute", "UC2" to "MUSE", "UC3" to "Muse")))
        assertNull("not a channel", PlayerArtistFollow.pickExactChannel("Muse", listOf("MPREb_x" to "Muse")))
        assertNull("a name with nothing comparable never matches", PlayerArtistFollow.pickExactChannel("宇多田ヒカル", listOf("UCu" to "宇多田ヒカル")))
        assertNull(PlayerArtistFollow.pickExactChannel("Muse", emptyList()))
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
        assertTrue(follow.contains("toggleArtistFollow(channelId, ArtistEntity(id = channelId, name = artist.name))"))
        assertTrue(follow.contains("if (PlayerArtistFollow.shouldToggle(shownFollowed, followedNow)) {"))
        // Row 154: state from `followedByUserAt` under every spelling of the channel, never `bookmarkedAt`.
        assertTrue(follow.contains("database.isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(channelId))"))
        assertTrue(follow.contains("isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(channelId)).first()"))
        assertFalse(follow.contains("bookmarkedAt"))
        assertFalse("no second door to the account", follow.contains("subscribeChannel("))
        assertFalse(follow.contains("toggleLike("))
        assertFalse(follow.contains("YtmUploadSyncKey"))
        // Row 646: the fuzzy first-hit resolver of the navigation is not used to subscribe.
        assertFalse(follow.contains("resolveOnlineArtistBrowseId("))
        assertTrue(follow.contains("PlayerArtistFollow.pickExactChannel("))
    }

    @Test
    fun `the chip and the chooser rows are first-tap safe and the tap outlives the player`() {
        val player = main("ui/newui/AuraPlayer.kt")
        assertTrue(player.contains("context.applicationScope.launch(Dispatchers.Main) {"))
        assertTrue(player.contains("database.followArtistFromPlayer(artist, followChannelIds[key], shownFollowed)"))
        assertTrue("one tap at a time per artist", player.contains("if (followInFlight.add(key)) {"))
        assertEquals(
            "the single-artist chip and each chooser row",
            2,
            Regex("""onClick = \{ followArtistFromPlayer\(artist, followed\) \},\s*firstTap = true,""").findAll(player).count(),
        )
        assertTrue(player.contains("text = stringResource(if (followed) R.string.subscribed else R.string.subscribe),"))
        assertTrue("several artists open the chooser in follow mode", player.contains("selectArtistToFollow = true"))
        assertTrue(player.contains("if (showSelectArtistDialog && selectArtistToFollow) {"))
        // The navigation chooser is still there for «Ver artista».
        assertTrue(player.contains("} else if (showSelectArtistDialog) {"))
        assertTrue("four chips must wrap, not be squeezed out", player.contains("androidx.compose.foundation.layout.FlowRow("))
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            val strings = File(root, "app/src/main/res/$dir/echo_strings.xml").readText()
            assertTrue("$dir lacks subscribe", strings.contains("name=\"subscribe\""))
            assertTrue("$dir lacks subscribed", strings.contains("name=\"subscribed\""))
        }
    }
}
