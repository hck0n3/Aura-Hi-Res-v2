package iad1tya.echo.music.ui.menu

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import iad1tya.echo.music.R
import iad1tya.echo.music.devicesync.RemotePlayback
import iad1tya.echo.music.playback.PlayerConnection

/**
 * Registry row 733 (UX audit P22B): the song menus' «Reproducir a continuación», «Añadir a la cola» and «Añadir a lista»
 * ran with no confirmation at all. They now give the SAME short confirmation the row swipe already gives
 * (`ui/component/Items.kt`, `ui/newui/AuraContent.kt`): a short Toast carrying the action's own label.
 */
internal object MenuActionConfirmation {
    /**
     * Whether a queue edit just asked for actually happens on THIS device, so a «done» toast tells the truth.
     * A Listen Together guest is refused by [PlayerConnection] with its own toast, and while another device is controlled
     * the service refuses the edit with its own notice (rows 682/686) — a «done» next to «not sent» would contradict it.
     */
    fun queueEditConfirms(listenTogetherGuest: Boolean, controllingAnotherDevice: Boolean): Boolean =
        !listenTogetherGuest && !controllingAnotherDevice
}

/** Call right after `playNext` / `addToQueue` from a menu, with that menu item's own label. */
internal fun confirmQueueEdit(context: Context, playerConnection: PlayerConnection, @StringRes label: Int) {
    // Read without PlayerConnection.shouldBlockPlaybackChanges: that one shows the guest toast as a side effect.
    val guest = runCatching { playerConnection.service.listenTogetherManager.shouldBlockPlaybackChanges() }.getOrDefault(false)
    if (!MenuActionConfirmation.queueEditConfirms(guest, RemotePlayback.active)) return
    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
}

/** «Añadido a «%s»» once the songs were written to the list. Safe from any thread (the add runs on IO). */
internal fun confirmAddedToPlaylist(context: Context, playlistName: String) {
    val app = context.applicationContext
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(app, app.getString(R.string.added_to_playlist_toast, playlistName), Toast.LENGTH_SHORT).show()
    }
}
