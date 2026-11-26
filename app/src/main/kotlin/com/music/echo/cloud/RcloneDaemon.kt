package iad1tya.echo.music.cloud

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/*
 * Registry row 888 — the rclone daemon (`librclone.so rcd`), ON DEMAND only.
 *
 *  - Started by [RcloneDaemon.use], which only cloud actions call (the cloud page of Ajustes; wave 2: playing a cloud
 *    song). Never at boot, never from the service or a worker: CloudDaemonLifecycleTest pins that no such file names it.
 *  - Listens on 127.0.0.1 only, on a random free port, behind a random basic-auth pair (16 + 32 random bytes) passed in
 *    the ENVIRONMENT (RCLONE_RC_USER / RCLONE_RC_PASS), never in argv. No --rc-no-auth, no --rc-serve, no web GUI.
 *  - Stops [IDLE_STOP_MS] after the last use (one delayed job, rescheduled on each release: no polling), with core/quit
 *    and then destroy(). An app process killed by Android takes the child with it; if one ever survives (or the process
 *    died mid-use), the pid recorded at start is killed on the next start, after checking /proc/<pid>/cmdline is ours.
 *  - Its stdout/stderr (it can print the OAuth URL and paths) are drained into a bounded in-memory buffer and NEVER
 *    written to the log (rule 4). Only the exit code is logged.
 */
object RcloneProcessPolicy {
    /** The binary's file name in nativeLibraryDir (contract with the packaging, row 886/887). */
    const val BINARY_FILE_NAME = "librclone.so"

    /** Pure: stop only with nobody using it and [idleMs] elapsed since the last release. */
    fun shouldStop(activeUses: Int, lastReleaseAtMs: Long, nowMs: Long, idleMs: Long): Boolean =
        activeUses == 0 && nowMs - lastReleaseAtMs >= idleMs

    /** Pure: `/proc/<pid>/cmdline` (NUL-separated) of OUR daemon: `…/librclone.so rcd …`. */
    fun isOurDaemon(cmdline: String): Boolean {
        val args = cmdline.split('\u0000').filter { it.isNotEmpty() }
        return args.size >= 2 && args[0].substringAfterLast('/') == BINARY_FILE_NAME && args[1] == "rcd"
    }

    /** Pure: the first line the launcher shell prints is its pid (exec keeps it for rclone). */
    fun parsePid(line: String): Int? = line.trim().toIntOrNull()?.takeIf { it > 1 }

    /**
     * Pure: the daemon's command line. `sh -c 'echo $$; exec "$0" "$@"'` prints the pid and then BECOMES rclone (same
     * pid), which java.lang.Process does not expose on every Android version. No credential is in here.
     */
    fun command(binary: String, port: Int, config: String, cacheDir: String): List<String> = listOf(
        "/system/bin/sh", "-c", "echo \$\$; exec \"\$0\" \"\$@\"",
        binary,
        "rcd",
        "--rc-addr=127.0.0.1:$port",
        "--config=$config",
        "--cache-dir=$cacheDir",
        "--ask-password=false",
    )

    /** Environment variable names the credentials travel in (rclone maps --rc-user/--rc-pass to them). */
    const val ENV_USER = "RCLONE_RC_USER"
    const val ENV_PASS = "RCLONE_RC_PASS"
    const val ENV_CONFIG_PASS = "RCLONE_CONFIG_PASS"
}

object RcloneDaemon {
    const val IDLE_STOP_MS = 60_000L
    private const val TAG = "Cloud"
    private const val HEALTH_TRIES = 50
    private const val HEALTH_STEP_MS = 200L
    private const val OUTPUT_LIMIT = 8 * 1024
    private const val PID_FILE = "rcd.pid"

    /** This phone's ABI has no rclone binary: the cloud is «no disponible en este aparato». */
    class NoBinaryException : IllegalStateException("no rclone binary for this ABI")

    private class Running(val process: Process, val pid: Int?, val client: RcClient, val output: BoundedOutput)

    /** Set under the lock once [acquire] has counted this use: a caller cancelled after that still releases it. */
    private class Lease {
        @Volatile var held = false
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Running? = null
    private var activeUses = 0
    private var lastReleaseAtMs = 0L
    private var idleJob: Job? = null

    /** Runs [block] with a live daemon, starting it if needed; the daemon stays up while any block runs. */
    suspend fun <T> use(context: Context, block: suspend (RcClient) -> T): T {
        val lease = Lease()
        try {
            return block(acquire(context.applicationContext, lease))
        } finally {
            if (lease.held) withContext(NonCancellable) { release() }
        }
    }

    private suspend fun acquire(context: Context, lease: Lease): RcClient = withContext(Dispatchers.IO) {
        mutex.withLock {
            idleJob?.cancel()
            idleJob = null
            val live = running?.takeIf { it.process.isAlive } ?: start(context).also { running = it }
            activeUses++
            lease.held = true
            live.client
        }
    }

    /**
     * Row 892: a cloud stream media3 is reading holds the daemon (and the serve it streams from) up like a running [use]
     * does, without a coroutine around it: called on media3's loader thread when the stream opens, [releaseStream] when it
     * closes. False when the daemon is not running: nothing is started here, the read then fails and the player resolves
     * the song again, which starts it.
     */
    fun retainForStream(): Boolean = runBlocking {
        mutex.withLock {
            if (running?.process?.isAlive != true) return@withLock false
            idleJob?.cancel()
            idleJob = null
            activeUses++
            true
        }
    }

    fun releaseStream() {
        runBlocking { release() }
    }

