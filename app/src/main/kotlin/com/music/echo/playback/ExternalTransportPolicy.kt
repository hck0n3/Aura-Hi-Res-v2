package iad1tya.echo.music.playback

import androidx.media3.common.Player

/**
 * Registry row 786: next / previous / seek that come from OUTSIDE the app's own screens (home-screen widget, notification,
 * headset and Bluetooth buttons, Android Auto, watches). The in-app buttons already send these to the controlled device in remote
 * mode (row 603) and are disabled for a Listen Together guest; these doors moved the local player directly, so in remote mode the
 * mirror jumped and snapped back, and a guest could skip the room's song on its own device. Pure.
 */
object ExternalTransportPolicy {
    enum class Kind { NEXT, PREVIOUS, SEEK }

    enum class Route {
        /** Nothing special is going on: the local player does it, as always. */
        LOCAL,

        /** A Listen Together guest: the room's host drives the queue and the position. */
        REFUSE_GUEST,

        /** Another device is controlled from here: the order goes to it ([iad1tya.echo.music.devicesync.RemotePlayback]). */
        REMOTE,

        /** Another device is controlled from here and the order cannot be forwarded (a seek carries no target here). */
        REFUSE_REMOTE,
    }

    /** The media3 player commands that move through the queue or inside the song; null for every other command. */
    fun kindOf(playerCommand: Int): Kind? = when (playerCommand) {
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> Kind.NEXT
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> Kind.PREVIOUS
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
        Player.COMMAND_SEEK_BACK,
        Player.COMMAND_SEEK_FORWARD,
        -> Kind.SEEK
        else -> null
    }

    fun route(kind: Kind, listenTogetherGuest: Boolean, remoteActive: Boolean): Route = when {
        listenTogetherGuest -> Route.REFUSE_GUEST
        !remoteActive -> Route.LOCAL
        kind == Kind.SEEK -> Route.REFUSE_REMOTE
        else -> Route.REMOTE
    }
}
