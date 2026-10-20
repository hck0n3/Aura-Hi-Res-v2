package iad1tya.echo.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.constants.LibraryPinsKey
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Biblioteca ▸ «Fijados» (owner 2026-10-20): *«no quiero que se vean todos los artistas allá abajo, ni mis
 * playlists, a menos de que yo pueda pinear playlists y álbumes para que aparezcan ahí, e incluso artistas»*.
 *
 * ## Why this is a NEW store and not the old «Fijar playlist al inicio»
 * The app already had a pin: `speed_dial_item` (Room), written by «Fijar playlist al inicio» from the PLAYLIST menus
 * only and read by Inicio's «Fijadas en inicio» shelf and by the playlist widget. It is a Home feature with Home
 * semantics (playlists only, ordered by pin date, one shelf), and reusing it would have made «pin to the Library» silently
 * change Inicio and the widget. The Library gets its own list; the two never touch.
 *
 * ## Where it lives
 * One JSON string in the DataStore `settings` ([LibraryPinsKey]) — NO Room table, no migration (registry row 89: a
 * migration slip crashes every user on update). The DataStore file survives an app update like every other preference.
 * Each pin carries only what is needed to draw and open it if the item is not in the user's library list any more (kind,
 * id, a title/subtitle/cover snapshot); the live data of an item that IS in the library always wins at draw time.
 *
 * Pure on purpose (JSON in, JSON out, no Context): [LibraryPinsTest] pins the rules.
 */
enum class LibraryPinKind(val code: String) {
    PLAYLIST("P"),
    ALBUM("A"),
    ARTIST("R"),
    ;

    companion object {
        fun fromCode(code: String?): LibraryPinKind? = entries.firstOrNull { it.code == code }
    }
}

/** How the pinned section is ordered. MANUAL = the order the user dragged in «Editar fijados». */
enum class LibraryPinSort { MANUAL, CREATE_DATE, NAME, LAST_UPDATED }

data class LibraryPin(
    val kind: LibraryPinKind,
    /** The item's own id: local playlist row id or YouTube playlist id; album browseId; artist id. */
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    /** PLAYLIST only: pinned from the local-playlist menu, i.e. [id] is a local row id (and may stop existing). */
    val local: Boolean = false,
    /** ALBUM only: the album's playlistId, needed to rebuild the YouTube album menu for an album outside the library. */
    val extra: String? = null,
    /** Epoch millis when it was pinned (0 = unknown). Drives the «Fecha añadida» order. */
    val pinnedAt: Long = 0L,
) {
    val key: String get() = keyOf(kind, id)

    companion object {
        fun keyOf(kind: LibraryPinKind, id: String): String = "${kind.code}:$id"
    }
}

object LibraryPins {
    /** Enough for any real shelf; also a bound on the size of the preference. */
    const val MAX_PINS = 60

    fun parse(json: String?): List<LibraryPin> {
        if (json.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val out = ArrayList<LibraryPin>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val kind = LibraryPinKind.fromCode(o.optStringOrNull("k")) ?: continue
            val id = o.optStringOrNull("i") ?: continue
            out += LibraryPin(
                kind = kind,
                id = id,
                title = o.optStringOrNull("t").orEmpty(),
                subtitle = o.optStringOrNull("s"),
                thumbnailUrl = o.optStringOrNull("u"),
                local = o.optBoolean("l", false),
                extra = o.optStringOrNull("x"),
                pinnedAt = o.optLong("at", 0L),
            )
        }
        return out.distinctBy { it.key }.take(MAX_PINS)
    }

    fun encode(pins: List<LibraryPin>): String {
        val array = JSONArray()
        for (pin in pins) {
            val o = JSONObject()
            o.put("k", pin.kind.code)
            o.put("i", pin.id)
            o.put("t", pin.title)
            pin.subtitle?.let { o.put("s", it) }
            pin.thumbnailUrl?.let { o.put("u", it) }
            if (pin.local) o.put("l", true)
            pin.extra?.let { o.put("x", it) }
            if (pin.pinnedAt != 0L) o.put("at", pin.pinnedAt)
            array.put(o)
        }
        return array.toString()
    }

    fun isPinned(pins: List<LibraryPin>, kind: LibraryPinKind, id: String): Boolean {
        val key = LibraryPin.keyOf(kind, id)
        return pins.any { it.key == key }
    }

    fun canAdd(pins: List<LibraryPin>): Boolean = pins.size < MAX_PINS

