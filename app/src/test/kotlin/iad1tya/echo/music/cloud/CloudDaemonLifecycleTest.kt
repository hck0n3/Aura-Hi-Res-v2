package iad1tya.echo.music.cloud

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 888: the rclone daemon runs only while a cloud action needs it, on loopback, behind random credentials
 * that never appear in argv or in the log, and stops when idle. The process side has no Android SDK in the unit job,
 * so it is pinned on the source text; the policies are pure.
 */
class CloudDaemonLifecycleTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private val mainDir = File(root, "app/src/main/kotlin/com/music/echo")
    private val daemon by lazy { File(mainDir, "cloud/RcloneDaemon.kt").readText() }

    @Test
    fun `it stops only when nobody uses it and the idle time has passed`() {
        val idle = RcloneDaemon.IDLE_STOP_MS
        assertTrue("a minute or so, not hours", idle in 30_000L..300_000L)
        assertFalse("in use", RcloneProcessPolicy.shouldStop(1, 0L, 10 * idle, idle))
        assertFalse("released a moment ago", RcloneProcessPolicy.shouldStop(0, 1_000L, 1_000L + idle - 1, idle))
        assertTrue(RcloneProcessPolicy.shouldStop(0, 1_000L, 1_000L + idle, idle))
        // The delayed stop re-checks the policy under the lock (a use that came in meanwhile wins), and each acquire
        // cancels the pending stop: one delayed job, no polling loop.
        val release = daemon.substringAfter("private suspend fun release() = mutex.withLock {").substringBefore("\n    private suspend fun start(")
        assertTrue(release.contains("delay(IDLE_STOP_MS)"))
        assertTrue(release.contains("if (RcloneProcessPolicy.shouldStop(activeUses, lastReleaseAtMs, System.currentTimeMillis(), IDLE_STOP_MS)) {"))
        assertTrue(daemon.contains("idleJob?.cancel()\n            idleJob = null\n            val live = running?.takeIf { it.process.isAlive } ?: start(context).also { running = it }"))
        assertTrue("released even when the caller is cancelled", daemon.contains("withContext(NonCancellable) { release() }"))
        assertFalse(daemon.contains("while (true)"))
        assertFalse(daemon.contains("withFrameNanos"))
    }

    @Test
    fun `a caller cancelled while the daemon starts leaks neither the process nor a use`() {
        // Leaving «Nube» during the 1-2 s start used to leave `rcd` running untracked (delay threw after spawn), or
        // counted a use that withContext then dropped, so activeUses never reached 0 and the daemon never idled out.
        val use = daemon.substringAfter("suspend fun <T> use(").substringBefore("\n    private suspend fun release(")
        assertTrue(use.contains("val lease = Lease()"))
        assertTrue(use.contains("return block(acquire(context.applicationContext, lease))"))
        assertTrue("released iff counted", use.contains("if (lease.held) withContext(NonCancellable) { release() }"))
        assertTrue("counted and marked together under the lock", use.contains("activeUses++\n            lease.held = true\n            live.client"))
        val start = daemon.substringAfter("private suspend fun start(context: Context): Running {").substringBefore("\n    private suspend fun waitHealthy(")
        val spawn = start.indexOf("val process = builder.start()")
        val guarded = start.indexOf("try {", spawn)
        assertTrue("everything after the spawn is guarded", spawn >= 0 && guarded > spawn && guarded < start.indexOf("readFirstLine(process.inputStream)"))
        assertTrue(start.contains("started = true\n                    return Running(process, pid, client, output)"))
        assertTrue(
            "killed on any exit but success, cancellation included",
            start.contains("} finally {") && start.contains("if (!started) {\n                    process.destroyForcibly()\n                    pidFile(context).delete()"),
        )
    }

    @Test
    fun `the command line binds loopback and carries no credential`() {
        val cmd = RcloneProcessPolicy.command("/data/app/x/lib/arm64/librclone.so", 40123, "/data/c/rclone.conf", "/data/cache/rclone")
        assertEquals(listOf("/system/bin/sh", "-c", "echo \$\$; exec \"\$0\" \"\$@\""), cmd.take(3))
        assertEquals("/data/app/x/lib/arm64/librclone.so", cmd[3])
        assertEquals("rcd", cmd[4])
        assertTrue(cmd.contains("--rc-addr=127.0.0.1:40123"))
        assertTrue(cmd.contains("--ask-password=false"))
        for (arg in cmd) {
            for (forbidden in listOf("--rc-no-auth", "--rc-serve", "--rc-web-gui", "--rc-user", "--rc-pass", "--rc-allow-origin", "--rc-files", "0.0.0.0", "localhost")) {
                assertFalse("$forbidden in argv", arg.contains(forbidden))
            }
        }
        assertEquals("RCLONE_RC_USER", RcloneProcessPolicy.ENV_USER)
        assertEquals("RCLONE_RC_PASS", RcloneProcessPolicy.ENV_PASS)
        assertEquals("RCLONE_CONFIG_PASS", RcloneProcessPolicy.ENV_CONFIG_PASS)
        // Random per start, in the environment, after the inherited RCLONE_ variables are cleared.
        val start = daemon.substringAfter("private suspend fun start(context: Context): Running {").substringBefore("\n    private suspend fun waitHealthy(")
        assertTrue(start.contains("val user = randomToken(16)"))
        assertTrue(start.contains("val pass = randomToken(32)"))
        assertTrue(start.indexOf("RcloneEnvironment.apply(context, env)") < start.indexOf("env[RcloneProcessPolicy.ENV_USER] = user"))
        assertTrue(start.contains("env[RcloneProcessPolicy.ENV_PASS] = pass"))
        assertTrue(start.contains("env[RcloneProcessPolicy.ENV_CONFIG_PASS] = prepared.password"))
        assertTrue("never started without a binary", start.contains("val binary = RcloneBinary.path(context) ?: throw NoBinaryException()"))
        assertTrue("never started on an unencrypted config", start.contains("val prepared = RcloneConfigStore.prepare(context, binary)"))
        assertTrue("orphans first", start.contains("killStale(context)"))
    }

    @Test
    fun `a stale pid is killed only when proc says it is our daemon`() {
        assertTrue(RcloneProcessPolicy.isOurDaemon("/data/app/~~x/lib/arm64/librclone.so\u0000rcd\u0000--rc-addr=127.0.0.1:1\u0000"))
        assertFalse("another app's process reusing the pid", RcloneProcessPolicy.isOurDaemon("com.other.app\u0000"))
        assertFalse("rclone but not the daemon", RcloneProcessPolicy.isOurDaemon("/x/librclone.so\u0000config\u0000encryption\u0000set\u0000"))
        assertFalse("the launcher shell before exec", RcloneProcessPolicy.isOurDaemon("/system/bin/sh\u0000-c\u0000echo\u0000"))
        assertFalse(RcloneProcessPolicy.isOurDaemon(""))
        assertEquals(1234, RcloneProcessPolicy.parsePid("1234\n"))
        assertNull(RcloneProcessPolicy.parsePid("NOTICE: something"))
        assertNull(RcloneProcessPolicy.parsePid("1"))
        val kill = daemon.substringAfter("private fun killStale(context: Context) {").substringBefore("\n    private fun pidFile(")
        assertTrue(kill.contains("if (cmdline != null && RcloneProcessPolicy.isOurDaemon(cmdline)) {\n                android.os.Process.killProcess(pid)"))
    }

    @Test
    fun `its output is kept in memory only and the log gets the exit code`() {
        val out = BoundedOutput(4)
        out.append("abcdef".toByteArray(), 6)
        assertEquals("cdef", out.snapshot().toString(Charsets.UTF_8))
        val logs = Regex("""Timber\.tag\(TAG\)\.[a-z]\(([^\n]*)\)""").findAll(daemon).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf(
                "\"cloud: rclone daemon started\"",
                "\"cloud: rclone daemon exited code=%d\", code",
                "\"cloud: stopped a daemon left by an earlier run\"",
            ),
            logs,
        )
        assertFalse("no other logging", Regex("""\b(Log\.[diwev]|println|AppLogger)\b""").containsMatchIn(daemon))
        assertFalse("rclone's output never reaches a file", daemon.contains("snapshot()") && daemon.contains("writeText(output"))
        val client = File(mainDir, "cloud/RcClient.kt").readText()
        assertFalse(client.contains("Timber"))
        assertTrue("never through a user proxy", client.contains(".proxy(Proxy.NO_PROXY)"))
        assertTrue("loopback only", client.contains(".url(\"http://127.0.0.1:\$port/\${call.method}\")"))
    }

    @Test
    fun `the binary is librclone so in nativeLibraryDir, null when this ABI has none`() {
        // The accessor lives in main sources (it once existed only as a local stub, and the app did not compile).
        val accessor = File(mainDir, "cloud/RcloneBinary.kt").readText()
        assertTrue(accessor.contains("object RcloneBinary {"))
        assertTrue(accessor.contains("fun path(context: Context): File? = resolve(context.applicationInfo.nativeLibraryDir)"))
        val dir = kotlin.io.path.createTempDirectory("rclone-abi").toFile()
        try {
            assertNull("no directory", RcloneBinary.resolve(null))
            assertNull("no binary for this ABI", RcloneBinary.resolve(dir.path))
            val binary = File(dir, "librclone.so")
            binary.writeText("x")
            assertTrue(binary.setExecutable(false, false))
            assertNull("not executable", RcloneBinary.resolve(dir.path))
            assertTrue(binary.setExecutable(true))
            assertEquals(binary, RcloneBinary.resolve(dir.path))
            binary.delete()
            assertTrue(File(dir, "librclone.so").mkdir())
            assertNull("a directory is not the binary", RcloneBinary.resolve(dir.path))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `nothing starts it at boot or in the background`() {
        // Only the cloud package and the cloud page may name the daemon (wave 2 adds the player's cloud source).
        val namers = mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("RcloneDaemon") }
            .map { it.relativeTo(mainDir).invariantSeparatorsPath }
            .toSet()
        assertTrue("the daemon is used", namers.contains("cloud/RcloneDaemon.kt"))
        assertEquals("only the cloud package: $namers", emptySet<String>(), namers.filterNot { it.startsWith("cloud/") }.toSet())
        for (path in listOf("App.kt", "MainActivity.kt", "playback/MusicService.kt")) {
            assertFalse("$path must not touch the cloud daemon", File(mainDir, path).readText().contains("iad1tya.echo.music.cloud"))
        }
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("rclone", ignoreCase = true))
    }
}
