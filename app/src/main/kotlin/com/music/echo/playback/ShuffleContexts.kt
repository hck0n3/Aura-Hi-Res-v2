package iad1tya.echo.music.playback

/**
 * Persistent Enhanced Shuffle buckets ("Aleatorio mejorado").
 *
 * Prefixes are load-bearing: [iad1tya.echo.music.db.DatabaseDao.pruneOrphanEnhancedPlayed] deletes
 * every `PL:%` row whose suffix is not a live `playlist.id`. Albums, YouTube playlists and
 * auto-playlists must NEVER use `PL:` or a restart wipes their memory (registry #88).
 */
object ShuffleContexts {
    /**
     * Inicio's «Aleatorio» tile («Mezcla de tu gusto»). ONE stable bucket for every tap, so what one mix played is
     * remembered by the next ([iad1tya.echo.music.reco.TasteMixPlanner]). `AP:` like the other virtual lists: the
     * orphan prune only touches `PL:`.
     */
    const val TASTE_MIX = "AP:taste-mix"

    fun playlist(localId: String): String = "PL:$localId"

    fun onlinePlaylist(browseId: String): String = "OL:${browseId.removePrefix("VL")}"

    fun album(albumId: String): String = "AL:$albumId"

    /**
     * Owned / created lists keep `PL:<local id>` so existing memory is not orphaned.
     * Followed YouTube lists share `OL:<browseId>` whether opened from Biblioteca (local row) or
     * from Inicio/búsqueda (online screen) — otherwise shuffle memory only existed on lists the
     * user created.
     */
    fun forPlaylist(isEditable: Boolean, localId: String, browseId: String?): String {
        val browse = browseId?.takeIf { it.isNotBlank() }
        return if (!isEditable && browse != null) onlinePlaylist(browse) else playlist(localId)
    }

    /**
     * In-memory seed for a Continue shuffle: songs already in this context's memory PLUS songs
     * the user has actually heard ([totalPlayTime] > 0), so the checkmarks and the no-repeat
     * order agree even if Aleatorio mejorado was off when those songs played.
     * Start-over passes [resetMemory] = true and gets an empty set — history is not re-imported.
     */
    fun seedPlayedIds(
        resetMemory: Boolean,
        songIds: List<String>,
        shufflePlayed: Set<String>,
        playTimeMs: (String) -> Long,
    ): Set<String> {
        if (resetMemory) return emptySet()
        // Row 591 (owner 2026-10-31: «le doy iniciar desde cero y el contador no se resetea»): the lap is what the no-repeat MEMORY says. Lifetime
        // listening ([playTimeMs]) used to be added here and to every counter, and it never goes away, so after «Iniciar desde cero» the next
        // Continue brought all those songs back as «played» (26 of 29 again). The per-row «heard before» tick keeps using lifetime plays.
        return songIds.filterTo(HashSet()) { id -> id in shufflePlayed }
    }
}
