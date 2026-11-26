package iad1tya.echo.music.localmedia

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/** One audio document found in a picked folder that MediaStore does not index. */
internal data class PickedFolderAudio(
    val documentUri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    /** The ids of every document of this picked folder start with it. */
    val folderPrefix: String,
)

internal data class PickedFolderWalk(
    val files: List<PickedFolderAudio>,
    /** Picked folders that could not be walked completely: the songs under these prefixes that were not seen are kept. */
    val unreadPrefixes: Set<String>,
)

/**
 * Row 881: walks the picked folders (persisted SAF tree permissions) directory by directory with [DocumentsContract],
 * bounded by [deadlineElapsedMs], [MaxEntriesPerScan] and [MaxDepth], and cancellable between directories. A folder
 * that fails, is cut short or finds no audio at all is reported as unread, never as empty. [indexedKeys] are the files
 * MediaStore already lists ([mediaStoreDocumentKey]); they are left to the MediaStore pass.
 */
internal class PickedFolderWalker(private val context: Context) {

    /** What the id of every document walked from [treeUri] starts with (the same encoding the ids are built with). */
    fun folderPrefix(treeUri: String): String? = runCatching {
        val tree = Uri.parse(treeUri)
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)).toString()
            .substringBeforeLast("/document/") + "/document/"
    }.getOrNull()

    fun walk(treeUris: Collection<String>, indexedKeys: Set<String>, job: Job?, deadlineElapsedMs: Long): PickedFolderWalk {
        val files = ArrayList<PickedFolderAudio>()
        val unread = LinkedHashSet<String>()
        var entries = 0
        for (raw in treeUris.distinct()) {
            job?.ensureActive()
            val tree = runCatching { Uri.parse(raw) }.getOrNull() ?: continue
            val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
            val prefix = folderPrefix(raw) ?: continue
            val found = ArrayList<PickedFolderAudio>()
            var complete = true
            val pending = ArrayDeque<Pair<String, Int>>().apply { add(treeDocumentId to 0) }
            while (pending.isNotEmpty()) {
                job?.ensureActive()
                if (SystemClock.elapsedRealtime() > deadlineElapsedMs || entries >= MaxEntriesPerScan) {
                    complete = false
                    break
                }
                val (directoryId, depth) = pending.removeFirst()
                val children = runCatching {
                    context.contentResolver.query(
                        DocumentsContract.buildChildDocumentsUriUsingTree(tree, directoryId),
                        ChildColumns,
                        null,
                        null,
                        null,
                    )
                }.getOrNull()
                if (children == null) {
                    complete = false
                    break
                }
                children.use { cursor ->
                    while (cursor.moveToNext()) {
                        entries++
                        val documentId = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: continue
                        if (isHiddenDocumentName(name)) continue
                        val mime = cursor.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth + 1 <= MaxDepth) pending.add(documentId to depth + 1) else complete = false
                            continue
                        }
                        // Audio only: a video is the MediaStore video pass's (it goes to «Vídeos exportados», row 583).
                        if (mime?.startsWith("video/") == true || !SupportedLocalAudio.isSupported(name, mime)) continue
                        if (safDocumentKey(tree.authority, documentId)?.let(indexedKeys::contains) == true) continue
                        found += PickedFolderAudio(
                            documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId).toString(),
                            displayName = name,
                            mimeType = mime,
                            sizeBytes = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                            lastModifiedMs = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                            folderPrefix = prefix,
                        )
                    }
                }
            }
            files += found
            // Nothing found reads like "every file is gone" (row 694): only songs that came from this walk are kept by it.
            if (!complete || found.isEmpty()) unread += prefix
        }
        return PickedFolderWalk(files = files, unreadPrefixes = unread)
    }

    private companion object {
        val ChildColumns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        /** Design limits, not measured: a picked folder bigger or deeper than this is walked as far as these go. */
        const val MaxEntriesPerScan = 20_000
        const val MaxDepth = 24
    }
}
