package iad1tya.echo.music.utils

/**
 * 🔴 DUEÑO (2026-10-06): *"cualquier canción que ya no esté disponible en YouTube Music no se la
 * muestres al usuario, al igual que los vídeos no disponibles, para no causar errores de reproducción,
 * sin importar que esas canciones pertenezcan a una lista mía"*.
 *
 * Qué canciones se dan por NO DISPONIBLES, y con qué garantías. Es la parte peligrosa del pedido:
 * ocultar por error es perder canciones de su biblioteca de vista, así que solo entran dos señales, y
 * ninguna borra nada (el consumidor solo oculta o salta — la fila, la lista y el Me gusta siguen ahí):
 *
 *  1. **YouTube Music la muestra en gris** ([markGreyedOut]) — `MUSIC_ITEM_RENDERER_DISPLAY_POLICY_GREY_OUT`,
 *     la misma marca con la que su propia app la deja sin poder tocarse. Caduca a los [GREYED_TTL_MS] si
 *     no se vuelve a ver, y se quita en cuanto una lista la devuelve SIN gris ([markSeenAvailable]).
 *
 *  2. **Falló al reproducirse por el CONTENIDO** ([recordFailure]: región, solo Premium, retirada…). No
 *     se cree a la primera: queda PENDIENTE y solo se confirma cuando OTRA canción suena bien después
 *     ([recordSuccess]) — la prueba de que la sesión y la red estaban bien y el problema era esa canción.
 *     Si [IDENTITY_STREAK] canciones DISTINTAS fallan seguidas sin un solo acierto, lo que falla es la
 *     sesión, la red o el cifrado (filas #29/#30, `IdentityRejectionTracker`), no el contenido: se
 *     descartan todas las pendientes. Caduca a los [FAILURE_TTL_MS], y un acierto de esa misma canción la
 *     desmarca al instante.
 *
 * Pura (sin Android): el reloj se inyecta, la persistencia es un texto plano ([serialize]/[restore]).
 * Probada en UnavailableRegistryTest.
 */
class UnavailableRegistry(private val now: () -> Long = System::currentTimeMillis) {

    enum class Source(val code: Char) { GREYED('g'), FAILED('f') }

    private data class Entry(val markedAt: Long, val source: Source)

    private val entries = LinkedHashMap<String, Entry>()

    /** Fallos aún sin confirmar, en orden, con su hora. */
    private val pending = LinkedHashMap<String, Long>()

    /**
     * Tras una racha de [IDENTITY_STREAK] no se acepta ningún fallo nuevo hasta el próximo acierto: si no,
     * la cola de un corte de sesión (el 4.º y 5.º fallo, justo antes de que vuelva a funcionar) quedaría
     * pendiente y el primer acierto la confirmaría como "contenido".
     */
    private var streakTripped = false

    @Synchronized
    fun isUnavailable(id: String): Boolean {
        val e = entries[id] ?: return false
        if (expired(e)) {
            entries.remove(id)
            return false
        }
        return true
    }

    /** Los ids no disponibles vigentes (sin caducados). */
    @Synchronized
    fun snapshot(): Set<String> {
        pruneExpired()
        return HashSet(entries.keys)
    }

    /** YouTube Music las devolvió en gris. Devuelve true si algo cambió. */
    @Synchronized
    fun markGreyedOut(ids: Collection<String>): Boolean {
        var changed = false
        val t = now()
        ids.forEach { id ->
            if (id.isBlank()) return@forEach
            val previous = entries[id]
            // A runtime-confirmed failure is the stronger fact; a re-sighting only refreshes its date.
            val source = previous?.source ?: Source.GREYED
            entries[id] = Entry(t, source)
            if (previous == null) changed = true
        }
        if (changed) capSize()
        return changed
    }

