package iad1tya.echo.music.devicesync

import android.content.Context
import iad1tya.echo.music.constants.LibraryPinsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.SpeedDialItem
import iad1tya.echo.music.devicesync.LibrarySyncCore.PinMeta
import iad1tya.echo.music.devicesync.LibrarySyncCore.PinRef
import iad1tya.echo.music.utils.LibraryPin
import iad1tya.echo.music.utils.LibraryPinKind
import iad1tya.echo.music.utils.LibraryPins
import iad1tya.echo.music.utils.LibraryPinsStore
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.flow.first

/**
 * Row 584 (owner 2026-10-31: «sincroniza las listas que están pineadas, ya sea en el inicio o en la biblioteca») — the two places a thing
 * can be pinned, as registers of the library sync:
 *  · scope `l` — Biblioteca ▸ Fijados ([LibraryPinsStore], one preference);
 *  · scope `h` — «Fijadas en inicio» (the `speed_dial_item` table).
 * A local playlist has a different row id on each device, so its pin travels under the playlist's SYNC key (its browseId, or its own id
 * when it has none) and is mapped back to the row id of the device that receives it. A pin whose playlist has not arrived yet waits (it
 * is reported as unresolved and comes with the next round).
 */
class LibraryPinsBackend(
    private val context: Context,
    private val database: MusicDatabase,
) : PinsBackend {

    override suspend fun readLocal(): Map<String, String> {
        val facts = LinkedHashMap<String, String>()
        val keyOfRow = database.syncPlaylistRows().associate { it.id to (it.browseId ?: it.id) }

        for (pin in LibraryPins.parse(context.dataStore.data.first()[LibraryPinsKey])) {
            val id = if (pin.kind == LibraryPinKind.PLAYLIST && pin.local) keyOfRow[pin.id] ?: continue else pin.id
            if (!LibrarySyncCore.syncableId(id)) continue
            facts[LibrarySyncCore.pinKey(PinRef(SCOPE_LIBRARY, pin.kind.code, id))] = LibrarySyncCore.encodePin(
                PinMeta(pin.title, pin.subtitle.orEmpty(), pin.thumbnailUrl.orEmpty(), pin.local, pin.extra.orEmpty(), pin.pinnedAt),
            )
        }

        for (item in database.speedDialDao.getAll().first()) {
            val code = homeCode(item.type) ?: continue
            val isLocalPlaylist = code == "P" && keyOfRow.containsKey(item.id)
            val id = if (isLocalPlaylist) keyOfRow.getValue(item.id) else item.id
            if (!LibrarySyncCore.syncableId(id)) continue
            facts[LibrarySyncCore.pinKey(PinRef(SCOPE_HOME, code, id))] = LibrarySyncCore.encodePin(
                PinMeta(item.title, item.subtitle.orEmpty(), item.thumbnailUrl.orEmpty(), isLocalPlaylist, item.secondaryId.orEmpty(), item.createDate),
            )
        }
        return facts
    }

    override suspend fun applyPins(upserts: List<Pair<PinRef, PinMeta>>, removals: List<PinRef>): ApplyResult {
        var applied = 0
        var unresolved = 0

        // The row id here of every playlist a pin names (suspend lookups first: the preference edit below is not a suspend lambda).
        val rowFor = HashMap<String, String?>()
        for (ref in upserts.map { it.first } + removals) {
            if (ref.code == "P" && ref.id !in rowFor) rowFor[ref.id] = database.syncPlaylistIdFor(ref.id)
        }

        val libraryUps = ArrayList<LibraryPin>()
        for ((ref, meta) in upserts) {
            if (ref.scope != SCOPE_LIBRARY) continue
            val kind = LibraryPinKind.fromCode(ref.code) ?: continue
            val id = if (kind == LibraryPinKind.PLAYLIST && meta.local) rowFor[ref.id] ?: run { unresolved++; null } ?: continue else ref.id
            libraryUps += LibraryPin(
                kind, id, meta.title, meta.subtitle.ifBlank { null }, meta.cover.ifBlank { null }, meta.local,
                meta.extra.ifBlank { null }, meta.at,
            )
        }
        val libraryRemovals = removals.filter { it.scope == SCOPE_LIBRARY }.mapNotNull { ref ->
            val kind = LibraryPinKind.fromCode(ref.code) ?: return@mapNotNull null
            // A local playlist pin may be stored under the row id here; try both spellings.
            listOfNotNull(LibraryPin.keyOf(kind, ref.id), rowFor[ref.id]?.let { LibraryPin.keyOf(kind, it) })
        }.flatten()
        if (libraryUps.isNotEmpty() || libraryRemovals.isNotEmpty()) {
            LibraryPinsStore.update(context) { current ->
                var list = current
                for (key in libraryRemovals) list = LibraryPins.without(list, key)
                for (pin in libraryUps) list = LibraryPins.withPin(list, pin, pin.pinnedAt.takeIf { it > 0L } ?: System.currentTimeMillis())
                list
            }
            applied += libraryUps.size + libraryRemovals.size
        }

        for ((ref, meta) in upserts) {
            if (ref.scope != SCOPE_HOME) continue
            val type = homeType(ref.code) ?: continue
            val id = if (ref.code == "P" && meta.local) rowFor[ref.id] ?: run { unresolved++; null } ?: continue else ref.id
            database.speedDialDao.insert(
                SpeedDialItem(
                    id = id,
                    secondaryId = meta.extra.ifBlank { null },
                    title = meta.title,
                    subtitle = meta.subtitle.ifBlank { null },
                    thumbnailUrl = meta.cover.ifBlank { null },
                    type = type,
                    createDate = if (meta.at > 0L) meta.at else System.currentTimeMillis(),
                ),
            )
            applied++
        }
        for (ref in removals) {
            if (ref.scope != SCOPE_HOME) continue
            database.speedDialDao.delete(ref.id)
            rowFor[ref.id]?.let { database.speedDialDao.delete(it) }
            applied++
        }
        return ApplyResult(applied, unresolved)
    }

    private fun homeCode(type: String): String? = when (type) {
        "SONG" -> "S"
        "ALBUM" -> "A"
        "ARTIST" -> "R"
        "PLAYLIST" -> "P"
        else -> null
    }

    private fun homeType(code: String): String? = when (code) {
        "S" -> "SONG"
        "A" -> "ALBUM"
        "R" -> "ARTIST"
        "P" -> "PLAYLIST"
        else -> null
    }

    private companion object {
        const val SCOPE_LIBRARY = "l"
        const val SCOPE_HOME = "h"
    }
}
