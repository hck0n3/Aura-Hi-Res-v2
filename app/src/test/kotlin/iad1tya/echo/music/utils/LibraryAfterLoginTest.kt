package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La biblioteca, nada más iniciar sesión.
 *
 * 🔴 ORDEN DEL DUEÑO (2026-09-16): *"cuando uno inicia sesión con SimpMusic la biblioteca ya está
 * allí de un solo, sin estar esperando que cargue… tanto las suscripciones, me gustas y listas"*.
 *
 * Son dos arreglos distintos y este archivo vigila los dos, porque los dos fallan **en silencio**:
 *
 * 1. **El orden de la cola.** `SyncUtils.syncChannel` es serial a propósito (es la defensa contra
 *    el 429 de HALLAZGO-056), así que lo que `syncLibraryAfterLogin` encola último no se ve hasta
 *    que termina todo lo anterior. Con el orden viejo, las suscripciones y las listas — las dos que
 *    él nombró — iban detrás de las dos pasadas sin cota. Nada de eso produce un error: simplemente
 *    tardan, y el único sitio donde el orden está escrito es esa función.
 *
 * 2. **`isSectionPending`.** Decide si una pestaña vacía dice "sincronizando" o afirma que no
 *    tienes nada. Invertirlo tampoco da error: solo hace que la app mienta.
 */
class LibraryAfterLoginTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private val syncUtilsSource: String by lazy {
        val f = File(repoRoot, "app/src/main/kotlin/com/music/echo/utils/SyncUtils.kt")
        assertTrue("SyncUtils.kt se movió — actualiza este test para seguirlo", f.isFile)
        f.readText()
    }

    // ---------------------------------------------------------------- el orden de la cola

    @Test
    fun `las listas y las suscripciones se encolan ANTES que las dos pasadas sin cota`() {
        val body = Regex("""fun syncLibraryAfterLogin\(\) \{(.*?)\n {4}\}""", RegexOption.DOT_MATCHES_ALL)
            .find(syncUtilsSource)
            ?.groupValues
            ?.get(1)
            ?: error("no se encontró syncLibraryAfterLogin en SyncUtils.kt")

        val order = Regex("""sync(SavedPlaylists|ArtistsSubscriptions|LikedSongs|LibrarySongs)\(\)""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(
            "syncLibraryAfterLogin tiene que encolar exactamente estas cuatro pasadas — si se añade " +
                "o se quita una, este test es el sitio donde decidir su puesto en la cola",
            4,
            order.size,
        )

        val playlists = order.indexOf("SavedPlaylists")
        val artists = order.indexOf("ArtistsSubscriptions")
        val liked = order.indexOf("LikedSongs")
        val library = order.indexOf("LibrarySongs")

        // El fondo del asunto: la cola es SERIAL, así que el puesto ES el tiempo de espera.
        // `LikedSongs` transmite sus páginas pero al final reconcilia la lista local entera y puede
        // disparar un likeVideo por canción; `LibrarySongs` va detrás. Poner delante las dos
        // secciones acotadas es lo único que hace que se vean enseguida.
        assertTrue(
            "las listas guardadas tienen que ir antes que 'me gusta': detrás de esa pasada la " +
                "pestaña de listas se queda vacía justo el rato que el dueño reportó",
            playlists < liked,
        )
        assertTrue(
            "las suscripciones tienen que ir antes que 'me gusta', por lo mismo",
            artists < liked,
        )
        assertTrue(
            "las listas y las suscripciones tienen que ir antes que las canciones de la biblioteca",
            playlists < library && artists < library,
        )
    }

    @Test
    fun `la cola de sincronizacion sigue siendo serial`() {
        // Si alguien la hace concurrente, el orden de arriba deja de significar nada y este test
        // pasaría dando una falsa sensación de seguridad. Además la serialidad ES la defensa contra
        // el 429 de HALLAZGO-056, así que romperla es un problema por sí mismo.
        assertTrue(
            "el procesador de la cola ya no consume el canal en un bucle serial — revisa si el " +
                "orden de syncLibraryAfterLogin sigue teniendo sentido, y el 429 de HALLAZGO-056",
            syncUtilsSource.contains("for (operation in syncChannel)"),
        )
    }

    // ---------------------------------------------------------------- "vacío" vs "aún no llegó"

    private val idle = SyncState()

    @Test
    fun `una seccion que ya termino muestra su texto real de vacio`() {
        // Aquí el vacío ya es verdad. Seguir mostrando "sincronizando" sería una rueda eterna.
        val state = idle.copy(playlists = SyncStatus.Completed)
        assertFalse(isSectionPending(state.playlists, state))
    }

    @Test
    fun `una seccion que esta corriendo cuenta como pendiente`() {
        val state = idle.copy(playlists = SyncStatus.Syncing)
        assertTrue(isSectionPending(state.playlists, state))
    }

    @Test
    fun `una seccion ENCOLADA detras de otra tambien cuenta como pendiente`() {
        // ESTE es el caso que un `== Syncing` a secas falla, y el que más importa: mientras corre
        // "me gusta", la pasada de listas todavía no ha arrancado, así que su estado es Idle — no
        // Syncing. Sin esta regla, la pestaña de listas diría "no tienes listas" durante justo el
        // rato en que esa frase es más falsa.
        val state = idle.copy(likedSongs = SyncStatus.Syncing, playlists = SyncStatus.Idle)
        assertTrue(
            "una sección en cola detrás de otra pasada tiene que leerse como pendiente",
            isSectionPending(state.playlists, state),
        )
    }

    @Test
    fun `sin ninguna sincronizacion en marcha nada esta pendiente`() {
        // Sesión ya asentada, o usuario sin sesión: el vacío es la verdad y hay que decirlo.
        assertFalse(isSectionPending(idle.playlists, idle))
        assertFalse(isSectionPending(idle.artists, idle))
        assertFalse(isSectionPending(idle.likedSongs, idle))
    }

    @Test
    fun `una seccion que fallo no se queda girando para siempre`() {
        // Error devuelve false a propósito: el usuario tiene que poder enterarse, y una rueda
        // infinita es la peor forma de contárselo.
        val state = idle.copy(playlists = SyncStatus.Error("boom"))
        assertFalse(isSectionPending(state.playlists, state))
        // Y sigue siendo false aunque otra pasada continúe: el fallo es de ESTA sección.
        val alsoRunning = state.copy(likedSongs = SyncStatus.Syncing)
        assertFalse(isSectionPending(alsoRunning.playlists, alsoRunning))
    }

    @Test
    fun `una seccion completada no vuelve a pendiente porque otra siga corriendo`() {
        // El caso inverso del de la cola: si 'me gusta' termina y las listas siguen, la pestaña de
        // 'me gusta' ya no debe decir "sincronizando" — su dato ya está.
        val state = idle.copy(likedSongs = SyncStatus.Completed, playlists = SyncStatus.Syncing)
        assertFalse(isSectionPending(state.likedSongs, state))
        assertTrue(isSectionPending(state.playlists, state))
    }
}
