package iad1tya.echo.music.cloud

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Registry row 891 — the cloud index on disk: `noBackupFilesDir/cloud/library.json`, next to the encrypted rclone.conf
 * (never in Android's backup nor in Aura's own: it holds paths, rule 4 applies to them too). Written whole and renamed
 * into place, so a crash mid-write leaves the previous index. Never logged.
 */
object CloudLibraryStore {
    private const val FILE = "library.json"
    private val mutex = Mutex()

    @Volatile
    private var cached: CloudLibraryState? = null

    suspend fun read(context: Context): CloudLibraryState = mutex.withLock { load(context.applicationContext) }

    suspend fun update(context: Context, change: (CloudLibraryState) -> CloudLibraryState): CloudLibraryState = mutex.withLock {
        val app = context.applicationContext
        val next = change(load(app))
        withContext(Dispatchers.IO) { write(app, next) }
        cached = next
        next
    }

    /** For the player's loader thread, which already blocks on its resolves. */
    fun readBlocking(context: Context): CloudLibraryState = cached ?: runBlocking(Dispatchers.IO) { read(context) }

    fun record(context: Context, songId: String): CloudFileRecord? = readBlocking(context).files.firstOrNull { it.id == songId }

    private suspend fun load(context: Context): CloudLibraryState = cached ?: withContext(Dispatchers.IO) {
        CloudLibraryCodec.decode(runCatching { file(context).takeIf(File::isFile)?.readText() }.getOrNull())
    }.also { cached = it }

    private fun write(context: Context, state: CloudLibraryState) {
        val target = file(context)
        val tmp = File(target.parentFile, "$FILE.tmp")
        tmp.writeText(CloudLibraryCodec.encode(state))
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException("cloud index not saved")
        }
    }

    private fun file(context: Context) = File(RcloneConfigFiles.dir(context).apply { mkdirs() }, FILE)
}
