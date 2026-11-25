package iad1tya.echo.music.playback

/**
 * Registry row 795 (owner 2026-11-24, beta3: «cuando pase a modo avión pasa a la música offline pero quiero que el reproductor
 * esté con un aviso que está continuando en el modo sin conexión»). What the full player's and the mini player's «Sin conexión»
 * sign shows, from the states that make the player go on with offline music only (the same ones as `MusicService.offlineQueueModeNow`):
 *  · [AUTOMATIC] — the player's own offline session ([PlaybackOfflineSession], in memory, never writes `OfflineModeKey`, row 284),
 *    or the offline queue it left parked when it timed out with no proof the network works (row 717): still offline music only;
 *  · [MANUAL] — the user's own switch (`OfflineModeKey`); only here does «Volver online» do something, so only here is it offered.
 * The manual switch wins: with it on, ending the session changes nothing the user hears.
 */
enum class OfflinePlaybackNotice {
    NONE,
    AUTOMATIC,
    MANUAL,

    /**
     * Row 854: automatic, but the song that was playing when the connection went away is still the current one — it goes on
     * from what was already loaded, and the offline music comes after it.
     */
    BUFFERED,
    ;

    /** Row 854: what the full player's chip says. [FROM_LOADED] «esta canción sigue desde lo cargado; después, tu música sin conexión». */
    enum class Wording { FROM_LOADED, OFFLINE_MUSIC }

    val visible: Boolean get() = this != NONE

    /** The sign offers «Volver online» (turns the manual switch off). Never for the automatic session: it ends by itself. */
    val offersGoOnline: Boolean get() = this == MANUAL

    /** Row 854: the chip's text by state; null while nothing is shown. */
    val wording: Wording? get() = when (this) {
        NONE -> null
        BUFFERED -> Wording.FROM_LOADED
        AUTOMATIC, MANUAL -> Wording.OFFLINE_MUSIC
    }

    companion object {
        fun of(sessionActive: Boolean, queueParked: Boolean, manual: Boolean): OfflinePlaybackNotice = when {
            manual -> MANUAL
            sessionActive || queueParked -> AUTOMATIC
            else -> NONE
        }

        /**
         * Row 853 (owner test #17, beta8: airplane mode on, 25 s, no sign): the device itself has no network — no transport
         * at all ([iad1tya.echo.music.utils.NetworkState.online], the same reading as Inicio's automatic offline mode) and the
         * default network is not validated — with "que se ponga solo" on. Only then does the player go on with offline music
         * by itself, so only then may the sign say so. A VPN, IPv6-only or signed-in captive portal keeps a transport and is
         * never taken for "no network" here (they carry traffic).
         */
        fun deviceOffline(online: Boolean, validated: Boolean, autoOffline: Boolean): Boolean = autoOffline && !online && !validated

        /**
         * Row 853: the sign does not wait for the player's offline session (its entry hangs on one live read at the moment
         * the network drops, and a network song reaching READY ends it): with the device offline it is on at once.
         * Row 854: until another song starts ([songMovedOn] false), the automatic sign says the current song goes on from
         * what was loaded ([BUFFERED]); the manual switch keeps its own sign.
         */
        fun shown(base: OfflinePlaybackNotice, deviceOffline: Boolean, songMovedOn: Boolean): OfflinePlaybackNotice {
            val on = if (base == NONE && deviceOffline) AUTOMATIC else base
            return if (on == AUTOMATIC && !songMovedOn) BUFFERED else on
        }
    }
}