    /**
     * Adds [pin] at the END. Already pinned: the snapshot is refreshed IN PLACE (position and original pin date kept).
     * Past [MAX_PINS] a new pin is refused (the list comes back unchanged).
     */
    fun withPin(pins: List<LibraryPin>, pin: LibraryPin, now: Long = System.currentTimeMillis()): List<LibraryPin> {
        val at = pins.indexOfFirst { it.key == pin.key }
        if (at >= 0) {
            val next = pins.toMutableList()
            next[at] = pin.copy(pinnedAt = pins[at].pinnedAt)
            return next
        }
        if (!canAdd(pins)) return pins
        return pins + pin.copy(pinnedAt = if (pin.pinnedAt != 0L) pin.pinnedAt else now)
    }

    fun without(pins: List<LibraryPin>, key: String): List<LibraryPin> = pins.filterNot { it.key == key }

    fun toggled(pins: List<LibraryPin>, pin: LibraryPin, now: Long = System.currentTimeMillis()): List<LibraryPin> =
        if (pins.any { it.key == pin.key }) without(pins, pin.key) else withPin(pins, pin, now)

    /**
     * The pins in the order of [orderedKeys]. Keys that are not pinned are ignored; pins that are not listed (pinned while
     * the user was dragging, say) keep their relative order AFTER the listed ones — a reorder can never lose a pin.
     */
    fun reordered(pins: List<LibraryPin>, orderedKeys: List<String>): List<LibraryPin> {
        val byKey = pins.associateBy { it.key }
        val listed = orderedKeys.distinct().mapNotNull { byKey[it] }
        val listedKeys = listed.mapTo(HashSet()) { it.key }
        return listed + pins.filter { it.key !in listedKeys }
    }

    /**
     * Display order of the pinned section. MANUAL keeps [items] as they come (descending is ignored). The others are
     * stable sorts; names compare with the locale's collator ignoring case and accents, like the rest of the Library.
     */
    fun <T> sorted(
        items: List<T>,
        sort: LibraryPinSort,
        descending: Boolean,
        pinnedAt: (T) -> Long,
        name: (T) -> String,
        updatedAt: (T) -> Long,
    ): List<T> {
        if (sort == LibraryPinSort.MANUAL) return items
        val ascending = when (sort) {
            LibraryPinSort.CREATE_DATE -> items.sortedBy(pinnedAt)
            LibraryPinSort.LAST_UPDATED -> items.sortedBy(updatedAt)
            LibraryPinSort.NAME -> {
                val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
                items.sortedWith { a, b -> collator.compare(name(a), name(b)) }
            }
            LibraryPinSort.MANUAL -> items
        }
        return if (descending) ascending.asReversed() else ascending
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
}

/**
 * Reads and writes the pins in the DataStore. Reads are a Flow; writes are atomic read-modify-write inside
 * `DataStore.edit`, so two quick taps (pin from a menu while «Editar» is saving a reorder) cannot lose each other.
 */
object LibraryPinsStore {
    // App-lifetime scope for fire-and-forget writes: a menu is dismissed right after the tap, and a scope tied to
    // the menu's composition would be cancelled before the edit lands.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun flow(context: Context): Flow<List<LibraryPin>> =
        // Parse only when OUR string changed: the DataStore emits on every preference write, whatever the key.
        context.dataStore.data.map { it[LibraryPinsKey] }.distinctUntilChanged().map { LibraryPins.parse(it) }

    /** First-frame value: the process snapshot when it has arrived, the blocking read only on a cold start. */
    fun snapshot(context: Context): List<LibraryPin> =
        LibraryPins.parse(context.dataStore.getNonBlocking(LibraryPinsKey, ""))

    suspend fun update(context: Context, transform: (List<LibraryPin>) -> List<LibraryPin>) {
        context.dataStore.edit { prefs ->
            val next = transform(LibraryPins.parse(prefs[LibraryPinsKey]))
            if (next.isEmpty()) {
                prefs.remove(LibraryPinsKey)
            } else {
                prefs[LibraryPinsKey] = LibraryPins.encode(next)
            }
        }
    }

    fun updateDetached(context: Context, transform: (List<LibraryPin>) -> List<LibraryPin>) {
        val app = context.applicationContext
        scope.launch {
            try {
                update(app, transform)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
            }
        }
    }
}

/** The pins as state. Seeded synchronously (no empty-state flash on the first frame), then follows the DataStore. */
@Composable
fun rememberLibraryPins(): State<List<LibraryPin>> {
    val context = LocalContext.current
    val initial = remember { LibraryPinsStore.snapshot(context) }
    return remember { LibraryPinsStore.flow(context) }.collectAsState(initial)
}