    private suspend fun release() = mutex.withLock {
        activeUses = (activeUses - 1).coerceAtLeast(0)
        lastReleaseAtMs = System.currentTimeMillis()
        if (activeUses == 0) {
            idleJob?.cancel()
            idleJob = scope.launch {
                delay(IDLE_STOP_MS)
                mutex.withLock {
                    if (RcloneProcessPolicy.shouldStop(activeUses, lastReleaseAtMs, System.currentTimeMillis(), IDLE_STOP_MS)) {
                        stopLocked()
                    }
                }
            }
        }
    }

    private suspend fun start(context: Context): Running {
        val binary = RcloneBinary.path(context) ?: throw NoBinaryException()
        val prepared = RcloneConfigStore.prepare(context, binary)
        killStale(context)
        val cacheDir = File(context.cacheDir, "rclone").apply { mkdirs() }
        repeat(2) {
            val port = freeLoopbackPort()
            val user = randomToken(16)
            val pass = randomToken(32)
            val builder = ProcessBuilder(
                RcloneProcessPolicy.command(binary.path, port, prepared.config.path, cacheDir.path),
            ).redirectErrorStream(true)
            val env = builder.environment()
            RcloneEnvironment.apply(context, env)
            env[RcloneProcessPolicy.ENV_USER] = user
            env[RcloneProcessPolicy.ENV_PASS] = pass
            env[RcloneProcessPolicy.ENV_CONFIG_PASS] = prepared.password
            val process = builder.start()
            var started = false
            try {
                runCatching { process.outputStream.close() }
                val pid = RcloneProcessPolicy.parsePid(readFirstLine(process.inputStream))
                val output = BoundedOutput(OUTPUT_LIMIT)
                Thread { output.drain(process.inputStream) }.apply { isDaemon = true; start() }
                pid?.let { runCatching { pidFile(context).writeText(it.toString()) } }
                val client = RcClient(port, user, pass)
                if (waitHealthy(client, process)) {
                    watchExit(context, process, pid)
                    Timber.tag(TAG).i("cloud: rclone daemon started")
                    started = true
                    return Running(process, pid, client, output)
                }
            } finally {
                // Unhealthy, or the caller was cancelled mid-start (delay throws): never leave an untracked daemon.
                if (!started) {
                    process.destroyForcibly()
                    pidFile(context).delete()
                }
            }
        }
        throw IOException("rclone daemon did not start")
    }

    private suspend fun waitHealthy(client: RcClient, process: Process): Boolean {
        repeat(HEALTH_TRIES) {
            if (!process.isAlive) return false
            val ok = runCatching { client.call(RcRequests.version()) }.isSuccess
            if (ok) return true
            delay(HEALTH_STEP_MS)
        }
        return false
    }

    private fun stopLocked() {
        val r = running ?: return
        running = null
        idleJob = null
        runCatching { r.client.call(RcRequests.quit()) }
        if (!r.process.waitFor(3, TimeUnit.SECONDS)) {
            r.process.destroy()
            if (!r.process.waitFor(2, TimeUnit.SECONDS)) r.process.destroyForcibly()
        }
    }

    private fun watchExit(context: Context, process: Process, pid: Int?) {
        Thread {
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            Timber.tag(TAG).i("cloud: rclone daemon exited code=%d", code)
            val file = pidFile(context)
            if (pid != null && runCatching { file.readText().trim() }.getOrNull() == pid.toString()) file.delete()
        }.apply { isDaemon = true; start() }
    }

    /** A daemon recorded by an earlier app process that is still alive is killed, only if /proc says it is ours. */
    private fun killStale(context: Context) {
        val file = pidFile(context)
        val pid = runCatching { file.readText() }.getOrNull()?.let(RcloneProcessPolicy::parsePid)
        if (pid != null) {
            val cmdline = runCatching { File("/proc/$pid/cmdline").readBytes().toString(Charsets.UTF_8) }.getOrNull()
            if (cmdline != null && RcloneProcessPolicy.isOurDaemon(cmdline)) {
                android.os.Process.killProcess(pid)
                Timber.tag(TAG).w("cloud: stopped a daemon left by an earlier run")
            }
        }
        file.delete()
    }

    private fun pidFile(context: Context) = File(RcloneConfigFiles.dir(context).apply { mkdirs() }, PID_FILE)

    internal fun freeLoopbackPort(): Int =
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

    internal fun randomToken(bytes: Int): String {
        val b = ByteArray(bytes).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    /** Reads up to the first newline byte by byte, so nothing after it is swallowed by a buffer. */
    private fun readFirstLine(input: InputStream): String {
        val sb = StringBuilder()
        while (sb.length < 32) {
            val c = input.read()
            if (c < 0 || c == '\n'.code) break
            sb.append(c.toChar())
        }
        return sb.toString()
    }
}

/** The last [limit] bytes the daemon printed, kept in memory only (never logged, never written to a file). */
internal class BoundedOutput(private val limit: Int) {
    private val buffer = ByteArray(limit)
    private var size = 0
    private var start = 0

    fun drain(input: InputStream) {
        runCatching {
            input.use {
                val chunk = ByteArray(1024)
                var n = it.read(chunk)
                while (n >= 0) {
                    append(chunk, n)
                    n = it.read(chunk)
                }
            }
        }
    }

    @Synchronized
    fun append(bytes: ByteArray, length: Int) {
        for (i in 0 until length) {
            val at = (start + size) % limit
            buffer[at] = bytes[i]
            if (size < limit) size++ else start = (start + 1) % limit
        }
    }

    @Synchronized
    fun snapshot(): ByteArray = ByteArray(size) { buffer[(start + it) % limit] }
}
