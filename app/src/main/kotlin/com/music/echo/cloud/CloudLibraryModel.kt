package iad1tya.echo.music.cloud

import java.security.MessageDigest
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/*
 * Registry row 891 — the cloud music library, pure part (tested on the JVM).
 *
 *  - A cloud song is a row of the same `song` table the phone's scanner fills (isLocal = 1, same artist/album ids and
 *    rules), with an id of its own scheme, [CloudSongIds]: derived from the remote and the path, stable across indexes,
 *    and it never reveals the path or the file name (song ids reach the shared log, rule 4).
 *  - What the index knows of each file (path, size, date, tags read) lives in [CloudLibraryState], kept next to the
 *    encrypted rclone.conf in noBackupFilesDir (never in a backup): the path is what playback needs to find the file.
 *  - An index is incremental: a file whose size and date did not change keeps the tags read last time ([CloudIndexPolicy]).
 */

object CloudSongIds {
    const val SCHEME = "aura-cloud"
    private const val PREFIX = "$SCHEME://"

    /** `aura-cloud://<remote>/<32 hex>`: the same file always gets the same id; neither its path nor its name can be read back. */
    fun of(remote: String, path: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$remote\u0000$path".toByteArray(Charsets.UTF_8))
        val hex = digest.take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "$PREFIX$remote/$hex"
    }

    fun isCloud(id: String?): Boolean = id != null && id.startsWith(PREFIX)

    fun remoteOf(id: String): String? =
        if (!isCloud(id)) null else id.removePrefix(PREFIX).substringBefore('/').takeIf { it.isNotEmpty() }
}

fun String.isCloudSongId(): Boolean = CloudSongIds.isCloud(this)

/** The audio files the cloud library takes: the ones Biblioteca plays (each mime type is in SupportedLocalAudio's list). */
object CloudAudioFiles {
    private val MIME_BY_EXTENSION = mapOf(
        "mp3" to "audio/mpeg",
        "flac" to "audio/flac",
        "m4a" to "audio/mp4",
        "m4b" to "audio/mp4",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "opus" to "audio/opus",
        "wav" to "audio/wav",
        "mka" to "audio/x-matroska",
    )

    private val LOSSLESS_MIME = setOf("audio/flac", "audio/x-flac", "audio/wav", "audio/wave", "audio/x-wav")

    fun extensionOf(name: String): String = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)

    fun isAudio(name: String): Boolean = extensionOf(name) in MIME_BY_EXTENSION

    fun mimeOf(name: String): String? = MIME_BY_EXTENSION[extensionOf(name)]

    /** FLAC and WAV. An .m4a may hold ALAC, but nothing tells without opening it, so it counts as compressed. */
    fun isLosslessMime(mimeType: String?): Boolean = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) in LOSSLESS_MIME
}

/** The tags of one file. A file read with no tag at all is a [CloudTags] with every field null (not "unread"). */
data class CloudTags(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val disc: Int? = null,
    val track: Int? = null,
    val durationMs: Long? = null,
)

/** One audio file of the index. [tags] null = not read yet; [tagsFor] = the [CloudIndexPolicy.fingerprint] they were read at. */
data class CloudFileRecord(
    val id: String,
    val remote: String,
    val path: String,
    val size: Long,
    val modTimeMs: Long?,
    val tags: CloudTags? = null,
    val tagsFor: String? = null,
) {
    val fileName: String get() = path.substringAfterLast('/')
}

/** [folders]: remote -> the folders the user chose ("" is the whole cloud). [lastIndexAtMs]: 0 = never indexed. */
data class CloudLibraryState(
    val folders: Map<String, List<String>> = emptyMap(),
    val files: List<CloudFileRecord> = emptyList(),
    val lastIndexAtMs: Long = 0L,
)

object CloudIndexPolicy {
    /** Bounds of one index run (a whole cloud is never walked without an end). Design values, not measured. */
    const val MAX_FOLDERS_PER_RUN = 2_000
    const val MAX_FILES_PER_RUN = 20_000
    const val MAX_DEPTH = 12

    /** At most this many files' tags are read per run, the same bound as the phone's ReplayGain reads (row 725). */
    const val MAX_TAG_READS_PER_RUN = 200

    /** The automatic index (once per app start) runs only when the last one is older than this. */
    const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L

