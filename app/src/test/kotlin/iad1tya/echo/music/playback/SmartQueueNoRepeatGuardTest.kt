package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 645 — the smart queue never brings back a song already heard or queued (owner 2026-11-22). Source-text
 * checks on purpose: these paths need a real player, YouTube's radio and a process restart, none of which runs in a
 * JVM test.
 */
class SmartQueueNoRepeatGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    private val service by lazy { main("playback/MusicService.kt") }

    /** The member body that starts at [signature], up to its closing brace at class-member indentation. */
    private fun body(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("signature not found: $signature", start >= 0)
        val end = src.indexOf("\n    }\n", start)
        assertTrue("end of body not found: $signature", end > start)
        return src.substring(start, end)
    }

    /** (a) The player's automatic top-up appends the first unheard automix item, not automix[0] as is. */
    @Test
    fun `the automix top-up skips what was already heard or queued`() {
        val player = main("ui/newui/AuraPlayer.kt")
        assertTrue(player.contains("playerConnection.service.addNextUnheardAutomix()"))
        assertFalse(player.contains("addToQueueAutomix(automix[0], 0)"))

        val topUp = body(service, "fun addNextUnheardAutomix() {")
        assertTrue(topUp.contains("val index = items.indexOfFirst { !it.isHeardForSmartQueue(queued) }"))
        assertTrue(topUp.contains("if (index >= 0) addToQueueAutomix(items[index], index)"))

        val heard = body(service, "internal fun MediaItem.isHeardForSmartQueue(queued: LiveQueueSnapshot?): Boolean {")
        assertTrue(heard.contains("id in sessionPlayedIds || id in persistedNoRepeatIds || cachedPlayedIds?.contains(id) == true ||"))
        assertTrue(heard.contains("cachedEverPlayedIds?.contains(id) == true || queued?.ids?.contains(id) == true"))
        assertTrue(heard.contains("return key in sessionPlayedDedupKeys || key in persistedNoRepeatKeys || queued?.keys?.contains(key) == true"))
    }

    /** (a) The top-up runs on the player thread and only reads the history caches: they are loaded at service start. */
    @Test
    fun `the play history caches and the saved memory load when the service starts`() {
        assertTrue(service.contains("scope.launch(Dispatchers.IO) {\n            loadPersistedNoRepeat()\n            recentlyPlayedIds()\n            everPlayedIds()\n            everPlayedKeys()\n        }"))
    }

    /** Row 650: the whole play history counts, by id AND by title+artist, in the strict path and in the gate's fill. */
    @Test
    fun `the smart queue never brings back anything in the whole play history`() {
        assertTrue(service.contains("private const val NO_REPEAT_HISTORY_LIMIT = Int.MAX_VALUE"))
        val keys = body(service, "private suspend fun everPlayedKeys(): Set<String> {")
        assertTrue(keys.contains("database.recentPlayedTitleArtists(NO_REPEAT_HISTORY_LIMIT)"))
        assertTrue(keys.contains("NoRepeatKey.of(it.title, it.artist)"))
        assertTrue("cached like everPlayedIds", keys.contains("now - cachedEverPlayedKeysAt < 5 * 60_000L"))

        assertTrue(service.contains("val playedKeyHistory = if (strictNoRepeat) everPlayedKeys() else emptySet()"))
        assertTrue(service.contains("(strictNoRepeat && dedupKey in playedKeyHistory) ||"))
        val heard = body(service, "internal fun MediaItem.isHeardForSmartQueue(queued: LiveQueueSnapshot?): Boolean {")
        assertTrue(heard.contains("cachedEverPlayedKeys?.contains(key) == true"))

        val gate = main("playback/ExactStyleGate.kt")
        assertTrue("the fill drops what the strict no-repeat would drop", gate.contains("!mi.isHeardForSmartQueue(null) &&"))
        assertTrue(gate.contains("all[i].mediaId !in heardInBatch && ContextLane.tier(lane, judged[i].candidate) == 0"))
        assertTrue("only the searching (smart-queue) callers", gate.contains("if (allowSearch) items.filter { it.isHeardForSmartQueue(null) }"))
        // Row 655: heard songs never count toward `enough` nor the widening cut (else the batch empties and the queue relaxes).
        assertTrue(gate.contains("val tiers = judged.mapIndexed { i, j -> if (all[i].mediaId in heardInBatch) null else ContextLane.tier(lane, j.candidate) }"))
    }

    /** Row 655: the unbounded history is built off the main thread and never copied there. */
    @Test
    fun `the whole history is mapped on IO and not copied on the caller's thread`() {
        val keys = body(service, "private suspend fun everPlayedKeys(): Set<String> {")
        assertTrue(keys.contains("withContext(Dispatchers.IO) {\n                database.recentPlayedTitleArtists(NO_REPEAT_HISTORY_LIMIT).mapTo(HashSet<String>()) { NoRepeatKey.of(it.title, it.artist) }\n            }"))
        val ids = body(service, "private suspend fun everPlayedIds(): Set<String> {")
        assertTrue(ids.contains("withContext(Dispatchers.IO) { database.recentPlayedSongIds(NO_REPEAT_HISTORY_LIMIT).toHashSet() }"))
        assertFalse(service.contains("recentlyPlayedIds() + everPlayedIds()"))
        assertTrue(service.contains("val everPlayed = if (strictNoRepeat) everPlayedIds() else emptySet()"))
        assertTrue(service.contains("m.id in playedHistory || m.id in everPlayed ||"))
    }

    /** Row 750: a song the strict no-repeat drops never takes one of the AI second opinion's slots (EdgeJudge.MAX_CANDIDATES). */
    @Test
    fun `the AI second opinion is only asked about songs that can still play`() {
        val gate = main("playback/ExactStyleGate.kt")
        val doubtful = gate.substringAfter("val doubtful = judged.indices.filter { i ->").substringBefore("}.take(iad1tya.echo.music.edge.EdgeJudge.MAX_CANDIDATES)")
        assertTrue(doubtful.contains("all[i].mediaId !in heardInBatch && step != null && step >= 1"))
        assertTrue("heardInBatch is declared before the AI pass", gate.indexOf("val heardInBatch: Set<String> =") in 0 until gate.indexOf("val doubtful = judged.indices.filter { i ->"))
    }

    /** Row 655: when every source is exhausted the user is told once per queue (online), never a silent stop. */
    @Test
    fun `an exhausted smart queue tells the user once instead of stopping silently`() {
        val engine = main("playback/RadioContinuationEngine.kt")
        assertTrue(engine.contains("isNetworkConnected.value && queueGeneration == seedGeneration && smartQueueEndNoticeGeneration != seedGeneration"))
        assertTrue(engine.contains("smartQueueEndNoticeGeneration = seedGeneration"))
        assertTrue(engine.contains("R.string.smart_queue_no_new_songs"))
        for (dir in listOf("values", "values-es")) {
            val f = File(root, "app/src/main/res/$dir/smart_queue_end_strings.xml")
            assertTrue("$dir string missing", f.isFile && f.readText().contains("name=\"smart_queue_no_new_songs\""))
        }
    }

    /** (b) Songs queued by hand, by Listen Together / an empty-player playNext, or by Android Auto enter the memory. */
    @Test
    fun `every way a song enters the queue records it for the smart queue`() {
        val remember = body(service, "private fun rememberQueuedItems(items: List<MediaItem>) {")
        assertTrue(remember.contains("sessionPlayedIds.addAll(items.mapNotNull { it.mediaId.takeIf(String::isNotEmpty) })"))
        assertTrue(remember.contains("sessionPlayedDedupKeys.addAll(items.mapNotNull { it.dedupKeyOrNull() })"))

        assertTrue(body(service, "fun playNext(items: List<MediaItem>) {").contains("rememberQueuedItems(items)"))
        assertTrue(body(service, "fun addToQueue(items: List<MediaItem>) {").contains("rememberQueuedItems(items)"))
        assertTrue(body(service, "fun adoptDirectQueue(").contains("rememberQueuedItems(items)"))
        assertTrue(
            "Android Auto queue landing",
            service.contains("rememberQueuedItems((0 until player.mediaItemCount).mapNotNull { i -> runCatching { player.getMediaItemAt(i) }.getOrNull() })"),
        )
        assertTrue(
            "the song a crossfade swap advances into",
            service.contains("player.currentMediaItem?.let { rememberRecentRadioId(it.mediaId, it.dedupKeyOrNull()) }"),
        )
    }

    /** (c) Only a tapped song's own radio is filtered, decided when the queue is built; a list the user chose is not. */
    @Test
    fun `a tapped song's radio drops what was heard, and only that radio`() {
        val queue = main("playback/queues/YouTubeQueue.kt")
        assertTrue(queue.contains("val isSongRadio: Boolean = endpoint.videoId != null &&"))
        assertTrue(queue.contains("endpoint.playlistId.let { it.isNullOrBlank() || it.startsWith(\"RDAMVM\") }"))

        assertTrue(service.contains("queue is YouTubeQueue && queue.isSongRadio && laneStatus.items.size > 1"))
        assertTrue(service.contains("val kept = tail.filterNot { it.isHeardForSmartQueue(null) }"))
        // The review rejected the earlier, wider checks (any RD list, or "has a preload item").
        assertFalse(service.contains("queue.metricsPlaylistId?.let { !it.startsWith(\"RD\") } != true"))
        assertFalse(service.contains("queue is YouTubeQueue && queue.preloadItem != null && laneStatus.items.size > 1"))
    }

    /** (c) A radio emptied by the filter seeds the continuation at once instead of stopping at STATE_ENDED. */
    @Test
    fun `an emptied radio seeds the continuation right away`() {
        assertTrue(service.contains("if (kept.isEmpty() && tail.isNotEmpty()) heardFilterEmptiedGeneration = myGeneration"))
        assertTrue(service.contains("val heardFilterEmptied = heardFilterEmptiedGeneration == generation"))
        assertTrue(service.contains("(!currentQueue.hasNextPage() || heardFilterEmptied)"))
    }

    /** (d) The memory survives a restart: own file, bounded, saved always (not behind «Cola persistente»), race-free. */
    @Test
    fun `the no-repeat memory is saved on its own and never overwritten by an older copy`() {
        assertTrue(service.contains("const val PERSISTENT_NO_REPEAT_FILE = \"persistent_no_repeat.data\""))
        assertTrue(service.contains("const val PERSISTED_NO_REPEAT_LIMIT = 4000"))

        val save = body(service, "private fun saveNoRepeatMemory(synchronous: Boolean = false) {")
        assertTrue(save.contains("if (version == noRepeatSavedVersion) return"))
        assertTrue(save.contains("synchronized(persistFileWriteLock) {\n                    val current = noRepeatVersion.get()"))
        assertTrue(save.contains("if (current == noRepeatSavedVersion) return@synchronized"))
        assertFalse("the queue save is gated on «Cola persistente»", save.contains("PersistentQueueKey"))

        // 30 s tick, shutdown/reboot and onDestroy: each save comes BEFORE the «Cola persistente» gate.
        assertTrue(service.contains("saveNoRepeatMemory()\n                if (dataStore.getNonBlocking(PersistentQueueKey, true) && queueDirty) {"))
        assertTrue(service.contains("runCatching { saveNoRepeatMemory(synchronous = true) }\n                    if (!dataStore.getNonBlocking(PersistentQueueKey, true)) return"))
        assertTrue(service.contains("runCatching { saveNoRepeatMemory(synchronous = true) }\n        if (dataStore.getNonBlocking(PersistentQueueKey, true)) {"))
        assertFalse("the memory is not written inside the queue save", body(service, "private fun saveQueueToDisk(").contains("PERSISTENT_NO_REPEAT_FILE"))
    }

    /** (d) Only the smart queue consults the saved memory; pages of a list the user chose do not. */
    @Test
    fun `the saved memory is consulted only by the strict smart-queue path`() {
        assertTrue(service.contains("(strictNoRepeat && (m.id in persistedNoRepeatIds || dedupKey in persistedNoRepeatKeys))"))
    }
}
