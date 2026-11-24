package iad1tya.echo.music.utils

/**
 * Registry row 833 (owner 2026-11-24: «no que sincronice otras cosas que no tengan nada que ver con lo que estoy pidiendo»).
 * LibraryUploadSync.reconcileSpotifyMirrors only keeps in step (adds AND removes songs) an account playlist that is Aura's own copy
 * of a Spotify source. Pure, for JVM tests.
 */
object SpotifyMirrorOwnership {
    /**
     * Whether the account playlist [browseId] linked to a Spotify mirror is Aura's own copy of it.
     *
     * - [createdMarker] is the browseId uploadPlaylists recorded when it CREATED the playlist (the normal case from this version on).
     * - A mirror from before that marker is Aura's copy when an earlier version already reconciled it completely ([storedFingerprint]
     *   starts with this browseId: a complete pass left the account copy with exactly the mirror's songs), or when the account copy
     *   holds no song outside the mirror ([remoteIds] ⊆ [localIds]).
     * - Anything else may be the user's own playlist that an older version linked by name: false, and it is left untouched.
     */
    fun isAurasCopy(
        browseId: String,
        createdMarker: String?,
        storedFingerprint: String?,
        remoteIds: Collection<String>,
        localIds: Set<String>,
    ): Boolean =
        createdMarker == browseId ||
            storedFingerprint?.startsWith("$browseId|") == true ||
            remoteIds.all { it in localIds }
}
