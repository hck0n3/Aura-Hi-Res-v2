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


    /** Greedy artist-spacing: keep the incoming (taste/relatedness) order as the base, but when the next item
     *  repeats a primary artist placed in the last 2 slots, skip ahead to the best-ranked item by a different
     *  artist (fallback: take the head). Preserves the backbone, kills same-artist streaks. */
    fun spacedByArtist(items: List<MediaItem>): List<MediaItem> {
        if (items.size < 3) return items
        val remaining = ArrayList(items)
        val out = ArrayList<MediaItem>(items.size)
        val recent = ArrayDeque<String>()
        while (remaining.isNotEmpty()) {
            var idx = remaining.indexOfFirst { mi ->
                val a = mi.metadata?.artists?.firstOrNull()?.name?.lowercase()
                a == null || a !in recent
            }
            if (idx < 0) idx = 0
            val pick = remaining.removeAt(idx)
            out.add(pick)
            pick.metadata?.artists?.firstOrNull()?.name?.lowercase()?.let {
                recent.addLast(it); if (recent.size > 2) recent.removeFirst()
            }
        }
        return out
    }

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