    /** The folder browser shows at most this many folders of one folder. */
    const val FOLDER_BROWSER_LIMIT = 300

    fun fingerprint(size: Long, modTimeMs: Long?): String = "$size:${modTimeMs ?: -1L}"

    /** Tags are read for a new file, a changed file, or one whose read never happened. */
    fun needsTagRead(previous: CloudFileRecord?, size: Long, modTimeMs: Long?): Boolean =
        previous?.tags == null || previous.tagsFor != fingerprint(size, modTimeMs)

    fun isStale(lastIndexAtMs: Long, nowMs: Long): Boolean =
        lastIndexAtMs <= 0L || nowMs < lastIndexAtMs || nowMs - lastIndexAtMs >= STALE_AFTER_MS

    fun normalizeFolder(path: String): String = path.trim().trim('/')

    fun isUnder(path: String, folder: String): Boolean = folder.isEmpty() || path == folder || path.startsWith("$folder/")

    /** A folder inside another chosen one adds nothing: only the outermost ones are walked. */
    fun outermost(folders: Collection<String>): List<String> {
        val normalized = folders.map(::normalizeFolder).distinct().sorted()
        return normalized.filter { f -> normalized.none { other -> other != f && isUnder(f, other) } }
    }

    data class Merge(val records: List<CloudFileRecord>, val gone: List<CloudFileRecord>)

    /**
     * Pure. The index after walking [remote]: [seen] replaces what it had; a previous file of [remote] not seen is GONE
     * only when its chosen folder was walked completely ([completeFolders]) or it is no longer under a chosen folder. A file
     * under a folder that could not be read completely is kept as it was (a failed listing never removes anything), and
     * the files of other remotes are untouched.
     */
    fun merge(
        previous: List<CloudFileRecord>,
        remote: String,
        chosen: List<String>,
        completeFolders: Set<String>,
        seen: List<CloudFileRecord>,
    ): Merge {
        val seenIds = seen.mapTo(HashSet(), CloudFileRecord::id)
        val kept = ArrayList<CloudFileRecord>()
        val gone = ArrayList<CloudFileRecord>()
        previous.forEach { old ->
            when {
                old.remote != remote -> kept += old
                old.id in seenIds -> Unit
                else -> {
                    val folder = chosen.firstOrNull { isUnder(old.path, it) }
                    if (folder != null && folder !in completeFolders) kept += old else gone += old
                }
            }
        }
        return Merge(records = kept + seen, gone = gone)
    }
}

/** The title, artist and duration a cloud song shows. The file name is the title only when the file has no title tag. */
object CloudTagFallback {
    fun title(tags: CloudTags?, fileName: String): String =
        tags?.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.substringBeforeLast('.').trim().takeIf { it.isNotEmpty() }
            ?: fileName

    fun durationSeconds(tags: CloudTags?): Int = ((tags?.durationMs ?: 0L) / 1000L).toInt().coerceAtLeast(0)
}

/** A path of the cloud as the serve's URL path: every byte outside the unreserved set is percent-encoded. */
object CloudPaths {
    fun encode(path: String): String = path.split('/').joinToString("/") { segment ->
        buildString {
            segment.toByteArray(Charsets.UTF_8).forEach { b ->
                val c = b.toInt() and 0xff
                if ((c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) || c == '-'.code ||
                    c == '.'.code || c == '_'.code || c == '~'.code
                ) {
                    append(c.toChar())
                } else {
                    append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xf])
                }
            }
        }
    }
}

/** The index file, as JSON. Anything unreadable is an empty library: the songs in the database are kept either way. */
object CloudLibraryCodec {
    private const val VERSION = 1

    fun encode(state: CloudLibraryState): String = JSONObject()
        .put("v", VERSION)
        .put("lastIndexAtMs", state.lastIndexAtMs)
        .put("folders", JSONObject().apply { state.folders.forEach { (remote, list) -> put(remote, JSONArray(list)) } })
        .put("files", JSONArray().apply { state.files.forEach { put(fileJson(it)) } })
        .toString()

