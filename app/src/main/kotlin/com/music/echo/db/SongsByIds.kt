package iad1tya.echo.music.db

import iad1tya.echo.music.db.entities.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Room binds one SQL variable per id in `WHERE id IN (:songIds)`, and the system SQLite of Android 8-11 refuses more than
 * 999 («too many SQL variables»). The exported lists hold up to [iad1tya.echo.music.constants.ExportedEntriesCap] ids
 * and a selection can be a whole library, so a long list is split; 900 matches `LocalSongScanner.SqlBatchSize`.
 */
internal const val SONG_ID_QUERY_CHUNK = 900

/** The id groups [getSongsByIdsFlowChunked] queries: duplicates dropped (an `IN` list returns each row once anyway). */
internal fun songIdQueryChunks(songIds: List<String>): List<List<String>> =
    songIds.distinct().chunked(SONG_ID_QUERY_CHUNK)

/**
 * [DatabaseDao.getSongsByIdsFlow] for a list of any length: one query per chunk, combined into one list. Like the single
 * query, the result comes back in no particular order — callers that need an order sort it themselves.
 */
fun DatabaseDao.getSongsByIdsFlowChunked(songIds: List<String>): Flow<List<Song>> {
    val chunks = songIdQueryChunks(songIds)
    if (chunks.size <= 1) return getSongsByIdsFlow(chunks.firstOrNull().orEmpty())
    return combine(chunks.map { getSongsByIdsFlow(it) }) { parts -> parts.toList().flatten() }
}
