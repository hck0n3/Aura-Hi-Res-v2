package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 731: `app.log` is written in batches instead of one open/append/close per line, and every path that reads
 * the file or ends the process on purpose writes the waiting lines first — so a crash still leaves its last lines.
 */
class AppLoggerBufferTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `the buffer asks for a write after N lines and drains them in order`() {
        val buffer = LogLineBuffer(3)
        assertTrue(buffer.isEmpty())
        assertFalse(buffer.add("a\n"))
        assertFalse(buffer.add("b\n"))
        assertTrue("the Nth line triggers the write", buffer.add("c\n"))
        assertEquals("a\nb\nc\n", buffer.drain())
        assertTrue(buffer.isEmpty())
        assertEquals("", buffer.drain())
        assertFalse("the count restarts after a drain", buffer.add("d\n"))
    }

    @Test
    fun `lines are buffered, not opened one by one`() {
        val logger = main("utils/AppLogger.kt")
        val append = logger.substringAfter("private fun append(context: Context, line: String)").substringBefore("private fun scheduleFlush()")
        assertFalse("append no longer writes the file per line", append.contains("appendText"))
        assertTrue(append.contains("if (pending.add(redacted)) writePending() else scheduleFlush()"))
        assertTrue(logger.contains("private const val FLUSH_EVERY_LINES = 32"))
        assertTrue(logger.contains("private const val FLUSH_DELAY_MS = 2_000L"))
        // one timer per burst: nothing stays scheduled while nothing is logged (AGENTS rule 7)
        assertTrue(logger.contains("if (flushScheduled) return"))
        assertFalse("no periodic flusher", logger.contains("scheduleAtFixedRate") || logger.contains("scheduleWithFixedDelay"))
    }

    @Test
    fun `readers, the crash handler, the restore restart and the background flush first`() {
        val logger = main("utils/AppLogger.kt")
        assertTrue("the Logs screen and the share bundle read after a flush",
            logger.substringAfter("fun readRecentLog(context: Context)").substringBefore("fun readLastCrash").contains("flushBlocking()"))
        assertTrue("«Borrar» drops the waiting lines too", logger.substringAfter("fun clear(context: Context)").contains("pending.drain()"))

        val crash = main("utils/CrashHandler.kt")
        val fatal = crash.substringAfter("val crashLog = buildCrashLog(throwable, thread)")
        assertTrue("the crash line is written before the kill",
            fatal.indexOf("AppLogger.flushBlocking()") in 0 until fatal.indexOf("android.os.Process.killProcess"))
        val fallback = crash.substringAfter("Timber.e(t, \"Error handling crash\")")
        assertTrue("the fallback path flushes before the default handler",
            fallback.indexOf("AppLogger.flushBlocking()") in 0 until fallback.indexOf("defaultHandler?.uncaughtException"))

        val restart = main("viewmodels/BackupRestoreViewModel.kt").substringAfter("private fun restartApp(")
        assertTrue(restart.indexOf("AppLogger.flushBlocking()") in 0 until restart.indexOf("exitProcess(0)"))

        val onStop = main("MainActivity.kt").substringAfter("override fun onStop()").substringBefore("override fun onDestroy()")
        assertTrue("going to the background writes the waiting lines", onStop.contains("AppLogger.flush()"))

        val upload = main("edge/EdgeClient.kt").substringAfter("suspend fun sendDiagnostics(")
        assertTrue("the uploaded log is complete",
            upload.indexOf("AppLogger.flushBlocking()") in 0 until upload.indexOf("AppLogger.logFile(context)"))
    }
}
