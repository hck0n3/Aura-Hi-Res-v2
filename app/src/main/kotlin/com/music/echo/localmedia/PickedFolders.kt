package iad1tya.echo.music.localmedia

import java.util.Locale

/**
 * Row 881 (plan, phase 4: «recorrer de verdad la carpeta elegida»): a folder picked in the scan sheet is walked through
 * the storage access framework, so the audio in it that MediaStore does not index (a `.nomedia` folder, a card MediaStore
 * skipped) is found too. Pure Kotlin: which document is the same file as a MediaStore row, and what an incomplete walk
 * must keep, are unit-tested on the JVM.
 */

/** The documents provider of the phone's own storage and its cards; MediaStore indexes only what lives there. */
internal const val ExternalStorageDocumentsAuthority = "com.android.externalstorage.documents"

private val RepeatedSlash = Regex("/+")

private fun normalizedPath(path: String): String =
    path.replace('\\', '/').replace(RepeatedSlash, "/").trim('/').lowercase(Locale.ROOT)

/** `primary:Music/a.mp3` -> `primary/music/a.mp3`; `1A2B-3C4D:Music/a.mp3` -> `1a2b-3c4d/music/a.mp3`. Null: another provider. */
internal fun safDocumentKey(authority: String?, documentId: String): String? {
    if (authority != ExternalStorageDocumentsAuthority) return null
    val root = documentId.substringBefore(':', missingDelimiterValue = "")
    val path = documentId.substringAfter(':', missingDelimiterValue = "")
    if (root.isBlank() || path.isBlank()) return null
    return root.lowercase(Locale.ROOT) + "/" + normalizedPath(path)
}

/**
 * The same key for a MediaStore row: Android 10+ from its volume, relative path and name (`external_primary` is the
 * `primary` root), before that from its absolute path (`/storage/emulated/<user>` is `primary`). Null when it cannot say.
 */
internal fun mediaStoreDocumentKey(
    volumeName: String?,
    relativePath: String?,
    displayName: String?,
    absolutePath: String?,
): String? {
    if (!volumeName.isNullOrBlank() && relativePath != null && !displayName.isNullOrBlank()) {
        val root = when (val volume = volumeName.lowercase(Locale.ROOT)) {
            "external_primary", "external" -> "primary"
            else -> volume
        }
        return root + "/" + normalizedPath("$relativePath/$displayName")
    }
    val parts = absolutePath?.replace('\\', '/')?.split('/')?.filter(String::isNotEmpty) ?: return null
    if (parts.size < 3 || parts[0] != "storage") return null
    return if (parts[1] == "emulated") {
        if (parts.size < 4) null else "primary/" + normalizedPath(parts.drop(3).joinToString("/"))
    } else {
        parts[1].lowercase(Locale.ROOT) + "/" + normalizedPath(parts.drop(2).joinToString("/"))
    }
}

/**
 * The songs a scan no longer sees that belong to a picked folder it could not walk completely (permission revoked, the
 * card taken out, the time or size limit reached, or a walk that found no audio at all) are KEPT, like the absent volume
 * of row 694: [this] is (may delete, keep) and the result moves those songs to the kept side.
 */
internal fun Pair<List<String>, List<String>>.keepingUnreadFolders(unreadPrefixes: Collection<String>): Pair<List<String>, List<String>> {
    if (unreadPrefixes.isEmpty()) return this
    val (unread, readable) = first.partition { id -> unreadPrefixes.any(id::startsWith) }
    return readable to second + unread
}

/** A display name the walk never imports: hidden files and the `.trashed-…` / `.pending-…` files of MediaStore. */
internal fun isHiddenDocumentName(displayName: String): Boolean = displayName.startsWith(".")
