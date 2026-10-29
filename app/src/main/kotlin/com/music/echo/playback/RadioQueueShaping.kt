package iad1tya.echo.music.playback

import androidx.media3.common.MediaItem
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.reco.TasteProfile

/**
 * HALLAZGO-021 split, phase A — the two stateless post-sort passes that shape the radio queue order,
 * extracted verbatim from MusicService.orderedByTaste (which stays in the service: it reads the taste
 * profile, dislike store, listening history and genre cache). Nothing here touches service state, the
 * database or the network — both passes are in-memory, order- and length-preserving.
 */
object RadioQueueShaping {

    /**
     * ¿Puede el **adelanto** de la radio infinita sembrar ya, o hay que esperar a que la cola termine
     * de verdad?
     *
     * 🔴 Reporte del dueño, DOS veces (2026-09-17): *"estoy en un álbum y empiezo a reproducir el
     * álbum desde el inicio y estoy cambiando las canciones de manera manual […] de la nada me
     * reproduce música de otro artista o otra canción que no corresponde a la cola actual"*. Y antes,
     * sobre lo mismo: *"deve de poner canción dentro del playlist o álbum que esté escuchando, y para
     * cuando termine devirá seguir con la cola infinita"*.
     *
     * ## El fallo
     * La siembra de adelanto (el bloque B3 de `onMediaItemTransition`) se disparaba con **cualquier**
     * motivo de transición menos `REPEAT` — incluido un **SEEK manual** — en cuanto
     * `!player.hasNextMediaItem()`. O sea: saltar a mano hasta la última canción del álbum le metía
     * ahí mismo una tanda de canciones ajenas, y a partir de ese momento el álbum ya no era el álbum.
     *
     * Y con el **aleatorio encendido** es peor, porque `hasNextMediaItem()` mira el orden ALEATORIO:
     * un salto manual puede caer en "el último que toca sonar" habiendo escuchado tres de quince, y la
     * radio se sembraba con el álbum casi entero sin oír.
     *
     * El código ya sabía que un salto manual no es una cola terminada — la marca de "vuelta completa"
     * del aleatorio mejorado, en ese mismo bloque, exige `reason == AUTO` — pero el traspaso a la radio
     * se quedó sin esa misma prueba, con un comentario diciendo que corría "de todas formas".
     *
     * ## Por qué restringirlo es seguro
     * Esta siembra es **solo un adelanto** para que no haya hueco en el empalme. La red que de verdad
     * garantiza que la música no se pare es otra: la de `STATE_ENDED` con `!hasNextMediaItem()`, que
     * está **siempre activa** (ni siquiera la apaga el interruptor de autoplay) y cuyo propio
     * comentario ya dice que existe porque el adelanto "puede no dispararse (en pausa, **salto manual a
     * la última pista**, semilla vacía)". O sea: el caso del salto manual ya estaba cubierto por la
     * red de abajo, y el adelanto solo estaba ensuciando la cola antes de tiempo.
     *
     * Así queda el comportamiento que pidió, exacto:
     *  - álbum sonando de principio a fin → el adelanto siembra en la última transición AUTO, sin hueco;
     *  - saltando a mano → no se le mete nada, el álbum sigue siendo el álbum;
     *  - salta a la última y la deja acabar → la red de `STATE_ENDED` siembra y la música continúa.
     *
     * @param reason el `Player.MEDIA_ITEM_TRANSITION_REASON_*` de la transición en curso.
     * @param isAuto `reason` es AUTO (avance natural al acabar una canción).
     * @param isPlaylistChanged `reason` es PLAYLIST_CHANGED (una cola nueva que **nace** en su último
     *   elemento — una cola de un solo tema, por ejemplo — y que por tanto no tiene nada detrás).
     */
    fun mayPreSeedRadio(isAuto: Boolean, isPlaylistChanged: Boolean): Boolean = isAuto || isPlaylistChanged

    /**
     * Row 503 (owner 2026-10-29: *«cuando la última canción de una lista o álbum termina y va a empezar la cola inteligente, le
     * cuesta reaccionar al botón de cambiar canción, se queda pensando; ya debería estar preparada»*). The head-start seed
     * ([mayPreSeedRadio]) fires when the LAST song STARTS, and a tap on «siguiente» in its first seconds had to wait for the
     * whole fetch + style gate. This is the same seed, one song EARLIER: when the song BEFORE the last one starts by itself, the
     * continuation of a finished album / list is fetched and appended AFTER the last song (nothing is cut), so by the time the
     * last song plays the queue already goes on.
     *
     * Deliberately narrow, because the head-start of 2026-09-17 once dirtied albums: only a natural advance (never a manual
     * seek), in order (not shuffle), with repeat off, in a collection with no next page, and only when the NEXT item is the last.
     */
    fun mayPrimeEarly(
        isAuto: Boolean,
        shuffle: Boolean,
        repeatOff: Boolean,
        isCollection: Boolean,
        hasNextPage: Boolean,
        itemCount: Int,
        nextIndex: Int,
    ): Boolean = isAuto && !shuffle && repeatOff && isCollection && !hasNextPage &&
        itemCount >= 2 && nextIndex == itemCount - 1


