package iad1tya.echo.music.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import iad1tya.echo.music.constants.AutoOfflineModeKey
import iad1tya.echo.music.constants.OfflineModeKey

/**
 * Lo que una pantalla ve: [manual] es el interruptor del dueño y [automatic] es "está así porque no
 * hay red". [offline] es lo único que hace falta para decidir qué dibujar; [automatic] solo gobierna
 * el cartel (con el manual hay un "Desactivar" que sirve, con el automático no hay nada que
 * desactivar). Ver [AutoOfflinePolicy].
 */
data class OfflineState(
    val offline: Boolean,
    val manual: Boolean,
    val automatic: Boolean,
)

/**
 * El estado sin conexión efectivo: el interruptor manual **más** el automático cuando no hay red.
 *
 * Se lee así, y no como `rememberPreference(OfflineModeKey)` suelto, en las cuatro pantallas de red
 * (Inicio, Novedades, Biblioteca, Buscar). Quien necesite además ESCRIBIR el manual — Inicio tiene un
 * botón "continuar sin conexión" — sigue teniendo su `rememberPreference` para eso: esto no guarda
 * nada.
 */
@Composable
fun rememberOfflineState(): OfflineState {
    val manual by rememberPreference(OfflineModeKey, false)
    val auto by rememberPreference(AutoOfflineModeKey, true)
    val online by NetworkState.online.collectAsState()
    return OfflineState(
        offline = AutoOfflinePolicy.offline(manual = manual, auto = auto, online = online),
        manual = manual,
        automatic = AutoOfflinePolicy.automatic(manual = manual, auto = auto, online = online),
    )
}
