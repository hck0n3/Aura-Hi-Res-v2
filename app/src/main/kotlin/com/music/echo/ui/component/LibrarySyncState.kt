package iad1tya.echo.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import iad1tya.echo.music.LocalSyncUtils
import iad1tya.echo.music.R
import iad1tya.echo.music.utils.SyncState
import iad1tya.echo.music.utils.SyncStatus
import iad1tya.echo.music.utils.isSectionPending

/**
 * # "Vacío" y "todavía no ha llegado" no son lo mismo
 *
 * 🔴 ORDEN DEL DUEÑO (2026-09-16): *"cuando uno inicia sesión con SimpMusic la biblioteca ya está
 * allí de un solo, sin estar esperando que cargue, tanto las suscripciones, me gustas y listas"*.
 *
 * La mitad del problema era el ORDEN de las pasadas, y se arregló en
 * `SyncUtils.syncLibraryAfterLogin`. Esta es la otra mitad: hasta ahora **ninguna** pantalla de
 * biblioteca miraba el estado de sincronización — solo Home lo hacía, para su banda de
 * "Sincronizando tu biblioteca…" — así que en cuanto Room devolvía la lista vacía, las pestañas
 * afirmaban *"no tienes artistas"* / *"no tienes álbumes"*.
 *
 * Eso no es un estado de carga, es una afirmación, y durante toda la sincronización es **falsa**.
 * Y es la peor clase de falsedad para este caso concreto: el usuario acaba de iniciar sesión
 * justamente para ver su biblioteca, y lo primero que le dice la app es que no tiene nada.
 *
 * ## Por qué esto vive en un solo sitio
 * Son seis pantallas entre las dos apariencias. Un `if` copiado seis veces se despega al primer
 * ajuste, y aquí despegarse significa que una pestaña miente y las otras cinco no — el tipo de
 * incoherencia que parece un fallo aleatorio.
 *
 * Reutiliza `R.string.home_syncing_library`, el MISMO texto que ya usa la banda de Home: es
 * literalmente el mismo hecho contado en dos sitios, y duplicarlo en un recurso nuevo solo crearía
 * dos redacciones que se separan.
 *
 * @param section de qué campo de [SyncState] depende esta pestaña (`SyncState::artists`,
 *   `SyncState::playlists`, `SyncState::likedSongs`…). Se pasa como función y no como valor para
 *   que la lectura ocurra DENTRO, con el estado ya recolectado.
 */
@Composable
fun rememberLibrarySyncPending(section: (SyncState) -> SyncStatus): Boolean {
    val syncUtils = LocalSyncUtils.current
    val state by syncUtils.syncState.collectAsStateWithLifecycle()
    return isSectionPending(section(state), state)
}

/**
 * El texto del hueco vacío de una pestaña de biblioteca.
 *
 * Devuelve "Sincronizando tu biblioteca…" mientras la pasada de esa sección siga pendiente (ojo:
 * *pendiente* incluye **encolada**, porque la cola de sincronización es serial — ver
 * [isSectionPending]), y el texto real en cuanto deja de serlo.
 *
 * @param emptyText lo que se dice cuando el vacío ya es verdad.
 * @param searching true si el usuario está filtrando: entonces el vacío es del filtro, no de la
 *   biblioteca, y avisar de la sincronización sería responder a una pregunta que no hizo.
 */
@Composable
fun librarySectionEmptyText(
    section: (SyncState) -> SyncStatus,
    emptyText: String,
    searching: Boolean = false,
): String {
    val pending = rememberLibrarySyncPending(section)
    return if (!searching && pending) stringResource(R.string.home_syncing_library) else emptyText
}
