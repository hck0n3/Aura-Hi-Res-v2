package iad1tya.echo.music.localmedia

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 881 (plan, phase 4: «recorrer de verdad la carpeta elegida»): until now a picked folder was only a MediaStore path
 * filter, so its audio that MediaStore does not index (a `.nomedia` folder, a card it skipped) never appeared. The picked
 * SAF tree is now walked; a file MediaStore already lists is left to MediaStore, and a folder that could not be walked
 * completely never deletes the songs that came from it.
 */
class PickedFoldersTest {

    @Test
    fun `a SAF document and its MediaStore row give the same key`() {
        val saf = safDocumentKey(ExternalStorageDocumentsAuthority, "primary:Music/Rock/Song.mp3")
        assertEquals("primary/music/rock/song.mp3", saf)
        assertEquals(saf, mediaStoreDocumentKey("external_primary", "Music/Rock/", "Song.mp3", null))
        assertEquals(saf, mediaStoreDocumentKey(null, null, null, "/storage/emulated/0/Music/Rock/Song.mp3"))
        val card = safDocumentKey(ExternalStorageDocumentsAuthority, "1A2B-3C4D:Music/a.flac")
        assertEquals("1a2b-3c4d/music/a.flac", card)
        assertEquals(card, mediaStoreDocumentKey("1a2b-3c4d", "Music/", "a.flac", null))
        assertEquals(card, mediaStoreDocumentKey(null, null, null, "/storage/1A2B-3C4D/Music/a.flac"))
    }

    @Test
    fun `other providers and unreadable rows never claim a file`() {
        assertNull(safDocumentKey("com.android.providers.downloads.documents", "raw:/storage/emulated/0/Download/a.mp3"))
        assertNull(safDocumentKey(ExternalStorageDocumentsAuthority, "primary:"))
        assertNull(safDocumentKey(ExternalStorageDocumentsAuthority, "noroot"))
        assertNull(mediaStoreDocumentKey(null, null, null, null))
        assertNull(mediaStoreDocumentKey(null, null, null, "/sdcard/Music/a.mp3"))
        assertNull(mediaStoreDocumentKey(null, null, null, "/storage/emulated/0"))
    }

    @Test
    fun `songs under a folder that was not walked completely are kept, the rest go as before`() {
        val prefix = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/"
        val split = listOf("${prefix}primary%3AMusic%2Fa.mp3", "content://media/external/audio/media/9") to listOf("sd-song")
        val (removable, kept) = split.keepingUnreadFolders(setOf(prefix))
        assertEquals(listOf("content://media/external/audio/media/9"), removable)
        assertEquals(listOf("sd-song", "${prefix}primary%3AMusic%2Fa.mp3"), kept)
        assertEquals(split, split.keepingUnreadFolders(emptySet()))
    }

    @Test
    fun `a folder picked inside another picked folder is walked only as itself, and a folder picked twice once`() {
        val music = PickedTreeRoot("content://a/tree/primary%3AMusic", ExternalStorageDocumentsAuthority, "primary:Music")
        val rock = PickedTreeRoot("content://a/tree/primary%3AMusic%2FRock", ExternalStorageDocumentsAuthority, "primary:Music/Rock")
        val musicAgain = PickedTreeRoot("content://a/tree/primary%3AMusic?x", ExternalStorageDocumentsAuthority, "primary:Music")
        val walked = pickedTreesToWalk(listOf(rock, musicAgain, music))
        assertEquals(listOf(music, rock), walked)
        assertEquals("the walk of «Music» never enters «Music/Rock»", setOf("primary:Music/Rock"), otherPickedRoots(music, walked))
        assertEquals(setOf("primary:Music"), otherPickedRoots(rock, walked))
        val drive = PickedTreeRoot("content://d/tree/x", "com.example.docs", "primary:Music/Rock")
        assertEquals("another provider's ids are its own", emptySet<String>(), otherPickedRoots(drive, walked + drive))
    }

