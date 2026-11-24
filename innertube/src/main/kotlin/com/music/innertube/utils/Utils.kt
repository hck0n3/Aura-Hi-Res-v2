package com.music.innertube.utils

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.LibraryPage
import com.music.innertube.pages.PlaylistPage
import java.security.MessageDigest

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
    val page = getOrThrow()
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0
    
    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++
        
        val continuationPage = YouTube.playlistContinuation(continuation).getOrNull() ?: break
        
        if (continuationPage.songs.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            songs += continuationPage.songs
        }
        
        continuation = continuationPage.continuation
    }
    PlaylistPage(
        playlist = page.playlist,
        songs = songs,
        songsContinuation = null,
        continuation = page.continuation
    )
}

/**
 * Registry row 837: [LibraryPage.continuation] of the result is non-null when the walk stopped early (request cap,
 * failed page, loop guard, two empty pages in a row), so [LibraryPage.items] is NOT the whole remote list. It used to
 * be forced to null, which made a cut-short read indistinguishable from a complete one.
 */
@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(maxRequests: Int = 50): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val walk = walkContinuations(page.items, page.continuation, maxRequests) { continuation ->
        YouTube.libraryContinuation(continuation).getOrNull()?.let { it.items to it.continuation }
    }
    LibraryPage(
        items = walk.items,
        continuation = walk.continuation
    )
}

/** What [walkContinuations] read; [continuation] non-null = it stopped before the end of the list. */
class ContinuationWalk<T>(val items: List<T>, val continuation: String?) {
    val complete: Boolean get() = continuation == null
}

/**
 * The pagination walk of [completed] (library twin), without the network so it can be tested: follows [start] for at
 * most [maxRequests] pages; a failed page ([fetch] returns null), an already-seen token or two empty pages in a row
 * stop it and leave the token it did not finish in [ContinuationWalk.continuation].
 */
suspend fun <T> walkContinuations(
    first: List<T>,
    start: String?,
    maxRequests: Int,
    fetch: suspend (String) -> Pair<List<T>, String?>?,
): ContinuationWalk<T> {
    val items = first.toMutableList()
    var continuation = start
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    var consecutiveEmptyResponses = 0

    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++

        val (pageItems, next) = fetch(continuation) ?: break

        if (pageItems.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            items += pageItems
        }

        continuation = next
    }
    return ContinuationWalk(items, continuation)
}

/**
 * HALLAZGO-028: same pagination walk as [completed], but hands each page to [onPage] AS IT
 * ARRIVES instead of accumulating everything first. After Google sign-in the library pulls used
 * to fetch up to 50 continuation pages BEFORE the first row reached Room, so the UI looked dead
 * for minutes. Streaming the inserts makes data appear right after the first page. The loop,
 * dedupe, request cap and empty-response handling are deliberately identical to [completed]; the
 * final accumulated result is still returned so callers that need the full list keep working.
 */
@JvmName("completedStreamingLibrary")
suspend fun Result<PlaylistPage>.completedStreaming(
    onPage: suspend (List<SongItem>) -> Unit,
): Result<PlaylistPage> = runCatching {
    val page = getOrThrow()
    onPage(page.songs)
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0

    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++

        val continuationPage = YouTube.playlistContinuation(continuation).getOrNull() ?: break

        if (continuationPage.songs.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            songs += continuationPage.songs
            onPage(continuationPage.songs)
        }

        continuation = continuationPage.continuation
    }
    PlaylistPage(
        playlist = page.playlist,
        songs = songs,
        // Non-null = the walk stopped early (request cap, failed page, loop guard): [songs] is NOT the
        // whole remote list, so callers must not treat "missing remotely" as "not on the account".
        songsContinuation = continuation,
        continuation = page.continuation
    )
}

/**
 * HALLAZGO-028: library-browse twin of [completedStreaming] — see that KDoc.
 */
@JvmName("completedStreamingPlaylist")
suspend fun Result<LibraryPage>.completedStreaming(
    onPage: suspend (List<YTItem>) -> Unit,
): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    onPage(page.items)
    val items = page.items.toMutableList()
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0

    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++

        val continuationPage = YouTube.libraryContinuation(continuation).getOrNull() ?: break

        if (continuationPage.items.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            items += continuationPage.items
            onPage(continuationPage.items)
        }

        continuation = continuationPage.continuation
    }
    LibraryPage(
        items = items,
        // Non-null = incomplete walk, same contract as the playlist twin above.
        continuation = continuation
    )
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null
            else part.substring(0, splitIndex) to part.substring(splitIndex + 1)
        }
        .toMap()

fun String.parseTime(): Int? {
    try {
        val parts = split(":").map { it.toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

fun isPrivateId(browseId: String): Boolean {
    return browseId.contains("privately")
}
