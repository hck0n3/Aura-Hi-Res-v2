package iad1tya.echo.music.utils

import android.content.Context
import iad1tya.echo.music.constants.ExportedFileUrisKey
import iad1tya.echo.music.constants.ExportedSongIdsKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/**
 * Row 801 (H30, owner 2026-11-24): Biblioteca ▸ Exportadas and the automatic «Exportadas» list keep only the exported
 * MP3s whose file is still on the phone — the rule Descargadas applies (`AuraOfflineSearch`) and the resolver obeys
 * (it refuses an export whose file is gone). Order of [ids] is kept.
 */
internal fun exportedIdsWithFile(
    ids: List<String>,
    uris: Map<String, String>,
    fileExists: (String) -> Boolean,
): List<String> = ids.filter { id -> uris[id]?.let(fileExists) == true }

/**
 * The exported song ids whose file still exists, newest export first. One batched pass on IO each time the id list or
 * the uri map changes (or a list subscribes); a Room emission downstream (a play count, a like) reuses the answer
 * instead of touching the disk again (rule 7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun exportedSongIdsOnDevice(context: Context): Flow<List<String>> =
    context.dataStore.data
        .map { it[ExportedSongIdsKey].orEmpty() to it[ExportedFileUrisKey].orEmpty() }
        .distinctUntilChanged()
        .mapLatest { (idsRaw, urisRaw) ->
            withContext(Dispatchers.IO) {
                exportedIdsWithFile(idsRaw.split(",").filter { it.isNotBlank() }, parseExportedFileUriMap(urisRaw)) {
                    exportedFileUriExists(context, it)
                }
            }
        }
