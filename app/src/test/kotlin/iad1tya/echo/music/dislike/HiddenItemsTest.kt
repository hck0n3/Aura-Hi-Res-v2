package iad1tya.echo.music.dislike

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 811 (owner H6a, 2026-11-24): the «Ocultos» screen. */
class HiddenItemsTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `every set of the store becomes rows, grouped in a fixed order and sorted inside each group`() {
        val d = DislikeStore.Disliked(
            songs = setOf("s2", "s1"),
            artists = setOf("UCa"),
            albums = setOf("MPREb1"),
            playlists = setOf("PL1"),
            softSongs = setOf("s3"),
            softArtists = setOf("some artist"),
        )
        val rows = HiddenItems.rows(d)
        assertEquals(
            listOf(
                HiddenItems.Row(HiddenItems.Kind.SONG, "s1"),
                HiddenItems.Row(HiddenItems.Kind.SONG, "s2"),
                HiddenItems.Row(HiddenItems.Kind.ARTIST, "UCa"),
                HiddenItems.Row(HiddenItems.Kind.ALBUM, "MPREb1"),
                HiddenItems.Row(HiddenItems.Kind.PLAYLIST, "PL1"),
                HiddenItems.Row(HiddenItems.Kind.SOFT_SONG, "s3"),
                HiddenItems.Row(HiddenItems.Kind.SOFT_ARTIST, "some artist"),
            ),
            rows,
        )
    }

    @Test
    fun `the same id hidden two ways is two rows, each undone on its own`() {
        val rows = HiddenItems.rows(DislikeStore.Disliked(songs = setOf("x"), softSongs = setOf("x")))
        assertEquals(2, rows.size)
        assertTrue(HiddenItems.clearsAccountDislike(rows[0]))
        assertFalse("«Menos de esto» never touched the account", HiddenItems.clearsAccountDislike(rows[1]))
    }

    @Test
    fun `an empty store has nothing to show`() {
        assertTrue(HiddenItems.rows(DislikeStore.Disliked()).isEmpty())
    }

    @Test
    fun `each row is reversed by its own store call`() {
        val s = main("dislike/HiddenItems.kt")
        assertTrue(s.contains("Kind.SONG -> store.undislikeSong(row.id)"))
        assertTrue(s.contains("Kind.ARTIST -> store.undislikeArtist(row.id)"))
        assertTrue(s.contains("Kind.ALBUM -> store.undislikeAlbum(row.id)"))
        assertTrue(s.contains("Kind.PLAYLIST -> store.undislikePlaylist(row.id)"))
        assertTrue(s.contains("Kind.SOFT_SONG -> store.undoSoftDislikeSong(row.id)"))
        assertTrue(s.contains("Kind.SOFT_ARTIST -> store.undoSoftDislikeArtist(row.id)"))
    }

    @Test
    fun `the screen is reachable, indexed, follows the store and logs nothing`() {
        val screen = main("ui/screens/settings/HiddenItemsScreen.kt")
        assertTrue(screen.contains("store.disliked.collectAsState(initial = null)"))
        assertTrue(screen.contains("HiddenItems.undo(store, row)"))
        assertTrue(screen.contains("iad1tya.echo.music.utils.AccountPushPolicy.Op.SONG_UNLIKE,"))
        // Names from the local database, the id when it has none.
        assertTrue(screen.contains("title = { Text(text = name ?: row.id) },"))
        // AGENTS.md rule 4: titles, artists and list names never reach a log.
        assertFalse(screen.contains("Timber"))
        assertFalse(screen.contains("AppLogger"))
        assertFalse(screen.contains("Log."))

        assertTrue(main("ui/screens/NavigationBuilder.kt").contains("composable(\"settings/content/hidden\")"))
        assertTrue(main("ui/screens/settings/ContentSettings.kt").contains("onClick = { navController.navigate(\"settings/content/hidden\") }"))
        val index = main("ui/screens/settings/SearchableSettings.kt")
        assertTrue(index.contains("Triple(stringResource(R.string.hidden_items_title), \"Contenido\", \"settings/content\"),"))
        assertTrue(index.contains("Triple(stringResource(R.string.hidden_items_desc), \"Contenido\", \"settings/content\"),"))
    }
}
