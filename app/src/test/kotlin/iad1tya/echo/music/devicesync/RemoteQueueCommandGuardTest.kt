package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 809 (owner 2026-11-24, H24): «Reproducir a continuación» / «Añadir a la cola» while another device is controlled from
 * here go to THAT device as a `queue` order, which it confirms with `queue_ok`; an older app ignores the order and the sender falls back
 * to the notice of before. The deployed Worker forwards any `cmd` kind unchanged, so it is not touched.
 */
class RemoteQueueCommandGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    private val engine = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt")
    private val service = read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")

    private fun body(src: String, signature: String) = src.substringAfter(signature).substringBefore("\n    }\n")

    @Test
    fun `a tapped edit goes to the controlled device before the old refusal, and automatic fills still stay silent`() {
        for ((sig, next) in listOf("fun playNext(items: List<MediaItem>) {" to "true", "fun addToQueue(items: List<MediaItem>) {" to "false")) {
            val f = body(service, sig)
            val remote = f.indexOf("if (deviceSync?.queueOnRemote(items, insertNext = $next) == true) return")
            assertTrue(sig, remote >= 0)
            assertTrue(sig, remote < f.indexOf("if (refuseQueueEditInRemoteMode()) return"))
        }
        // The automix pool and the automatic fills refuse BEFORE they reach playNext/addToQueue: they never travel.
        assertTrue(body(service, "fun addNextUnheardAutomix() {").contains("if (refuseQueueEditInRemoteMode(notify = false)) return"))
        assertTrue(body(service, "fun playNextAutomix(").contains("if (refuseQueueEditInRemoteMode()) return"))
    }

    @Test
    fun `the sender arms the fallback before sending and only while another device is controlled`() {
        val send = body(engine, "fun queueOnRemote(items: List<MediaItem>, insertNext: Boolean): Boolean {")
        assertTrue(send.contains("val target = RemotePlayback.target.value ?: return false"))
        assertTrue(send.indexOf("delay(RemotePlayback.ANSWER_WAIT_MS)") in 0 until send.indexOf("for ((index, chunk) in chunks.withIndex())"))
        assertTrue(send.contains("R.string.device_sync_queue_edit_not_sent"))
        assertTrue("chunks the size of a take window", send.contains("RemotePlayback.queueChunks(sendable, WINDOW_BEHIND + WINDOW_AHEAD + 1)"))
        assertTrue("each chunk says which order it is and where it goes", send.contains("put(\"q\", order); put(\"i\", index); put(\"of\", chunks.size)"))
        assertTrue("one order at a time on the wire", send.indexOf("scope.launch { synchronized(queueSendLock) {") in 0 until send.indexOf("for ((index, chunk) in chunks.withIndex())"))
        assertTrue(engine.contains("const val CMD_QUEUE = \"queue\""))
        assertTrue(engine.contains("const val CMD_QUEUE_OK = \"queue_ok\""))
        assertTrue("the room's «not connected» cancels the second notice", engine.contains("if (msg.str(\"cmd\") == CMD_QUEUE) queueAckWatch?.cancel()"))
    }

    @Test
    fun `the answer is not an order - it is handled before anything that would let this device start playing`() {
        val run = body(engine, "private suspend fun runCommand(from: String, cmd: String, args: JsonObject) {")
        val ack = run.indexOf("if (cmd == CMD_QUEUE_OK) {")
        assertTrue(ack >= 0)
        assertTrue(ack < run.indexOf("lastOrderHereAt = System.currentTimeMillis()"))
        assertTrue(ack < run.indexOf("RemotePlayback.grantLocalStart()"))
    }

    @Test
    fun `the outcome of a remote queue order reaches the screen - the notices are collected and a confirmation shows the menu label`() {
        val activity = read("app/src/main/kotlin/com/music/echo/MainActivity.kt")
        val collector = activity.substringAfter("iad1tya.echo.music.devicesync.DeviceSyncHub.notices.collect { text ->", "")
        assertTrue("nobody collected the notices: every failure of the order was silent", collector.isNotEmpty())
        assertTrue(collector.substringBefore("}").contains("snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Long)"))
        val run = body(engine, "private suspend fun runCommand(from: String, cmd: String, args: JsonObject) {")
        val ack = run.substringAfter("if (cmd == CMD_QUEUE_OK) {").substringBefore("return")
        assertTrue(ack.contains("iad1tya.echo.music.R.string.play_next else iad1tya.echo.music.R.string.add_to_queue"))
        assertTrue(ack.contains("android.widget.Toast.makeText(appContext, label, android.widget.Toast.LENGTH_SHORT).show()"))
        assertTrue("the answer says which edit it was", run.contains("put(\"n\", items.size); put(\"next\", next)"))
    }

    @Test
    fun `the receiver queues, confirms, and refuses when it is itself a remote or a Listen Together guest`() {
        val run = body(engine, "private suspend fun runCommand(from: String, cmd: String, args: JsonObject) {")
        val branch = run.substringAfter("CMD_QUEUE -> {")
        assertTrue(branch.contains("if (items.isEmpty() || RemotePlayback.active || guest) {"))
        assertTrue("the order is applied whole, never chunk by chunk", branch.contains("val items = queueOrders.offer(\"\$from/\${args.str(\"q\").orEmpty()}\", args.int(\"i\") ?: 0, args.int(\"of\") ?: 1, chunk)"))
        assertTrue(branch.contains("if (items == null) {"))
        assertTrue(branch.contains("onMain { if (next) service.playNext(items) else service.addToQueue(items) }"))
        assertTrue(branch.contains("put(\"to\", from); put(\"cmd\", CMD_QUEUE_OK)"))
    }

    @Test
    fun `the deployed Worker forwards any cmd kind as it is, so it needs no change`() {
        val worker = read("docs/worker/sync/src/index.js")
        val command = worker.substringAfter("  command(ws, me, msg) {").substringBefore("\n  }\n")
        assertTrue(command.contains("const cmd = String(msg.cmd || \"\").slice(0, 24);"))
        assertTrue(command.contains("send(dest, { t: \"cmd\", from: me.id, fromName: me.name, cmd, args: msg.args && typeof msg.args === \"object\" ? msg.args : {} });"))
        assertTrue("queue_ok fits the 24-character cut", "queue_ok".length <= 24)
    }

    @Test
    fun `chunks go in their own order and the receiver applies the order whole, so an idle player starts on song 1`() {
        val songs = (1..5).toList()
        val chunks = RemotePlayback.queueChunks(songs, size = 2)
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), chunks)
        val receiver = RemotePlayback.QueueOrderAssembler<Int>()
        val applied = chunks.mapIndexed { i, c -> receiver.offer("a/1", i, chunks.size, c) }
        assertEquals(listOf(null, null, songs), applied)
        // An empty or IDLE player builds its new queue from what it is given: the whole order, song 1 first.
        assertEquals(1, applied.last()!!.first())
    }

    @Test
    fun `a single-chunk order is applied at once and a broken order is never applied`() {
        val receiver = RemotePlayback.QueueOrderAssembler<Int>()
        assertEquals(listOf(7), receiver.offer("a/1", 0, 1, listOf(7)))
        // An order without q/i/of (one chunk) as well.
        assertEquals(listOf(8), receiver.offer("a/", 0, 1, listOf(8)))
        // A gap: chunk 2 of 3 never came.
        assertEquals(null, receiver.offer("a/2", 0, 3, listOf(1)))
        assertEquals(null, receiver.offer("a/2", 2, 3, listOf(3)))
        // Another order interleaved: the first one is dropped, the new one still completes.
        assertEquals(null, receiver.offer("a/3", 0, 2, listOf(1)))
        assertEquals(null, receiver.offer("b/4", 1, 2, listOf(9)))
        assertEquals(null, receiver.offer("a/3", 1, 2, listOf(2)))
        assertEquals(null, receiver.offer("a/5", 0, 2, listOf(1)))
        assertEquals(listOf(1, 2), receiver.offer("a/5", 1, 2, listOf(2)))
    }
}
