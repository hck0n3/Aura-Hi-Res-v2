package iad1tya.echo.music.playback

/**
 * Registry row 795 (owner 2026-11-24, beta3: «cuando pase a modo avión pasa a la música offline pero quiero que el reproductor
 * esté con un aviso que está continuando en el modo sin conexión»). What the full player's and the mini player's «Sin conexión»
 * sign shows, from the two states that make the player go on with offline music only:
 *  · [AUTOMATIC] — the player's own offline session ([PlaybackOfflineSession], in memory, never writes `OfflineModeKey`, row 284);
 *  · [MANUAL] — the user's own switch (`OfflineModeKey`); only here does «Volver online» do something, so only here is it offered.
 * The manual switch wins: with it on, ending the session changes nothing the user hears.
 */
enum class OfflinePlaybackNotice {
    NONE,
    AUTOMATIC,
    MANUAL,
    ;

    val visible: Boolean get() = this != NONE

    /** The sign offers «Volver online» (turns the manual switch off). Never for the automatic session: it ends by itself. */
    val offersGoOnline: Boolean get() = this == MANUAL

    companion object {
        fun of(sessionActive: Boolean, manual: Boolean): OfflinePlaybackNotice = when {
            manual -> MANUAL
            sessionActive -> AUTOMATIC
            else -> NONE
        }
    }
}
