package iad1tya.echo.music.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import iad1tya.echo.music.MainActivity

/**
 * The tap target of a notification, in one place.
 *
 * 🔴 OWNER REPORT (2026-09-15): *"cuando toco las notificaciones de Aura no hacen nada, quiero que me
 * redirija a lo que está notificando"*. Two different shapes of the same bug were behind it:
 *
 *  1. Notifications with **no `setContentIntent` at all** — Spotify import (en curso / terminado /
 *     fallido), exportación de audio, descarga de la actualización en curso y su fallo. Android lets a
 *     notification be built without one; tapping it then does literally nothing, not even open the app.
 *  2. Notifications with a **bare launcher intent** — Radar de novedades said so in its own comment
 *     ("plain launch intent for now; a deep link … lands in the next sub-task"). That reopens the app
 *     wherever it was, which from the user's side is indistinguishable from nothing happening.
 *
 * So every notification the app posts now carries a tap target, and the target is the screen the
 * notification is ABOUT. The route travels as an extra on [MainActivity.ACTION_OPEN_ROUTE] and is
 * resolved by `handleDeepLinkIntent`, which already knows how to navigate without wiping the user's
 * back stack.
 *
 * ROUTES ARE ALLOW-LISTED ([ALLOWED_ROUTES]). MainActivity is exported — it is the launcher activity —
 * so any app on the device can send it this action; an allow-list means the worst a forged intent can
 * do is open one of the same four screens a notification could have opened anyway.
 */
object NotificationTapIntents {
    /** Ajustes ▸ Actualizaciones. */
    const val ROUTE_UPDATE = "settings/update"

    /** Radar de novedades (lanzamientos nuevos de los artistas que sigues). */
    const val ROUTE_RELEASE_RADAR = "release_radar"

    /** La pantalla de importación de Spotify, con sus argumentos opcionales por defecto. */
    const val ROUTE_SPOTIFY_IMPORT = "settings/spotify_import"

    /** Biblioteca — donde aparece lo que se acaba de exportar. */
    const val ROUTE_LIBRARY = "library"

    /**
     * Every route a notification may ask for. Adding a notification means adding its route HERE too,
     * or the tap is ignored (and says so in the log) rather than navigating somewhere unvetted.
     */
    val ALLOWED_ROUTES =
        setOf(ROUTE_UPDATE, ROUTE_RELEASE_RADAR, ROUTE_SPOTIFY_IMPORT, ROUTE_LIBRARY)

    /**
     * A PendingIntent that opens [route] inside the app.
     *
     * [requestCode] must differ per notification id: two PendingIntents that compare equal share one
     * slot, and with `FLAG_UPDATE_CURRENT` the second would quietly rewrite the first one's extras —
     * which is how two notifications end up opening the same screen.
     */
    fun open(
        context: Context,
        route: String,
        requestCode: Int,
    ): PendingIntent {
        // Written flat, with the target named TWICE on purpose — this is the one PendingIntent the
        // app hands to the system, and a PendingIntent built on an intent that does not name its
        // target is the classic Android hole: whoever holds it can fill in the blanks and have it
        // run with this app's identity (CWE-927).
        //
        // `Intent(context, MainActivity::class.java)` already names the component, and
        // FLAG_IMMUTABLE already forbids the holder from changing anything. The extra `setPackage`
        // is belt and braces for a human reader — and it is also what CodeQL's explicit-intent
        // barrier recognises: that barrier follows LOCAL flow only, and it does not traverse
        // Kotlin's `apply { }`, so the earlier version of this function (a builder chained inside
        // `apply`) read to the analyzer as an intent of unknown target and raised 8 high-severity
        // alerts on PR #25. The behaviour was never unsafe; it was unprovable. Keep it flat.
        val intent = Intent(context, MainActivity::class.java)
        intent.setPackage(context.packageName)
        intent.action = MainActivity.ACTION_OPEN_ROUTE
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra(MainActivity.EXTRA_OPEN_ROUTE, route)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