    /** Greedy artist-spacing: keep the incoming (taste/relatedness) order as the base, but when the next item
     *  repeats a primary artist placed in the last 2 slots, skip ahead to the best-ranked item by a different
     *  artist (fallback: take the head). Preserves the backbone, kills same-artist streaks.
     *
     *  [leadingArtists] (lowercase primary artists, oldest first) are the songs this batch FOLLOWS in the queue
     *  (see [LiveQueueSnapshot.tailArtists]): they count as already placed, so a batch no longer opens with the
     *  artist of the song that is playing. Empty = exactly the old behaviour. The rule itself lives in the
     *  Android-free [ArtistSpacing] so it can be unit-tested on the JVM. */
    fun spacedByArtist(items: List<MediaItem>, leadingArtists: List<String> = emptyList()): List<MediaItem> =
        ArtistSpacing.space(items, { mi -> mi.metadata?.artists?.firstOrNull()?.name?.lowercase() }, leadingArtists)

    /**
     * Phase B #4 — exploration quota. Reserve roughly every 15th slot for a "fresh" candidate: one whose primary
     * artist is NOT already in the taste profile ([iad1tya.echo.music.reco.TasteProfile.isKnownArtist]), so radio
     * doesn't tunnel into pure exploitation — but far less often than the old 1-in-5 / 1-in-10 cadences that made
     * context-faithful radio feel random. Never drops or duplicates anything —
     * output length == input length, and each partition keeps its incoming (taste/relatedness) order. Null profile
     * (no taste yet), lists under 8, or no fresh/known split → returns the list unchanged (today's behaviour).
     * In-memory only, no network, no extra cost.
     *
     * [blocked] are ids that may NOT claim a reserved exploration slot: a CTX_SINK id, or a candidate the
     * genre steer pushed back for a genre we KNOW and know to be off-context
     * ([iad1tya.echo.music.reco.ContextProfile.blocksExploration]). Without that rule the quota undid the
     * steer — the reserved slots went to precisely the songs it had just demoted (the owner's "una
     * del género, otra que no tiene nada que ver").
     *
     * An UNKNOWN genre is deliberately NOT a reason to block, and that boundary is load-bearing: "not in
     * your taste profile" and "we have no idea what genre this is" describe the SAME candidate almost
     * every time, so blocking on absence would empty this fresh partition on a cold or partial GenreCache
     * and turn the discovery reserve off exactly when discovery is happening (registry #39/#41).
     *
     * A blocked candidate is only moved OUT of the fresh partition: it keeps its sorted position, is
     * never dropped, and if every fresh candidate is blocked the interleave no-ops (order unchanged).
     * Empty [blocked] (the steer is off) → byte-identical to before.
     */
    fun withExplorationQuota(
        items: List<MediaItem>,
        p: TasteProfile?,
        blocked: Set<String> = emptySet(),
    ): List<MediaItem> {
        if (p == null || items.size < 8) return items
        val known = ArrayList<MediaItem>(items.size)
        val fresh = ArrayList<MediaItem>()
        for (mi in items) {
            val artist = mi.metadata?.artists?.firstOrNull()?.name
            val isFresh = artist != null && !p.isKnownArtist(artist) &&
                (blocked.isEmpty() || mi.mediaId !in blocked)
            if (isFresh) fresh.add(mi) else known.add(mi)
        }
        // Nothing to interleave (all known or all fresh) → preserve the existing order exactly.
        if (fresh.isEmpty() || known.isEmpty()) return items
        val out = ArrayList<MediaItem>(items.size)
        val ki = known.iterator()
        val fi = fresh.iterator()
        var pos = 0
        while (ki.hasNext() || fi.hasNext()) {
            val takeFresh = pos % 15 == 14 && fi.hasNext()
            out.add(if (takeFresh) fi.next() else if (ki.hasNext()) ki.next() else fi.next())
            pos++
        }
        return out
    }
}