    fun decode(text: String?): CloudLibraryState {
        if (text.isNullOrBlank()) return CloudLibraryState()
        return runCatching {
            val o = JSONObject(text)
            if (o.optInt("v") != VERSION) return CloudLibraryState()
            val folders = LinkedHashMap<String, List<String>>()
            o.optJSONObject("folders")?.let { f ->
                f.keys().forEach { remote ->
                    val arr = f.optJSONArray(remote) ?: return@forEach
                    folders[remote] = (0 until arr.length()).map { arr.optString(it) }
                }
            }
            val files = o.optJSONArray("files")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::file) } }
            CloudLibraryState(folders = folders, files = files.orEmpty(), lastIndexAtMs = o.optLong("lastIndexAtMs", 0L))
        }.getOrDefault(CloudLibraryState())
    }

    private fun fileJson(r: CloudFileRecord): JSONObject = JSONObject()
        .put("id", r.id)
        .put("remote", r.remote)
        .put("path", r.path)
        .put("size", r.size)
        .apply {
            r.modTimeMs?.let { put("mod", it) }
            r.tagsFor?.let { put("tagsFor", it) }
            r.tags?.let { t ->
                put(
                    "tags",
                    JSONObject().apply {
                        t.title?.let { put("title", it) }
                        t.artist?.let { put("artist", it) }
                        t.albumArtist?.let { put("albumArtist", it) }
                        t.album?.let { put("album", it) }
                        t.year?.let { put("year", it) }
                        t.disc?.let { put("disc", it) }
                        t.track?.let { put("track", it) }
                        t.durationMs?.let { put("durationMs", it) }
                    },
                )
            }
        }

    private fun file(o: JSONObject): CloudFileRecord? {
        val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val remote = o.optString("remote").takeIf { it.isNotEmpty() } ?: return null
        val path = o.optString("path").takeIf { it.isNotEmpty() } ?: return null
        val tags = o.optJSONObject("tags")?.let { t ->
            CloudTags(
                title = t.str("title"),
                artist = t.str("artist"),
                albumArtist = t.str("albumArtist"),
                album = t.str("album"),
                year = t.int("year"),
                disc = t.int("disc"),
                track = t.int("track"),
                durationMs = if (t.has("durationMs")) t.optLong("durationMs") else null,
            )
        }
        return CloudFileRecord(
            id = id,
            remote = remote,
            path = path,
            size = o.optLong("size", -1L),
            modTimeMs = if (o.has("mod")) o.optLong("mod") else null,
            tags = tags,
            tagsFor = o.str("tagsFor"),
        )
    }

    private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.int(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
}

/**
 * Pure. Walks the chosen folders breadth first with [list] (one `operations/list` per folder), bounded by folders, files
 * and depth. A chosen folder is COMPLETE only when every folder under it was listed: hitting a bound or a listing that
 * failed leaves it incomplete, and the index then keeps every file it had there ([CloudIndexPolicy.merge]).
 * [checkActive] throws to cancel (the caller's coroutine was cancelled).
 */
object CloudFolderWalk {
    data class Result(val files: List<CloudEntry>, val completeFolders: Set<String>, val foldersListed: Int, val failedListings: Int)

    fun walk(
        chosen: List<String>,
        list: (String) -> List<CloudEntry>,
        checkActive: () -> Unit = {},
        maxFolders: Int = CloudIndexPolicy.MAX_FOLDERS_PER_RUN,
        maxFiles: Int = CloudIndexPolicy.MAX_FILES_PER_RUN,
        maxDepth: Int = CloudIndexPolicy.MAX_DEPTH,
    ): Result {
        var listed = 0
        var failed = 0
        val files = ArrayList<CloudEntry>()
        val complete = LinkedHashSet<String>()
        for (root in CloudIndexPolicy.outermost(chosen)) {
            var whole = true
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.addLast(root to 0)
            while (queue.isNotEmpty()) {
                checkActive()
                if (listed >= maxFolders) {
                    whole = false
                    break
                }
                val (dir, depth) = queue.removeFirst()
                val entries = try {
                    list(dir)
                } catch (e: java.util.concurrent.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    whole = false
                    continue
                }
                listed++
                entries.sortedBy { it.name }.forEach { entry ->
                    when {
                        entry.isDir -> if (depth + 1 <= maxDepth) queue.addLast(entry.path to depth + 1) else whole = false
                        !CloudAudioFiles.isAudio(entry.name) -> Unit
                        files.size < maxFiles -> files += entry
                        else -> whole = false
                    }
                }
            }
            if (whole) complete += root
        }
        return Result(files = files, completeFolders = complete, foldersListed = listed, failedListings = failed)
    }
}
