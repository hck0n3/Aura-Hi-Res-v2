package iad1tya.echo.music.playback

import androidx.annotation.StringRes
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.player.PlaybackErrorAdvice
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Registry row 805 (owner decision H8): the service's notices that ask the user to DO something reach the app's
 * snackbar host with the button that does it, instead of a toast that names the step and vanishes.
 *
 * MainActivity collects [events] only while it is STARTED, so [post] answers false when no screen could show the
 * notice (app in the background, Android Auto): the caller then keeps the toast it always showed.
 */
object ActionNotices {
    enum class Kind(@StringRes val message: Int, @StringRes val action: Int, val route: String) {
        /** «Volver a entrar»: Ajustes ▸ Cuentas, the same destination as the playback error card's sign-in step. */
        SESSION_EXPIRED(R.string.session_expired_sign_in_again, R.string.notice_action_sign_in_again, requireNotNull(PlaybackErrorAdvice.Action.SIGN_IN.route)),

        /** «Ver filtros»: Ajustes ▸ Contenido, where «Ocultar vídeos» lives (the explicit filter's key is retired). */
        QUEUE_EMPTY_AFTER_FILTERS(R.string.queue_empty_after_filters, R.string.notice_action_view_filters, "settings/content"),

        /** «Ver proxy»: Ajustes ▸ Contenido, where the Proxy group asks for local network access (registry row 864). */
        LAN_PROXY_NEEDS_PERMISSION(R.string.proxy_local_network_notice, R.string.notice_action_view_proxy, "settings/content"),
    }

    private val _events = MutableSharedFlow<Kind>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Kind> = _events.asSharedFlow()

    /** True when a started screen took [kind]; false means nobody would see it and the caller must show its toast. */
    fun post(kind: Kind): Boolean = _events.subscriptionCount.value > 0 && _events.tryEmit(kind)

    /** Suspends until a started screen takes [kind] (a check made at process start, before any screen exists). */
    suspend fun postWhenShown(kind: Kind) {
        while (!post(kind)) _events.subscriptionCount.first { it > 0 }
    }
}
