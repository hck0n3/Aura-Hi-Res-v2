package iad1tya.echo.music.lyrics

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Finds and reads the `.lrc` next to a song on this phone (registry row 884), with the access the local scan
 * already has: the file's folder through MediaStore (READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE), its path where
 * Android still allows direct reads, and any folder the user granted through the system picker. Read once, when
 * the lyrics of that song are asked for: no scan, no polling (rule 7). Nothing is logged: no path, no file name
 * (rule 4). Every failure is "no sidecar", never an error for the user.
 */
class LocalLrcReader(private val context: Context) {
    private class AudioLocation(val displayName: String, val relativePath: String?, val volumeName: String?, val dataPath: String?)

    /** The normalised `.lrc` text of the MediaStore song [songId] (`content://media/…`), or null. */
    suspend fun read(songId: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(songId)
            if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY) return@withContext null
            val location = locate(uri) ?: return@withContext null
            val names = LocalLrcSidecar.sidecarNames(location.displayName)
            if (names.isEmpty()) return@withContext null
            val raw = fromFile(location, names) ?: fromMediaStore(location, names) ?: fromGrantedTrees(location, names)
            raw?.let(LocalLrcSidecar::normalize)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION") // MediaColumns.DATA: the folder of the song, read only to look next to it.
    private fun locate(audio: Uri): AudioLocation? {
        val projection = buildList {
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.VOLUME_NAME)
            }
        }.toTypedArray()
        return context.contentResolver.query(audio, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            fun column(name: String) = cursor.getColumnIndex(name).takeIf { it >= 0 }?.let(cursor::getString)
            val displayName = column(MediaStore.MediaColumns.DISPLAY_NAME) ?: return@use null
            AudioLocation(
                displayName = displayName,
                relativePath = column(MediaStore.MediaColumns.RELATIVE_PATH),
                volumeName = column(MediaStore.MediaColumns.VOLUME_NAME),
                dataPath = column(MediaStore.MediaColumns.DATA),
            )
        }
    }

    /** Same folder through the file system: always before Android 10; later only where the platform still lets it. */
    private fun fromFile(location: AudioLocation, names: List<String>): String? {
        val folder = location.dataPath?.let { File(it).parentFile } ?: return null
        for (name in names) {
            val file = File(folder, name)
            val text = runCatching { if (file.isFile && file.canRead()) file.inputStream().use(::readBounded) else null }.getOrNull()
            if (text != null) return text
        }
        return null
    }

    /** Same folder through MediaStore's file table (only what it lets this app see). */
    @Suppress("DEPRECATION") // MediaColumns.DATA before Android 10, where RELATIVE_PATH does not exist.
    private fun fromMediaStore(location: AudioLocation, names: List<String>): String? {
        val files = MediaStore.Files.getContentUri(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) location.volumeName ?: "external" else "external",
        )
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val folder = location.relativePath ?: return null
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} IN (?, ?)" to
                arrayOf(folder, names[0], names[1])
        } else {
            val parent = location.dataPath?.let { File(it).parent } ?: return null
            "${MediaStore.MediaColumns.DATA} IN (?, ?)" to arrayOf(File(parent, names[0]).path, File(parent, names[1]).path)
        }
        val found = runCatching {
            context.contentResolver.query(
                files,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                selection,
                args,
                null,
            )?.use { cursor ->
                val byName = buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(1), cursor.getLong(0))
                }
                names.firstNotNullOfOrNull { byName[it] }
            }
        }.getOrNull() ?: return null
        return openBounded(ContentUris.withAppendedId(files, found))
    }

    /** A folder the user granted with the system folder picker (persisted read access), when it holds the song. */
    private fun fromGrantedTrees(location: AudioLocation, names: List<String>): String? {
        if (location.relativePath == null) return null // before Android 10: no folder to place it in the tree
        val trees = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.uri.authority == EXTERNAL_STORAGE_AUTHORITY && DocumentsContract.isTreeUri(it.uri) }
        for (grant in trees) {
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(grant.uri) }.getOrNull() ?: continue
            for (name in names) {
                val documentId = LocalLrcSidecar.documentIdInTree(treeId, location.volumeName, location.relativePath, name) ?: continue
                val text = openBounded(DocumentsContract.buildDocumentUriUsingTree(grant.uri, documentId))
                if (text != null) return text
            }
        }
        return null
    }

    private fun openBounded(uri: Uri): String? =
        runCatching { context.contentResolver.openInputStream(uri)?.use(::readBounded) }.getOrNull()

    private fun readBounded(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > LocalLrcSidecar.MAX_BYTES) return null
        }
        val bytes = out.toByteArray()
        return if (bytes.isEmpty()) null else LocalLrcSidecar.decode(bytes)
    }

    private companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}