    /**
     * Una lista de YouTube Music devolvió estas canciones SIN gris: si alguna estaba marcada como gris,
     * vuelve a estar disponible (YouTube la restituyó). Un fallo de reproducción NO se quita por esto —
     * aparecer en una lista no prueba que suene; para eso está [recordSuccess].
     */
    @Synchronized
    fun markSeenAvailable(ids: Collection<String>): Boolean {
        var changed = false
        ids.forEach { id ->
            if (entries[id]?.source == Source.GREYED) {
                entries.remove(id)
                changed = true
            }
        }
        return changed
    }

    /** Falló por el contenido. Queda pendiente hasta que otra canción suene (ver la clase). */
    @Synchronized
    fun recordFailure(id: String) {
        if (id.isBlank() || id in entries || streakTripped) return
        val t = now()
        pending.entries.removeAll { t - it.value > PENDING_TTL_MS }
        pending[id] = t
        if (pending.size >= IDENTITY_STREAK) {
            // Tres canciones distintas seguidas: es la sesión/la red/el cifrado, no el contenido.
            pending.clear()
            streakTripped = true
        }
    }

    /**
     * [id] sonó bien: la sesión y la red funcionan, así que los fallos pendientes de OTRAS canciones eran
     * de verdad del contenido y se confirman. Y si [id] estaba marcada, deja de estarlo. Devuelve true si
     * algo cambió.
     */
    @Synchronized
    fun recordSuccess(id: String): Boolean {
        var changed = entries.remove(id) != null
        streakTripped = false
        val t = now()
        pending.remove(id)
        pending.forEach { (failedId, failedAt) ->
            if (t - failedAt <= PENDING_TTL_MS) {
                entries[failedId] = Entry(t, Source.FAILED)
                changed = true
            }
        }
        pending.clear()
        if (changed) capSize()
        return changed
    }

    /** `id,epochMillis,g|f` por línea. Lo caducado no se escribe. */
    @Synchronized
    fun serialize(): String {
        pruneExpired()
        return entries.entries.joinToString("\n") { (id, e) -> "$id,${e.markedAt},${e.source.code}" }
    }

    /** Carga lo que [serialize] escribió. Las líneas ilegibles se ignoran (nunca lanza). */
    @Synchronized
    fun restore(text: String?) {
        entries.clear()
        pending.clear()
        streakTripped = false
        text?.lineSequence()?.forEach { line ->
            val parts = line.split(',')
            if (parts.size != 3) return@forEach
            val id = parts[0].trim()
            val at = parts[1].toLongOrNull() ?: return@forEach
            val source = Source.entries.firstOrNull { it.code == parts[2].firstOrNull() } ?: return@forEach
            if (id.isNotEmpty()) entries[id] = Entry(at, source)
        }
        pruneExpired()
        capSize()
    }

    private fun expired(e: Entry): Boolean {
        val ttl = if (e.source == Source.GREYED) GREYED_TTL_MS else FAILURE_TTL_MS
        return now() - e.markedAt > ttl
    }

    private fun pruneExpired() {
        entries.entries.removeAll { expired(it.value) }
    }

    /** Acotado: lo más antiguo sale primero. */
    private fun capSize() {
        if (entries.size <= MAX_ENTRIES) return
        val overflow = entries.size - MAX_ENTRIES
        entries.entries.sortedBy { it.value.markedAt }.take(overflow).map { it.key }.forEach { entries.remove(it) }
    }

    companion object {
        /** Gris visto en YouTube Music: un mes si no se vuelve a ver (las listas se re-sincronizan antes). */
        const val GREYED_TTL_MS = 30L * 24 * 60 * 60 * 1000

        /** Fallo confirmado: dos semanas — una canción bloqueada a veces vuelve. */
        const val FAILURE_TTL_MS = 14L * 24 * 60 * 60 * 1000

        /** Un fallo sin confirmar que no ve un acierto en este tiempo no se confirma nunca. */
        const val PENDING_TTL_MS = 30L * 60 * 1000

        /** Canciones DISTINTAS fallando seguidas a partir de las cuales el problema no es el contenido. */
        const val IDENTITY_STREAK = 3

        const val MAX_ENTRIES = 5000
    }
}
