package iad1tya.echo.music.utils

import android.content.Context
import iad1tya.echo.music.constants.ExportedFileUrisKey
import iad1tya.echo.music.constants.ExportedSongIdsKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Row 801 (H30, owner 2026-11-24): Biblioteca ▸ Exportadas and the automatic «Exportadas» list keep only the exported
 * MP3s whose file is still on the phone — the rule Descargadas applies (`AuraOfflineSearch`) and the resolver obeys
 * (it refuses an export whose file is gone). Order of [ids] is kept.
 */
internal fun exportedIdsWithFile(
    ids: List<String>,
    uris: Map<String, String>,
    known: MutableMap<String, Boolean> = mutableMapOf(),
    fileExists: (String) -> Boolean,
): List<String> {
    // [known] carries the answers of the previous pass: only a uri that is new (or changed) is checked again, and one
    // that left the map is forgotten. An export writes its id and then its uri, so it costs one check, not a full pass.
    known.keys.retainAll(uris.values.toSet())
    return ids.filter { id -> uris[id]?.let { uri -> known.getOrPut(uri) { fileExists(uri) } } == true }
}

/**
 * The exported song ids whose file still exists, newest export first. A full pass on IO when a list subscribes; after
 * that, a change of the id list or the uri map only checks the uris it has not answered yet, and a superseded pass
 * stops at the next file. A Room emission downstream (a play count, a like) reuses the answer (rule 7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun exportedSongIdsOnDevice(context: Context): Flow<List<String>> = flow {
    val known = HashMap<String, Boolean>()
    emitAll(
        context.dataStore.data
            .map { it[ExportedSongIdsKey].orEmpty() to it[ExportedFileUrisKey].orEmpty() }
            .distinctUntilChanged()
            .mapLatest { (idsRaw, urisRaw) ->
                withContext(Dispatchers.IO) {
                    exportedIdsWithFile(idsRaw.split(",").filter { it.isNotBlank() }, parseExportedFileUriMap(urisRaw), known) {
                        ensureActive()
                        exportedFileUriExists(context, it)
                    }
                }
            }
    )
}

/** How long the shared check outlives its last list, so a sort or filter change re-subscribes without a new pass. */
internal const val EXPORTED_ON_DEVICE_KEEP_MS = 5_000L

/**
 * One [exportedSongIdsOnDevice] per ViewModel, shared by every sort and filter. `null` until the first pass answers,
 * so a list switching to «Exportadas» shows nothing instead of the previous filter's songs; it goes back to `null`
 * once no list has held it for [EXPORTED_ON_DEVICE_KEEP_MS], and the next list starts with a full pass.
 */
fun exportedSongIdsOnDeviceIn(scope: CoroutineScope, context: Context): StateFlow<List<String>?> =
    exportedSongIdsOnDevice(context).stateIn(scope, SharingStarted.WhileSubscribed(EXPORTED_ON_DEVICE_KEEP_MS, 0L), null)