    @Test
    fun `hidden and trashed files are never imported`() {
        assertTrue(isHiddenDocumentName(".trashed-1700000000-song.mp3"))
        assertTrue(isHiddenDocumentName(".hidden.flac"))
        assertFalse(isHiddenDocumentName("song.mp3"))
    }

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `the walk is bounded, cancellable and reports a failed or empty folder as unread`() {
        val walker = text("app/src/main/kotlin/com/music/echo/localmedia/PickedFolderWalker.kt")
        assertTrue(walker.contains("job?.ensureActive()"))
        assertTrue("overlapping picks: each file once, with one id", walker.contains("if (documentId in otherRoots) continue"))
        assertTrue(walker.contains("val picked = pickedTreesToWalk("))
        assertTrue(walker.contains("if (SystemClock.elapsedRealtime() > deadlineElapsedMs || entries >= MaxEntriesPerScan) {"))
        assertTrue(walker.contains("if (depth + 1 <= MaxDepth) pending.add(documentId to depth + 1) else complete = false"))
        assertTrue("a folder that cannot be listed is unread, never empty", walker.contains("if (children == null) {\n                    complete = false"))
        assertTrue(walker.contains("if (!complete || found.isEmpty()) unread += prefix"))
        assertTrue("MediaStore's files stay MediaStore's", walker.contains("if (safDocumentKey(tree.authority, documentId)?.let(indexedKeys::contains) == true) continue"))
        assertTrue("audio only", walker.contains("if (mime?.startsWith(\"video/\") == true || !SupportedLocalAudio.isSupported(name, mime)) continue"))
    }

    @Test
    fun `the scan keeps what an unread folder holds and never walks without MediaStore's list`() {
        val scanner = text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt")
        val transaction = scanner.substringAfter("database.withTransaction {").substringBefore("pruneLocalAlbums()")
        val split = transaction.indexOf("splitMissingByVolume(removedIds, recordedVolumes, volumePresent)")
        val keep = transaction.indexOf(".keepingUnreadFolders(snapshot.unreadFolderPrefixes)")
        assertTrue("applied to the guarded split, before any delete", keep > split && keep < transaction.indexOf("removable.chunked(SqlBatchSize).forEach(::deleteSongsByIds)"))
        val picked = scanner.substringAfter("private fun pickedFolderTracks(").substringBefore("private fun mediaStoreIndexedKeys(")
        assertTrue(picked.contains("?: return PickedFolderTracks(unreadPrefixes = trees.mapNotNullTo(HashSet(), walker::folderPrefix))"))
        assertTrue("an unchanged walked file is not reopened", picked.indexOf("isUnchangedFile(it.fingerprint, fingerprint)") in 0 until picked.indexOf("extractId3Metadata("))
        val floor = picked.indexOf("if (cached != null && cached.record.durationSeconds * 1000L < minimumDurationMs) {")
        assertTrue("a remembered build obeys today's minimum duration", floor in 0 until picked.indexOf("tracks += cached.record"))
        assertTrue("what does not fit in the time keeps its folder", picked.contains("unread += file.folderPrefix"))
        assertTrue(picked.contains("job?.ensureActive()"))
        assertTrue(scanner.contains("const val PickedFolderBudgetMs = 60_000L"))
    }

    @Test
    fun `a picked folder keeps its read permission and leaves with its chip`() {
        val screen = text("app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt")
        assertTrue(screen.contains("context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)"))
        assertTrue(screen.contains("}.onSuccess { onIncludedTreeUrisChange(includedTreeUris + uri.toString()) }"))
        assertTrue(screen.contains("includedTreeUris.filterTo(HashSet()) { tree -> Uri.parse(tree).toScanFolderEntry()?.lowercase(Locale.ROOT) in kept },"))
        assertTrue(screen.contains("includedTreeUris = includedTreeUris,"))
        val auto = text("app/src/main/kotlin/com/music/echo/localmedia/LocalLibraryAutoScan.kt")
        assertTrue(auto.contains("includedTreeUris = prefs[LocalSongsIncludedTreeUrisKey].orEmpty(),"))
        assertTrue(text("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
            .contains("val LocalSongsIncludedTreeUrisKey = stringSetPreferencesKey(\"local_songs_included_tree_uris\")"))
    }
}
