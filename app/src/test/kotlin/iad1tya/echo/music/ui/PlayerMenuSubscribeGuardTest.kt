package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 860 (owner test #5, decision delegated 2026-11-25): the player ⋮ menu offers «Suscribirme / Suscrito» right after
 * «Ver artista», and it is the artist-actions chip's own path — same tap, same state, same chooser, same name confirmation —
 * acting only on a click. The chip itself stays exactly as rows 670/836/859 pin it.
 */
class PlayerMenuSubscribeGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    private val chipChannel = "followChannelIds[PlayerArtistFollow.key(artist)] ?: artist.id?.takeIf { PlayerArtistFollow.isChannelId(it) }"

    /** The menu's subscribe block: from its row comment to the «Ver álbum» row that follows it. */
    private fun menuBlock(menu: String): String {
        val start = menu.indexOf("// Row 860 (owner test #5)")
        assertTrue("the menu has the row 860 entry", start >= 0)
        return menu.substring(start, menu.indexOf("resolvedAlbum?.let { album ->", start))
    }

    @Test
    fun `the menu entry and the chip go through the same follow function`() {
        val player = main("ui/newui/AuraPlayer.kt")
        // The door's tap IS the chip's `followArtistFromPlayer` (resolve, name confirmation, toggleFollowIfStillShown).
        assertTrue(player.contains("val latestFollowArtist by rememberUpdatedState(followArtistFromPlayer)"))
        assertTrue(player.contains("follow = { artist, shownFollowed -> latestFollowArtist(artist, shownFollowed) },"))
        assertEquals("the chip and each chooser row still call it", 2, Regex("""onClick = \{ followArtistFromPlayer\(artist, followed\) \},""").findAll(player).count())
        // Same state source: the door reads the channel exactly as the chips do.
        assertTrue(player.contains("channelIdOf = { artist -> $chipChannel },"))
        assertEquals("the single chip, the several chip, the chooser row and the door read it the same way", 4, player.split(chipChannel).size - 1)
        // Same chooser: several artists open the chip's follow-mode chooser.
        val door = player.substringAfter("val followDoor = remember(mediaMetadata?.id, followChannelIds) {").substringBefore("var showDownloadOrExportDialog")
        assertTrue(door.contains("chooserSongId = songId"))
        assertTrue(door.contains("selectArtistToFollow = true"))
        assertTrue(door.contains("showSelectArtistDialog = true"))

        val menu = main("ui/newui/AuraPlayerMenu.kt")
        val block = menuBlock(menu)
        assertTrue(block.contains("followDoor.follow(followable.single(), followedEach.single())"))
        assertTrue(block.contains("followDoor.chooseArtistToFollow(mediaMetadata.id)"))
        assertTrue(block.contains("rememberPlayerArtistFollowed(database, followDoor.channelIdOf(artist)).value"))
        // No second door to the account from the menu.
        for (direct in listOf("toggleArtistFollow(", "toggleFollowIfStillShown(", "resolvePlayerArtistChannel(", "subscribeChannel(", "toggleLike(")) {
            assertFalse("the menu must not call $direct itself", menu.contains(direct))
        }
    }

    @Test
    fun `the menu shows it right after Ver artista with the chip's texts and hiding rule`() {
        val menu = main("ui/newui/AuraPlayerMenu.kt")
        val viewArtist = menu.indexOf("label = stringResource(R.string.view_artist),")
        val subscribe = menu.indexOf("ActionLog.tap(ActionId.PMENU_SUBSCRIBE)")
        val viewAlbum = menu.indexOf("label = stringResource(R.string.view_album),")
        assertTrue("order: Ver artista, Suscribirme, Ver álbum", viewArtist in 0 until subscribe && subscribe < viewAlbum)
        val block = menuBlock(menu)
        assertTrue(block.contains("PlayerArtistFollow.Several.ALL -> stringResource(R.string.subscribed)"))
        assertTrue(block.contains("R.string.player_artists_some_subscribed,"))
        assertTrue(block.contains("PlayerArtistFollow.Several.NONE -> stringResource(R.string.subscribe)"))
        // Hidden exactly when the chip has nobody to follow, and only for the song still playing (the chooser closes otherwise).
        val rule = ".artists.filter { it.name.isNotBlank() }.distinctBy { it.id to it.name }"
        assertTrue(main("ui/newui/AuraPlayer.kt").contains("val followable = meta$rule"))
        assertTrue(block.contains("val followable = mediaMetadata$rule"))
        assertTrue(block.contains("if (followDoor != null && followable.isNotEmpty() &&"))
        assertTrue(block.contains("followDoor.songId == mediaMetadata.id && playingMetadata?.id == mediaMetadata.id"))
    }

    @Test
    fun `the menu acts only on a click`() {
        val block = menuBlock(main("ui/newui/AuraPlayerMenu.kt"))
        val click = block.indexOf("onClick = {")
        assertTrue(click >= 0)
        assertTrue("the follow call sits inside the row's onClick", block.indexOf("followDoor.follow(") > click)
        assertTrue(block.indexOf("followDoor.chooseArtistToFollow(") > click)
        for (sideEffect in listOf("LaunchedEffect", "SideEffect", "DisposableEffect", "firstTap", "pointerInput", "onLongClick")) {
            assertFalse("no $sideEffect in the subscribe entry", block.contains(sideEffect))
        }
        // The row is a plain `clickable` (a scroll or swipe cancels it), not the lift-anywhere `firstTap` of row 836.
        assertTrue(main("ui/newui/AuraGlassSlab.kt").contains(".auraClickableInternal(onClick = onClick, contentDescription = title)"))
        val clickable = main("ui/newui/AuraContent.kt").substringAfter("internal fun Modifier.auraClickableInternal(").substringBefore("\n}")
        assertTrue(clickable.contains("): Modifier = this.clickable("))
        assertTrue(clickable.contains("onClick = onClick,"))
    }

    @Test
    fun `both doors to the player menu carry the follow door`() {
        val player = main("ui/newui/AuraPlayer.kt")
        val queue = main("ui/newui/AuraQueue.kt")
        val hosts = main("ui/newui/NewUiHosts.kt")
        assertEquals("the queue bar's ⋮ (PlayerMenuHost) and QueueHost", 2, Regex("""followDoor = followDoor,""").findAll(player).count())
        assertTrue("the expanded queue header's ⋮", queue.substringAfter("val openPlayerMenu: () -> Unit = {").substringBefore("\n    }\n").contains("followDoor = followDoor,"))
        assertEquals("AuraPlayerMenu and AuraQueue", 2, Regex("""followDoor = followDoor,""").findAll(hosts).count())
        assertTrue(main("utils/ActionId.kt").contains("PMENU_SUBSCRIBE(\"UI.PMENU.SUBSCRIBE\"),"))
    }
}
